package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.FlowException
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.states.*
import net.corda.core.contracts.Amount
import java.util.Currency

/**
 * Returns the current status of an obligation.
 *
 * This flow is read-only.
 *
 * It does not create or consume any states and does not require
 * another party to sign anything.
 */
@StartableByRPC
class GetObligationStatusFlow(
    private val obligationId: UniqueIdentifier
) : FlowLogic<ObligationStatus>() {

    companion object {

        object QUERYING_IOU : ProgressTracker.Step(
            "Looking for the obligation."
        )

        object QUERYING_PAYMENTS : ProgressTracker.Step(
            "Looking for payments."
        )

        object QUERYING_COLLATERAL : ProgressTracker.Step(
            "Looking for collateral."
        )

        object QUERYING_GUARANTEES : ProgressTracker.Step(
            "Looking for guarantees."
        )

        object QUERYING_SETTLEMENT : ProgressTracker.Step(
            "Looking for settlement information."
        )

        fun tracker() = ProgressTracker(
            QUERYING_IOU,
            QUERYING_PAYMENTS,
            QUERYING_COLLATERAL,
            QUERYING_GUARANTEES,
            QUERYING_SETTLEMENT
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): ObligationStatus {

        // ---------------------------------------------------------
        // 1. Find the IOU
        // ---------------------------------------------------------

        progressTracker.currentStep = QUERYING_IOU

        val iouStateAndRefs =
            serviceHub.vaultService
                .queryBy<IOUState>()
                .states
                .filter {
                    it.state.data.linearId == obligationId
                }

        if (iouStateAndRefs.size > 1) {
            throw FlowException(
                "Multiple IOU states found for obligation $obligationId."
            )
        }

        val iou =
            iouStateAndRefs
                .singleOrNull()
                ?.state
                ?.data

        // ---------------------------------------------------------
        // 2. Find payments
        // ---------------------------------------------------------

        progressTracker.currentStep = QUERYING_PAYMENTS

        val payments =
            serviceHub.vaultService
                .queryBy<PaymentState>()
                .states
                .map { it.state.data }
                .filter {
                    it.obligationId == obligationId
                }

        // ---------------------------------------------------------
        // 3. Find collateral
        // ---------------------------------------------------------

        progressTracker.currentStep = QUERYING_COLLATERAL

        val collateral =
            serviceHub.vaultService
                .queryBy<CollateralState>()
                .states
                .map { it.state.data }
                .filter {
                    it.obligationId == obligationId
                }

        // ---------------------------------------------------------
        // 4. Find guarantees
        // ---------------------------------------------------------

        progressTracker.currentStep = QUERYING_GUARANTEES

        val guarantees =
            serviceHub.vaultService
                .queryBy<GuaranteeState>()
                .states
                .map { it.state.data }
                .filter {
                    it.obligationId == obligationId
                }

        // ---------------------------------------------------------
        // 5. Find settlement
        // ---------------------------------------------------------

        progressTracker.currentStep = QUERYING_SETTLEMENT

        val settlements =
            serviceHub.vaultService
                .queryBy<SettlementState>()
                .states
                .map { it.state.data }
                .filter {
                    it.obligationId == obligationId
                }

        // ---------------------------------------------------------
        // 6. Calculate remaining amount
        // ---------------------------------------------------------

        val remainingAmount =
            if (iou != null) {
                iou.amount - iou.paid
            } else {
                null
            }

        // ---------------------------------------------------------
        // 7. Determine high-level status
        // ---------------------------------------------------------

        val status =
            when {

                settlements.any {
                    it.status == SettlementState.Status.COMPLETED
                } ->
                    ObligationStatus.Status.SETTLED

                iou == null ->
                    ObligationStatus.Status.CANCELLED_OR_CONSUMED

                remainingAmount != null &&
                        remainingAmount.quantity == 0L ->
                    ObligationStatus.Status.FULLY_PAID

                settlements.any {
                    it.status == SettlementState.Status.IN_PROGRESS
                } ->
                    ObligationStatus.Status.SETTLEMENT_IN_PROGRESS

                else ->
                    ObligationStatus.Status.ACTIVE
            }

        return ObligationStatus(
            obligationId = obligationId,
            status = status,
            iou = iou,
            remainingAmount = remainingAmount,
            payments = payments,
            collateral = collateral,
            guarantees = guarantees,
            settlements = settlements
        )
    }
}


/**
 * Result returned by GetObligationStatusFlow.
 */
data class ObligationStatus(

    val obligationId: UniqueIdentifier,

    val status: Status,

    val iou: IOUState?,

    val remainingAmount: Amount<Currency>?,

    val payments: List<PaymentState>,

    val collateral: List<CollateralState>,

    val guarantees: List<GuaranteeState>,

    val settlements: List<SettlementState>
) {

    enum class Status {

        ACTIVE,

        FULLY_PAID,

        SETTLEMENT_IN_PROGRESS,

        SETTLED,

        CANCELLED_OR_CONSUMED
    }
}
