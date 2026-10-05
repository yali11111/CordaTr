package net.corda.samples.obligation.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.obligation.states.DisputeState

class DisputeContract : Contract {

    companion object {
        const val DISPUTE_CONTRACT_ID =
            "net.corda.samples.obligation.contract.DisputeContract"
    }

    interface Commands : CommandData {
        class Open : TypeOnlyCommandData(), Commands
        class Respond : TypeOnlyCommandData(), Commands
        class Resolve : TypeOnlyCommandData(), Commands
        class Reject : TypeOnlyCommandData(), Commands
        class Cancel : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {

            is Commands.Open -> requireThat {

                "No inputs should be consumed when opening a dispute." using
                        tx.inputs.isEmpty()

                "Only one output should be created when opening a dispute." using
                        (tx.outputs.size == 1)

                val dispute = tx.outputsOfType<DisputeState>().single()

                "The disputed amount must be positive." using
                        (dispute.disputedAmount.quantity > 0)

                "Claimant and respondent cannot be the same party." using
                        (dispute.claimant != dispute.respondent)

                "The claimant and respondent must sign." using
                        (command.signers.toSet() ==
                                setOf(
                                    dispute.claimant.owningKey,
                                    dispute.respondent.owningKey
                                ))
            }

            is Commands.Respond -> requireThat {

                "One dispute input is required." using
                        (tx.inputs.size == 1)

                "One dispute output is required." using
                        (tx.outputs.size == 1)

                val input = tx.inputsOfType<DisputeState>().single()
                val output = tx.outputsOfType<DisputeState>().single()

                "Only the status may change." using
                        (input.copy(status = output.status) == output)

                "The dispute must become RESPONDED." using
                        (output.status == DisputeState.Status.RESPONDED)

                "The respondent must sign." using
                        (command.signers.toSet() ==
                                setOf(input.respondent.owningKey))
            }

            is Commands.Resolve -> requireThat {

                "One dispute input is required." using
                        (tx.inputs.size == 1)

                "One dispute output is required." using
                        (tx.outputs.size == 1)

                val input = tx.inputsOfType<DisputeState>().single()
                val output = tx.outputsOfType<DisputeState>().single()

                "The dispute must become RESOLVED." using
                        (output.status == DisputeState.Status.RESOLVED)

                "Only the resolution and status may change." using
                        (input.copy(
                            status = output.status,
                            resolution = output.resolution
                        ) == output)

                "Both parties must sign the resolution." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.claimant.owningKey,
                                    input.respondent.owningKey
                                ))
            }

            is Commands.Reject -> requireThat {

                "One dispute input is required." using
                        (tx.inputs.size == 1)

                "No output should remain after rejection." using
                        (tx.outputs.isEmpty())

                val input = tx.inputsOfType<DisputeState>().single()

                "Both parties must sign the rejection." using
                        (command.signers.toSet() ==
                                setOf(
                                    input.claimant.owningKey,
                                    input.respondent.owningKey
                                ))
            }

            is Commands.Cancel -> requireThat {

                "One dispute input is required." using
                        (tx.inputs.size == 1)

                "No output should remain when cancelling a dispute." using
                        (tx.outputs.isEmpty())

                val input = tx.inputsOfType<DisputeState>().single()

                "The claimant must sign the cancellation." using
                        (command.signers.toSet() ==
                                setOf(input.claimant.owningKey))
            }
        }
    }
}
