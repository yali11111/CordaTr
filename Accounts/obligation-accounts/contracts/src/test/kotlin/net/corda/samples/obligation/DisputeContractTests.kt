package net.corda.samples.obligation.contract

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.samples.obligation.states.DisputeState
import org.junit.Test

class DisputeContractTests {

    private val ledgerServices = MockServices(
        listOf("net.corda.samples.obligation")
    )

    private val initiator = TestIdentity(
        CordaX500Name(
            "Initiator",
            "Paris",
            "FR"
        )
    )

    private val respondent = TestIdentity(
        CordaX500Name(
            "Respondent",
            "Paris",
            "FR"
        )
    )

    private fun dispute(
        status: DisputeState.Status =
            DisputeState.Status.OPEN,
        response: String? = null
    ): DisputeState {

        return DisputeState(
            initiator = initiator.party,
            respondent = respondent.party,
            obligationId = UniqueIdentifier(),
            reason = "Payment not recognised",
            response = response,
            status = status,
            linearId = UniqueIdentifier()
        )
    }

    // -------------------------------------------------------------
    // OPEN
    // -------------------------------------------------------------

    @Test
    fun `open dispute creates one output`() {

        val output =
            dispute(
                DisputeState.Status.OPEN
            )

        ledgerServices.ledger {

            transaction {

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Open()
                )

                verifies()
            }
        }
    }

    @Test
    fun `open dispute cannot consume input`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            dispute(
                DisputeState.Status.OPEN
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Open()
                )

                failsWith(
                    "Opening a dispute must not consume an existing dispute."
                )
            }
        }
    }

    @Test
    fun `initiator and respondent cannot be identical`() {

        val invalid =
            DisputeState(
                initiator = initiator.party,
                respondent = initiator.party,
                obligationId = UniqueIdentifier(),
                reason = "Invalid dispute",
                response = null,
                status = DisputeState.Status.OPEN,
                linearId = UniqueIdentifier()
            )

        ledgerServices.ledger {

            transaction {

                output(
                    invalid,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(initiator.publicKey),
                    DisputeContract.Commands.Open()
                )

                failsWith(
                    "The initiator and respondent cannot be the same party."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // RESPOND
    // -------------------------------------------------------------

    @Test
    fun `open dispute can be responded`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESPONDED,
                response = "Payment was made."
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                verifies()
            }
        }
    }

    @Test
    fun `response cannot be empty`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESPONDED,
                response = ""
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "A responded dispute must contain a response."
                )
            }
        }
    }

    @Test
    fun `responded dispute cannot be responded again`() {

        val input =
            dispute(
                DisputeState.Status.RESPONDED,
                "Original response"
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESPONDED,
                response = "Second response"
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "Only an OPEN dispute can be responded to."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // RESOLVE
    // -------------------------------------------------------------

    @Test
    fun `responded dispute can be resolved`() {

        val input =
            dispute(
                DisputeState.Status.RESPONDED,
                "Payment was made."
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESOLVED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.Commands.Resolve()
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Resolve()
                )

                verifies()
            }
        }
    }

    @Test
    fun `open dispute cannot be resolved directly`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESOLVED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Resolve()
                )

                failsWith(
                    "Only a RESPONDED dispute can be resolved."
                )
            }
        }
    }

    @Test
    fun `resolved dispute cannot be modified`() {

        val input =
            dispute(
                DisputeState.Status.RESOLVED,
                "Payment verified."
            )

        val output =
            input.copy(
                status = DisputeState.Status.RESPONDED,
                response = "Another response"
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "A resolved dispute cannot be modified."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // IMMUTABILITY
    // -------------------------------------------------------------

    @Test
    fun `response cannot change initiator`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                initiator = respondent.party,
                status = DisputeState.Status.RESPONDED,
                response = "Response"
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "The dispute initiator cannot change."
                )
            }
        }
    }

    @Test
    fun `response cannot change respondent`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                respondent = initiator.party,
                status = DisputeState.Status.RESPONDED,
                response = "Response"
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "The dispute respondent cannot change."
                )
            }
        }
    }

    @Test
    fun `response cannot change linearId`() {

        val input =
            dispute(
                DisputeState.Status.OPEN
            )

        val output =
            input.copy(
                linearId = UniqueIdentifier(),
                status = DisputeState.Status.RESPONDED,
                response = "Response"
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                output(
                    output,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )

                command(
                    listOf(
                        initiator.publicKey,
                        respondent.publicKey
                    ),
                    DisputeContract.Commands.Respond()
                )

                failsWith(
                    "The dispute linearId cannot change."
                )
            }
        }
    }
}
