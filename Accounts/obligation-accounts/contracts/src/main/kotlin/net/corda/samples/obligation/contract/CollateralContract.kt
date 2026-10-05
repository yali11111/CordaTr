package net.corda.samples.obligation.contract

import net.corda.core.contracts.Contract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.obligation.states.CollateralState

/**
 * Contract governing the lifecycle of a CollateralState.
 *
 * A collateral is used to secure an existing obligation/IOU.
 *
 * Lifecycle:
 *
 *      Create
 *        |
 *        v
 *      LOCKED
 *       /   \
 *      /     \
 *     v       v
 * RELEASED   CLAIMED
 */
class CollateralContract : Contract {

    companion object {
        const val COLLATERAL_CONTRACT_ID =
            "net.corda.samples.obligation.contract.CollateralContract"
    }

    /**
     * Commands supported by this contract.
     */
    interface Commands : CommandData {

        /**
         * Creates a new collateral.
         */
        class Create : TypeOnlyCommandData(), Commands

        /**
         * Releases the collateral back to its owner.
         */
        class Release : TypeOnlyCommandData(), Commands

        /**
         * Allows the beneficiary to claim the collateral.
         */
        class Claim : TypeOnlyCommandData(), Commands
    }

    /**
     * Verifies transactions involving CollateralState.
     */
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {

            // ---------------------------------------------------------
            // CREATE
            // ---------------------------------------------------------
            is Commands.Create -> requireThat {

                "No inputs should be consumed when creating collateral." using
                        tx.inputs.isEmpty()

                "Only one output should be created when creating collateral." using
                        (tx.outputs.size == 1)

                val collateral =
                    tx.outputsOfType<CollateralState>().single()

                "The collateral amount must be positive." using
                        (collateral.collateralAmount.quantity > 0)

                "The owner and beneficiary cannot be the same party." using
                        (collateral.owner != collateral.beneficiary)

                "A newly created collateral must have LOCKED status." using
                        (collateral.status == CollateralState.Status.LOCKED)

                "The owner and beneficiary must sign the collateral creation." using
                        (
                            command.signers.toSet() ==
                                    setOf(
                                        collateral.owner.owningKey,
                                        collateral.beneficiary.owningKey
                                    )
                        )
            }

            // ---------------------------------------------------------
            // RELEASE
            // ---------------------------------------------------------
            is Commands.Release -> requireThat {

                "One collateral input should be consumed when releasing collateral." using
                        (tx.inputs.size == 1)

                "One collateral output should be created when releasing collateral." using
                        (tx.outputs.size == 1)

                val input =
                    tx.inputsOfType<CollateralState>().single()

                val output =
                    tx.outputsOfType<CollateralState>().single()

                "The collateral owner must remain unchanged." using
                        (input.owner == output.owner)

                "The collateral beneficiary must remain unchanged." using
                        (input.beneficiary == output.beneficiary)

                "The collateral amount must remain unchanged." using
                        (input.collateralAmount == output.collateralAmount)

                "The obligation ID must remain unchanged." using
                        (input.obligationId == output.obligationId)

                "The linear ID must remain unchanged." using
                        (input.linearId == output.linearId)

                "The collateral must become RELEASED." using
                        (output.status == CollateralState.Status.RELEASED)

                "Only the status may change when releasing collateral." using
                        (
                            input.copy(status = output.status) == output
                        )

                "The owner and beneficiary must sign the collateral release." using
                        (
                            command.signers.toSet() ==
                                    setOf(
                                        input.owner.owningKey,
                                        input.beneficiary.owningKey
                                    )
                        )
            }

            // ---------------------------------------------------------
            // CLAIM
            // ---------------------------------------------------------
            is Commands.Claim -> requireThat {

                "One collateral input should be consumed when claiming collateral." using
                        (tx.inputs.size == 1)

                "No collateral output should remain after a claim." using
                        (tx.outputs.isEmpty())

                val input =
                    tx.inputsOfType<CollateralState>().single()

                "The beneficiary must sign the collateral claim." using
                        (
                            command.signers.toSet() ==
                                    setOf(input.beneficiary.owningKey)
                        )
            }
        }
    }
}