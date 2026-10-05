package net.corda.samples.obligation.flow

import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.node.services.Vault
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.samples.obligation.contract.DisputeContract
import net.corda.samples.obligation.flows.RespondDisputeFlow
import net.corda.samples.obligation.states.DisputeState
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RespondDisputeFlowTests {

    private lateinit var network: MockNetwork

    private lateinit var borrowerNode: StartedMockNode
    private lateinit var lenderNode: StartedMockNode

    private val borrower = TestIdentity(
        CordaX500Name(
            "Borrower",
            "Paris",
            "FR"
        )
    )

    private val lender = TestIdentity(
        CordaX500Name(
            "Lender",
            "Paris",
            "FR"
        )
    )

    @Before
    fun setup() {

        network = MockNetwork(
            MockNetworkParameters(
                listOf(
                    TestCordapp.findCordapp(
                        "net.corda.samples.obligation.contract"
                    ),
                    TestCordapp.findCordapp(
                        "net.corda.samples.obligation.flows"
                    )
                )
            )
        )

        borrowerNode =
            network.createNode(borrower.name)

        lenderNode =
            network.createNode(lender.name)

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    // -------------------------------------------------------------
    // Helper: create OPEN dispute
    // -------------------------------------------------------------

    private fun createOpenDispute(): UniqueIdentifier {

        val obligationId =
            UniqueIdentifier()

        val dispute =
            DisputeState(
                initiator = borrower.party,
                respondent = lender.party,
                obligationId = obligationId,
                reason = "Payment not recognised",
                status = DisputeState.Status.OPEN
            )

        val command =
            Command(
                DisputeContract.Commands.Open(),
                listOf(
                    borrower.publicKey,
                    lender.publicKey
                )
            )

        val notary =
            borrowerNode.notaryIdentities.first()

        val txBuilder =
            TransactionBuilder(notary)
                .addOutputState(
                    dispute,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )
                .addCommand(command)

        txBuilder.verify(
            borrowerNode.services
        )

        val signedTx =
            borrowerNode.services
                .signInitialTransaction(txBuilder)

        // Dans un environnement de test, on signe avec les deux
        // parties avant d'enregistrer l'état dans les vaults.
        val lenderKey =
            lenderNode.services.keyManagementService
                .freshKeyAndCert(
                    lender.party,
                    false
                )
                .owningKey

        // Le helper ci-dessus dépend de la configuration exacte
        // des identités de ton MockNetwork.
        //
        // Si tes nodes utilisent les identités TestIdentity directement,
        // il est préférable d'utiliser le flow OpenDisputeFlow dans
        // les tests d'intégration.

        borrowerNode.services.recordTransactions(
            signedTx
        )

        network.runNetwork()

        return dispute.linearId
    }

    // -------------------------------------------------------------
    // Respond
    // -------------------------------------------------------------

    @Test
    fun `respondent can respond to an open dispute`() {

        val obligationId =
            UniqueIdentifier()

        // Création du litige avec OpenDisputeFlow.
        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = obligationId,
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val openTx =
            openFuture.get()

        val disputeId =
            openTx.tx.outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        // Le lender répond.
        val respondFuture =
            lenderNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = "Payment was correctly made."
                )
            )

        network.runNetwork()

        val stx =
            respondFuture.get()

        assertNotNull(stx)

        val dispute =
            lenderNode.services.vaultService
                .queryBy<DisputeState>()
                .states
                .single()
                .state
                .data

        assertEquals(
            disputeId,
            dispute.linearId
        )

        assertEquals(
            DisputeState.Status.RESPONDED,
            dispute.status
        )

        assertEquals(
            "Payment was correctly made.",
            dispute.response
        )
    }

    // -------------------------------------------------------------
    // Both vaults
    // -------------------------------------------------------------

    @Test
    fun `responded dispute is visible in both vaults`() {

        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val openTx =
            openFuture.get()

        val disputeId =
            openTx.tx.outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        val respondFuture =
            lenderNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = "I disagree with the claim."
                )
            )

        network.runNetwork()

        respondFuture.get()

        val borrowerDispute =
            borrowerNode.services.vaultService
                .queryBy<DisputeState>()
                .states
                .single()
                .state
                .data

        val lenderDispute =
            lenderNode.services.vaultService
                .queryBy<DisputeState>()
                .states
                .single()
                .state
                .data

        assertEquals(
            DisputeState.Status.RESPONDED,
            borrowerDispute.status
        )

        assertEquals(
            DisputeState.Status.RESPONDED,
            lenderDispute.status
        )

        assertEquals(
            "I disagree with the claim.",
            borrowerDispute.response
        )
    }

    // -------------------------------------------------------------
    // Command
    // -------------------------------------------------------------

    @Test
    fun `response transaction contains Respond command`() {

        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val disputeId =
            openFuture.get()
                .tx
                .outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        val respondFuture =
            lenderNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = "Payment was made."
                )
            )

        network.runNetwork()

        val stx =
            respondFuture.get()

        val commands =
            stx.tx.commands.filter {
                it.value is DisputeContract.Commands.Respond
            }

        assertEquals(
            1,
            commands.size
        )
    }

    // -------------------------------------------------------------
    // Signatures
    // -------------------------------------------------------------

    @Test
    fun `both parties sign the response`() {

        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val disputeId =
            openFuture.get()
                .tx
                .outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        val respondFuture =
            lenderNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = "Payment was made."
                )
            )

        network.runNetwork()

        val stx =
            respondFuture.get()

        assertTrue(
            stx.sigs.any {
                it.by == borrower.publicKey
            }
        )

        assertTrue(
            stx.sigs.any {
                it.by == lender.publicKey
            }
        )
    }

    // -------------------------------------------------------------
    // Invalid response
    // -------------------------------------------------------------

    @Test
    fun `empty response is rejected`() {

        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val disputeId =
            openFuture.get()
                .tx
                .outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        val future =
            lenderNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = ""
                )
            )

        network.runNetwork()

        assertFailsWith<Exception> {
            future.get()
        }
    }

    // -------------------------------------------------------------
    // Only respondent can respond
    // -------------------------------------------------------------

    @Test
    fun `initiator cannot respond to its own dispute`() {

        val openFuture =
            borrowerNode.startFlow(
                net.corda.samples.obligation.flows.OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val disputeId =
            openFuture.get()
                .tx
                .outputs
                .map { it.data }
                .filterIsInstance<DisputeState>()
                .single()
                .linearId

        val future =
            borrowerNode.startFlow(
                RespondDisputeFlow(
                    disputeId = disputeId,
                    response = "I respond to my own dispute."
                )
            )

        network.runNetwork()

        assertFailsWith<Exception> {
            future.get()
        }
    }
}
