package net.corda.samples.obligation.flow

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.samples.obligation.contract.GuaranteeContract
import net.corda.samples.obligation.flows.CreateGuaranteeFlow
import net.corda.samples.obligation.flows.ReleaseGuaranteeFlow
import net.corda.samples.obligation.states.GuaranteeState
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseGuaranteeFlowTests {

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
            network.createNode(
                borrower.name
            )

        lenderNode =
            network.createNode(
                lender.name
            )

        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    // -------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------

    private fun createGuarantee(): UniqueIdentifier {

        val flow =
            CreateGuaranteeFlow(
                beneficiary = lender.party,
                obligationId = UniqueIdentifier(),
                // Adapter selon ton GuaranteeState
                // et ton CreateGuaranteeFlow.
            )

        val future =
            borrowerNode.startFlow(flow)

        network.runNetwork()

        val stx =
            future.get()

        return stx.tx.outputs
            .map { it.data }
            .filterIsInstance<GuaranteeState>()
            .single()
            .linearId
    }

    private fun getGuarantees(
        node: StartedMockNode
    ): List<Vault.StateAndRef<GuaranteeState>> {

        return node.services.vaultService
            .queryBy<GuaranteeState>()
            .states
    }

    // -------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------

    @Test
    fun `release active guarantee`() {

        val guaranteeId =
            createGuarantee()

        val before =
            getGuarantees(borrowerNode)

        assertEquals(
            1,
            before.size
        )

        assertEquals(
            GuaranteeState.Status.ACTIVE,
            before.single().state.data.status
        )

        val flow =
            ReleaseGuaranteeFlow(
                guaranteeId
            )

        val future =
            borrowerNode.startFlow(flow)

        network.runNetwork()

        val stx =
            future.get()

        assertNotNull(stx)

        val guarantees =
            getGuarantees(borrowerNode)

        assertEquals(
            1,
            guarantees.size
        )

        val guarantee =
            guarantees.single().state.data

        assertEquals(
            guaranteeId,
            guarantee.linearId
        )

        assertEquals(
            GuaranteeState.Status.RELEASED,
            guarantee.status
        )

        assertEquals(
            lender.party,
            guarantee.beneficiary
        )

        assertEquals(
            borrower.party,
            guarantee.owner
        )
    }

    // -------------------------------------------------------------
    // Both parties receive the final state
    // -------------------------------------------------------------

    @Test
    fun `release is recorded in both vaults`() {

        val guaranteeId =
            createGuarantee()

        val future =
            borrowerNode.startFlow(
                ReleaseGuaranteeFlow(
                    guaranteeId
                )
            )

        network.runNetwork()

        future.get()

        val borrowerGuarantees =
            getGuarantees(borrowerNode)

        val lenderGuarantees =
            getGuarantees(lenderNode)

        assertEquals(
            1,
            borrowerGuarantees.size
        )

        assertEquals(
            1,
            lenderGuarantees.size
        )

        assertEquals(
            GuaranteeState.Status.RELEASED,
            borrowerGuarantees
                .single()
                .state
                .data
                .status
        )

        assertEquals(
            GuaranteeState.Status.RELEASED,
            lenderGuarantees
                .single()
                .state
                .data
                .status
        )
    }

    // -------------------------------------------------------------
    // LinearId must remain unchanged
    // -------------------------------------------------------------

    @Test
    fun `release keeps the same linearId`() {

        val guaranteeId =
            createGuarantee()

        val future =
            borrowerNode.startFlow(
                ReleaseGuaranteeFlow(
                    guaranteeId
                )
            )

        network.runNetwork()

        future.get()

        val guarantee =
            getGuarantees(borrowerNode)
                .single()
                .state
                .data

        assertEquals(
            guaranteeId,
            guarantee.linearId
        )
    }

    // -------------------------------------------------------------
    // Unknown guarantee
    // -------------------------------------------------------------

    @Test
    fun `release fails for unknown guarantee`() {

        val unknownId =
            UniqueIdentifier()

        val future =
            borrowerNode.startFlow(
                ReleaseGuaranteeFlow(
                    unknownId
                )
            )

        network.runNetwork()

        assertFailsWith<Exception> {
            future.get()
        }
    }

    // -------------------------------------------------------------
    // Contract verification
    // -------------------------------------------------------------

    @Test
    fun `release transaction contains Release command`() {

        val guaranteeId =
            createGuarantee()

        val future =
            borrowerNode.startFlow(
                ReleaseGuaranteeFlow(
                    guaranteeId
                )
            )

        network.runNetwork()

        val stx =
            future.get()

        val releaseCommands =
            stx.tx.commands.filter {
                it.value is GuaranteeContract.Commands.Release
            }

        assertEquals(
            1,
            releaseCommands.size
        )
    }

    // -------------------------------------------------------------
    // Both signatures
    // -------------------------------------------------------------

    @Test
    fun `release transaction contains both signatures`() {

        val guaranteeId =
            createGuarantee()

        val future =
            borrowerNode.startFlow(
                ReleaseGuaranteeFlow(
                    guaranteeId
                )
            )

        network.runNetwork()

        val stx =
            future.get()

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
}
