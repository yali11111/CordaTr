package net.corda.samples.obligation.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant

/**
 * Represents the settlement process of an obligation.
 *
 * A SettlementState is created when the parties begin the final
 * settlement of an IOU.
 *
 * PaymentState represents individual payments, while
 * SettlementState represents the overall settlement process.
 */
data class SettlementState(

    /**
     * Party that originally borrowed the funds.
     */
    val borrower: Party,

    /**
     * Party that originally lent the funds.
     */
    val lender: Party,

    /**
     * Identifier of the IOU being settled.
     */
    val obligationId: UniqueIdentifier,

    /**
     * Total amount that was outstanding when settlement started.
     */
    val amountToSettle: Long,

    /**
     * Currency used for the settlement.
     *
     * Example: "EUR", "USD", "GBP".
     */
    val currency: String,

    /**
     * Current status of the settlement.
     */
    val status: Status = Status.INITIATED,

    /**
     * Time at which the settlement was initiated.
     */
    val initiatedAt: Instant = Instant.now(),

    /**
     * Time at which the settlement was completed.
     */
    val completedAt: Instant? = null,

    /**
     * Unique identifier of this settlement.
     */
    override val linearId: UniqueIdentifier = UniqueIdentifier()

) : LinearState {

    /**
     * Lifecycle of a settlement.
     */
    enum class Status {

        /**
         * Settlement process has been initiated.
         */
        INITIATED,

        /**
         * Payment/settlement is currently being processed.
         */
        IN_PROGRESS,

        /**
         * Obligation has been completely settled.
         */
        COMPLETED,

        /**
         * Settlement failed.
         */
        FAILED,

        /**
         * Settlement was cancelled.
         */
        CANCELLED
    }

    /**
     * Parties involved in the settlement.
     */
    override val participants: List<AbstractParty>
        get() = listOf(
            borrower,
            lender
        )
}
