package net.corda.samples.obligation.flow

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.samples.obligation.contract.DisputeContract
import net.corda.samples.obligation.flows.OpenDisputeFlow
import net.corda.samples.obligation.states.DisputeState
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenDisputeFlowTests {

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

    private fun getDisputes(
        node: StartedMockNode
    ): List<Vault.StateAndRef<DisputeState>> {

        return node.services.vaultService
            .queryBy<DisputeState>()
            .states
    }

    @Test
    fun `borrower can open a dispute`() {

        val obligationId =
            UniqueIdentifier()

        val flow =
            OpenDisputeFlow(
                obligationId = obligationId,
                respondent = lender.party,
                reason = "Payment not recognised"
            )

        val future =
            borrowerNode.startFlow(flow)

        network.runNetwork()

        val stx =
            future.get()

        assertNotNull(stx)

        val disputes =
            getDisputes(borrowerNode)

        assertEquals(
            1,
            disputes.size
        )

        val dispute =
            disputes.single().state.data

        assertEquals(
            borrower.party,
            dispute.initiator
        )

        assertEquals(
            lender.party,
            dispute.respondent
        )

        assertEquals(
            obligationId,
            dispute.obligationId
        )

        assertEquals(
            "Payment not recognised",
            dispute.reason
        )

        assertEquals(
            DisputeState.Status.OPEN,
            dispute.status
        )
    }

    @Test
    fun `lender can open a dispute`() {

        val obligationId =
            UniqueIdentifier()

        val flow =
            OpenDisputeFlow(
                obligationId = obligationId,
                respondent = borrower.party,
                reason = "Obligation amount is incorrect"
            )

        val future =
            lenderNode.startFlow(flow)

        network.runNetwork()

        val stx =
            future.get()

        assertNotNull(stx)

        val disputes =
            getDisputes(lenderNode)

        assertEquals(
            1,
            disputes.size
        )

        val dispute =
            disputes.single().state.data

        assertEquals(
            lender.party,
            dispute.initiator
        )

        assertEquals(
            borrower.party,
            dispute.respondent
        )

        assertEquals(
            DisputeState.Status.OPEN,
            dispute.status
        )
    }

    @Test
    fun `dispute is visible in both vaults`() {

        val obligationId =
            UniqueIdentifier()

        val future =
            borrowerNode.startFlow(
                OpenDisputeFlow(
                    obligationId = obligationId,
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        future.get()

        assertEquals(
            1,
            getDisputes(borrowerNode).size
        )

        assertEquals(
            1,
            getDisputes(lenderNode).size
        )

        assertEquals(
            DisputeState.Status.OPEN,
            getDisputes(lenderNode)
                .single()
                .state
                .data
                .status
        )
    }

    @Test
    fun `open transaction contains Open command`() {

        val future =
            borrowerNode.startFlow(
                OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
                )
            )

        network.runNetwork()

        val stx =
            future.get()

        val commands =
            stx.tx.commands.filter {
                it.value is DisputeContract.Commands.Open
            }

        assertEquals(
            1,
            commands.size
        )
    }

    @Test
    fun `both parties sign dispute opening`() {

        val future =
            borrowerNode.startFlow(
                OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = "Payment not recognised"
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

    @Test
    fun `opening dispute with empty reason fails`() {

        val future =
            borrowerNode.startFlow(
                OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = lender.party,
                    reason = ""
                )
            )

        network.runNetwork()

        assertFailsWith<Exception> {
            future.get()
        }
    }

    @Test
    fun `initiator cannot be respondent`() {

        val future =
            borrowerNode.startFlow(
                OpenDisputeFlow(
                    obligationId = UniqueIdentifier(),
                    respondent = borrower.party,
                    reason = "Invalid dispute"
                )
            )

        network.runNetwork()

        assertFailsWith<Exception> {
            future.get()
        }
    }
}
