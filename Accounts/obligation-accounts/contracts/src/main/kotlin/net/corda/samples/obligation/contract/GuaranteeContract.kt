package net.corda.samples.obligation.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.obligation.states.GuaranteeState

class GuaranteeContract : Contract {

    companion object {
        const val GUARANTEE_CONTRACT_ID =
            "net.corda.samples.obligation.contract.GuaranteeContract"
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Activate : TypeOnlyCommandData(), Commands
        class Claim : TypeOnlyCommandData(), Commands
        class Release : TypeOnlyCommandData(), Commands
        class Cancel : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {

            is Commands.Create -> requireThat {

                "No inputs should be consumed when creating a guarantee." using
                        tx.inputs.isEmpty()

                "Only one output should be created when creating a guarantee." using
                        (tx.outputs.size == 1)

                val guarantee = tx.outputsOfType<GuaranteeState>().single()

                "The guaranteed amount must be positive." using
                        (guarantee.guaranteedAmount.quantity > 0)

                "Guarantor, debtor and creditor must all be different." using
                        (guarantee.guarantor != guarantee.debtor &&
                         guarantee.guarantor != guarantee.creditor &&
                         guarantee.debtor != guarantee.creditor)

                "The guarantor and debtor must sign the creation." using
                        (command.signers.toSet() ==
                                setOf(
                                    guarantee.guarantor.owningKey,
                                    guarantee.debtor.owningKey
                                ))
            }

            is Commands.Activate -> requireThat {

                "One input should be consumed when activating a guarantee." using
                        (tx.inputs.size == 1)

                "One output should be created when activating a guarantee." using
                        (tx.outputs.size == 1)

                val input = tx.inputsOfType<GuaranteeState>().single()
                val output = tx.outputsOfType<GuaranteeState>().single()

                "Only the status may change when activating a guarantee." using
                        (input.copy(status = output.status) == output)

                "The guarantee must become ACTIVE." using
                        (output.status == GuaranteeState.Status.ACTIVE)

                "The guarantor and debtor must sign." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.guarantor.owningKey,
                                    input.debtor.owningKey
                                ))
            }

            is Commands.Claim -> requireThat {

                "One input should be consumed when claiming a guarantee." using
                        (tx.inputs.size == 1)

                "One output should be created when claiming a guarantee." using
                        (tx.outputs.size == 1)

                val input = tx.inputsOfType<GuaranteeState>().single()
                val output = tx.outputsOfType<GuaranteeState>().single()

                "Only the status may change when claiming a guarantee." using
                        (input.copy(status = output.status) == output)

                "The guarantee must become CLAIMED." using
                        (output.status == GuaranteeState.Status.CLAIMED)

                "The creditor and guarantor must sign." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.creditor.owningKey,
                                    input.guarantor.owningKey
                                ))
            }

            is Commands.Release -> requireThat {

                "One input should be consumed when releasing a guarantee." using
                        (tx.inputs.size == 1)

                "One output should be created when releasing a guarantee." using
                        (tx.outputs.size == 1)

                val input = tx.inputsOfType<GuaranteeState>().single()
                val output = tx.outputsOfType<GuaranteeState>().single()

                "Only the status may change when releasing a guarantee." using
                        (input.copy(status = output.status) == output)

                "The guarantee must become RELEASED." using
                        (output.status == GuaranteeState.Status.RELEASED)

                "The guarantor and creditor must sign." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.guarantor.owningKey,
                                    input.creditor.owningKey
                                ))
            }

            is Commands.Cancel -> requireThat {

                "One input should be consumed when cancelling a guarantee." using
                        (tx.inputs.size == 1)

                "No output should remain when cancelling a guarantee." using
                        (tx.outputs.isEmpty())

                val input = tx.inputsOfType<GuaranteeState>().single()

                "The guarantor and debtor must sign." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.guarantor.owningKey,
                                    input.debtor.owningKey
                                ))
            }
        }
    }
}
