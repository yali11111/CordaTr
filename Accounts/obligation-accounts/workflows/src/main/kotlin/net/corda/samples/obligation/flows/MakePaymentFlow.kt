package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.IOUContract
import net.corda.samples.obligation.states.IOUState
import net.corda.samples.obligation.states.PaymentState
import java.util.Currency

/**
 * Makes a payment against an existing IOU.
 *
 * The borrower initiates the payment.
 *
 * The transaction:
 *
 *   IOUState (input)
 *          |
 *          +--------------------+
 *          |                    |
 *          v                    v
 *   IOUState (output)      PaymentState
 *
 * The IOU's paid amount is increased by the payment amount.
 */
@InitiatingFlow
@StartableByRPC
class MakePaymentFlow(
    private val obligationId: UniqueIdentifier,
    private val paymentAmount: Amount<Currency>,
    private val paymentReference: String? = null
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_IOU : ProgressTracker.Step(
            "Finding the IOU."
        )

        object VALIDATING_PAYMENT : ProgressTracker.Step(
            "Validating the payment."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the payment transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the payment transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the payment transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the lender's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the payment transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_IOU,
            VALIDATING_PAYMENT,
            BUILDING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            COLLECTING_SIGNATURES,
            FINALISING
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {

        // ---------------------------------------------------------
        // 1. Find the IOU
        // ---------------------------------------------------------

        progressTracker.currentStep = FINDING_IOU

        val iouStateAndRef =
            serviceHub.vaultService
                .queryBy<IOUState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == obligationId
                }
                ?: throw FlowException(
                    "IOU with linearId $obligationId was not found."
                )

        val inputIou = iouStateAndRef.state.data

        // ---------------------------------------------------------
        // 2. Validate payment
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_PAYMENT

        if (ourIdentity != inputIou.borrower) {
            throw FlowException(
                "Only the borrower can make a payment."
            )
        }

        if (paymentAmount.quantity <= 0) {
            throw FlowException(
                "The payment amount must be positive."
            )
        }

        if (paymentAmount.token != inputIou.amount.token) {
            throw FlowException(
                "The payment currency must match the IOU currency."
            )
        }

        val amountOutstanding =
            inputIou.amount - inputIou.paid

        if (paymentAmount > amountOutstanding) {
            throw FlowException(
                "The payment amount cannot exceed the outstanding amount."
            )
        }

        // ---------------------------------------------------------
        // 3. Create the new IOU state
        // ---------------------------------------------------------

        val newPaidAmount =
            inputIou.paid + paymentAmount

        val outputIou =
            inputIou.copy(
                paid = newPaidAmount
            )

        // ---------------------------------------------------------
        // 4. Create PaymentState
        // ---------------------------------------------------------

        val paymentState =
            PaymentState(
                payer = inputIou.borrower,
                payee = inputIou.lender,
                amount = paymentAmount,
                obligationId = inputIou.linearId,
                status = PaymentState.Status.COMPLETED,
                paymentReference = paymentReference
            )

        // ---------------------------------------------------------
        // 5. Build transaction
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val command =
            Command(
                IOUContract.Commands.Settle(),
                listOf(
                    inputIou.borrower.owningKey,
                    inputIou.lender.owningKey
                )
            )

        val txBuilder =
            TransactionBuilder(
                iouStateAndRef.state.notary
            )
                .addInputState(iouStateAndRef)
                .addOutputState(
                    outputIou,
                    IOUContract.IOU_CONTRACT_ID
                )
                .addOutputState(
                    paymentState,
                    IOUContract.IOU_CONTRACT_ID
                )
                .addCommand(command)

        // ---------------------------------------------------------
        // 6. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 7. Sign as borrower
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 8. Collect lender signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val lenderSession =
            initiateFlow(inputIou.lender)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(lenderSession)
                )
            )

        // ---------------------------------------------------------
        // 9. Finalise
        // ---------------------------------------------------------

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(lenderSession)
            )
        )
    }
}


/**
 * Responder for MakePaymentFlow.
 *
 * The lender verifies the payment transaction before signing it.
 */
@InitiatedBy(MakePaymentFlow::class)
class MakePaymentFlowResponder(
    private val counterpartySession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val signTransactionFlow =
            object : SignTransactionFlow(
                counterpartySession
            ) {

                override fun checkTransaction(
                    stx: SignedTransaction
                ) {

                    // -------------------------------------------------
                    // Check IOU input
                    // -------------------------------------------------

                    val inputIou =
                        stx.tx.inputs
                            .map {
                                serviceHub
                                    .toStateAndRef<IOUState>(it)
                            }
                            .map { it.state.data }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one IOU input."
                            )

                    // -------------------------------------------------
                    // Check IOU output
                    // -------------------------------------------------

                    val outputIou =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<IOUState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one IOU output."
                            )

                    // -------------------------------------------------
                    // Check PaymentState
                    // -------------------------------------------------

                    val payment =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<PaymentState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one PaymentState output."
                            )

                    // -------------------------------------------------
                    // Check lender
                    // -------------------------------------------------

                    if (ourIdentity != inputIou.lender) {
                        throw FlowException(
                            "The responder is not the lender of the IOU."
                        )
                    }

                    // -------------------------------------------------
                    // Check IOU identity
                    // -------------------------------------------------

                    if (
                        inputIou.linearId != outputIou.linearId
                    ) {
                        throw FlowException(
                            "The IOU linearId must remain unchanged."
                        )
                    }

                    // -------------------------------------------------
                    // Check PaymentState relationship
                    // -------------------------------------------------

                    if (
                        payment.obligationId != inputIou.linearId
                    ) {
                        throw FlowException(
                            "The payment must reference the IOU."
                        )
                    }

                    if (
                        payment.payer != inputIou.borrower
                    ) {
                        throw FlowException(
                            "The payment payer must be the borrower."
                        )
                    }

                    if (
                        payment.payee != inputIou.lender
                    ) {
                        throw FlowException(
                            "The payment payee must be the lender."
                        )
                    }

                    if (
                        payment.status !=
                        PaymentState.Status.COMPLETED
                    ) {
                        throw FlowException(
                            "The payment must be COMPLETED."
                        )
                    }

                    // -------------------------------------------------
                    // Check paid amount
                    // -------------------------------------------------

                    val expectedPaid =
                        inputIou.paid + payment.amount

                    if (
                        outputIou.paid != expectedPaid
                    ) {
                        throw FlowException(
                            "The IOU paid amount must increase exactly by the payment amount."
                        )
                    }

                    // -------------------------------------------------
                    // Check payment doesn't exceed debt
                    // -------------------------------------------------

                    if (
                        payment.amount >
                        (inputIou.amount - inputIou.paid)
                    ) {
                        throw FlowException(
                            "The payment exceeds the outstanding IOU amount."
                        )
                    }

                    // -------------------------------------------------
                    // Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is IOUContract.Commands.Settle
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected an IOUContract.Commands.Settle command."
                            )

                    // -------------------------------------------------
                    // Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            inputIou.borrower.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The borrower must sign the payment."
                        )
                    }

                    if (
                        !command.signers.contains(
                            inputIou.lender.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The lender must sign the payment."
                        )
                    }
                }
            }

        subFlow(signTransactionFlow)

        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}
