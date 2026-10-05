package net.corda.samples.obligation.contract

import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.TransactionBuilder
import net.corda.testing.contracts.TransactionDSL
import net.corda.testing.node.MockServices
import net.corda.testing.node.TestIdentity
import net.corda.testing.node.ledger
import net.corda.samples.obligation.states.GuaranteeState
import org.junit.Test
import kotlin.test.assertFailsWith

class GuaranteeContractTests {

    private val ledgerServices = MockServices(
        listOf("net.corda.samples.obligation")
    )

    private val owner = TestIdentity(
        CordaX500Name(
            "Owner",
            "Paris",
            "FR"
        )
    )

    private val beneficiary = TestIdentity(
        CordaX500Name(
            "Beneficiary",
            "Paris",
            "FR"
        )
    )

    private fun guarantee(
        status: GuaranteeState.Status =
            GuaranteeState.Status.ACTIVE
    ): GuaranteeState {

        return GuaranteeState(
            owner = owner.party,
            beneficiary = beneficiary.party,
            obligationId = UniqueIdentifier(),
            status = status,
            linearId = UniqueIdentifier()
        )
    }

    // -------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------

    @Test
    fun `create guarantee must have no inputs`() {

        val output =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        ledgerServices.ledger {

            transaction {

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Create()
                )

                verifies()
            }
        }
    }

    @Test
    fun `create guarantee cannot consume inputs`() {

        val input =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        val output =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Create()
                )

                failsWith(
                    "A guarantee creation transaction must not consume inputs."
                )
            }
        }
    }

    @Test
    fun `owner and beneficiary cannot be identical`() {

        val invalidGuarantee =
            GuaranteeState(
                owner = owner.party,
                beneficiary = owner.party,
                obligationId = UniqueIdentifier(),
                status = GuaranteeState.Status.ACTIVE,
                linearId = UniqueIdentifier()
            )

        ledgerServices.ledger {

            transaction {

                output(
                    invalidGuarantee,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(owner.publicKey),
                    GuaranteeContract.Commands.Create()
                )

                failsWith(
                    "The guarantee owner and beneficiary cannot be the same party."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // RELEASE
    // -------------------------------------------------------------

    @Test
    fun `active guarantee can be released`() {

        val id =
            UniqueIdentifier()

        val input =
            GuaranteeState(
                owner = owner.party,
                beneficiary = beneficiary.party,
                obligationId = UniqueIdentifier(),
                status = GuaranteeState.Status.ACTIVE,
                linearId = id
            )

        val output =
            input.copy(
                status = GuaranteeState.Status.RELEASED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Release()
                )

                verifies()
            }
        }
    }

    @Test
    fun `released guarantee cannot be released again`() {

        val input =
            guarantee(
                GuaranteeState.Status.RELEASED
            )

        val output =
            input.copy(
                status = GuaranteeState.Status.RELEASED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Release()
                )

                failsWith(
                    "Only an ACTIVE guarantee can be released."
                )
            }
        }
    }

    @Test
    fun `release requires both owner and beneficiary signatures`() {

        val input =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        val output =
            input.copy(
                status = GuaranteeState.Status.RELEASED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(owner.publicKey),
                    GuaranteeContract.Commands.Release()
                )

                failsWith(
                    "Both guarantee owner and beneficiary must sign."
                )
            }
        }
    }

    @Test
    fun `release cannot change owner`() {

        val input =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        val output =
            input.copy(
                owner = beneficiary.party,
                status = GuaranteeState.Status.RELEASED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Release()
                )

                failsWith(
                    "The guarantee owner cannot change."
                )
            }
        }
    }

    @Test
    fun `release cannot change linearId`() {

        val input =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        val output =
            input.copy(
                linearId = UniqueIdentifier(),
                status = GuaranteeState.Status.RELEASED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Release()
                )

                failsWith(
                    "The guarantee linearId cannot change."
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CLAIM
    // -------------------------------------------------------------

    @Test
    fun `active guarantee can be claimed`() {

        val input =
            guarantee(
                GuaranteeState.Status.ACTIVE
            )

        val output =
            input.copy(
                status = GuaranteeState.Status.CLAIMED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Claim()
                )

                verifies()
            }
        }
    }

    @Test
    fun `released guarantee cannot be claimed`() {

        val input =
            guarantee(
                GuaranteeState.Status.RELEASED
            )

        val output =
            input.copy(
                status = GuaranteeState.Status.CLAIMED
            )

        ledgerServices.ledger {

            transaction {

                input(
                    input,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                output(
                    output,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )

                command(
                    listOf(
                        owner.publicKey,
                        beneficiary.publicKey
                    ),
                    GuaranteeContract.Commands.Claim()
                )

                failsWith(
                    "Only an ACTIVE guarantee can be claimed."
                )
            }
        }
    }
}
