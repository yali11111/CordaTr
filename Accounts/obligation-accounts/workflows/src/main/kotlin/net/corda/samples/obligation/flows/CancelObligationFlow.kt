package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.IOUContract
import net.corda.samples.obligation.states.IOUState

/**
 * Cancels an existing IOU.
 *
 * The borrower initiates the cancellation.
 *
 * The transaction consumes the existing IOU and does not create
 * another IOU output.
 */
@InitiatingFlow
@StartableByRPC
class CancelObligationFlow(
    private val obligationId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_IOU : ProgressTracker.Step(
            "Finding the IOU to cancel."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the cancellation transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the cancellation transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the cancellation transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the lender's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the cancellation transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_IOU,
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

        val iou = iouStateAndRef.state.data

        // ---------------------------------------------------------
        // 2. Validate the IOU
        // ---------------------------------------------------------

        if (ourIdentity != iou.borrower) {
            throw FlowException(
                "Only the borrower can cancel this obligation."
            )
        }

        /*
         * An IOU that has already received a payment should not
         * normally be cancelled.
         *
         * If your IOUState uses a different representation for
         * paid, adapt this condition accordingly.
         */
        if (iou.paid.quantity > 0) {
            throw FlowException(
                "An IOU with existing payments cannot be cancelled."
            )
        }

        // ---------------------------------------------------------
        // 3. Build the command
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val command = Command(
            IOUContract.Commands.Cancel(),
            listOf(
                iou.borrower.owningKey,
                iou.lender.owningKey
            )
        )

        // ---------------------------------------------------------
        // 4. Build transaction
        // ---------------------------------------------------------

        val txBuilder = TransactionBuilder(
            iouStateAndRef.state.notary
        )
            .addInputState(iouStateAndRef)
            .addCommand(command)

        // ---------------------------------------------------------
        // 5. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 6. Sign as borrower
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 7. Collect lender signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val lenderSession =
            initiateFlow(iou.lender)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(lenderSession)
                )
            )

        // ---------------------------------------------------------
        // 8. Finalise
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
 * Responder for CancelObligationFlow.
 *
 * The lender verifies and signs the cancellation.
 */
@InitiatedBy(CancelObligationFlow::class)
class CancelObligationFlowResponder(
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

                    val inputs =
                        stx.tx.inputs

                    if (inputs.size != 1) {
                        throw FlowException(
                            "A cancellation must consume exactly one state."
                        )
                    }

                    val input =
                        inputs
                            .map { serviceHub.toStateAndRef<IOUState>(it) }
                            .single()
                            .state
                            .data

                    if (input.lender != ourIdentity) {
                        throw FlowException(
                            "The responder is not the lender of the IOU."
                        )
                    }

                    /*
                     * A cancellation should not create another IOU.
                     */
                    if (
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<IOUState>()
                            .isNotEmpty()
                    ) {
                        throw FlowException(
                            "A cancelled IOU must not have an IOU output."
                        )
                    }

                    /*
                     * Verify that the expected Cancel command exists.
                     */
                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is IOUContract.Commands.Cancel
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected an IOUContract.Commands.Cancel command."
                            )

                    /*
                     * Both borrower and lender must be signers.
                     */
                    if (
                        !command.signers.contains(
                            input.borrower.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The borrower must sign the cancellation."
                        )
                    }

                    if (
                        !command.signers.contains(
                            input.lender.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The lender must sign the cancellation."
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
