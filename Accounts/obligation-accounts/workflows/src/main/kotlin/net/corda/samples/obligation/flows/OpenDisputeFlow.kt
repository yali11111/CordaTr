package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.DisputeContract
import net.corda.samples.obligation.states.DisputeState

/**
 * Opens a dispute against an obligation.
 *
 * Either the borrower or the lender can initiate a dispute.
 *
 * Example:
 *
 *     Borrower
 *        |
 *        | OpenDispute
 *        v
 *   DisputeState
 *       OPEN
 *        |
 *        v
 *     Lender
 *
 * The respondent must sign the transaction.
 */
@InitiatingFlow
@StartableByRPC
class OpenDisputeFlow(
    private val obligationId: UniqueIdentifier,
    private val respondent: Party,
    private val reason: String
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_OBLIGATION : ProgressTracker.Step(
            "Finding the obligation."
        )

        object VALIDATING_DISPUTE : ProgressTracker.Step(
            "Validating the dispute information."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the dispute transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the dispute transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the dispute transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the respondent's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the dispute transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_OBLIGATION,
            VALIDATING_DISPUTE,
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
        // 1. Find the obligation
        // ---------------------------------------------------------

        progressTracker.currentStep = FINDING_OBLIGATION

        val obligation =
            serviceHub.vaultService
                .queryBy<net.corda.samples.obligation.states.IOUState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == obligationId
                }
                ?: throw FlowException(
                    "Obligation $obligationId was not found."
                )

        val iou = obligation.state.data

        // ---------------------------------------------------------
        // 2. Validate dispute
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_DISPUTE

        if (
            ourIdentity != iou.borrower &&
            ourIdentity != iou.lender
        ) {
            throw FlowException(
                "Only the borrower or lender can open a dispute."
            )
        }

        if (respondent != iou.borrower &&
            respondent != iou.lender
        ) {
            throw FlowException(
                "The respondent must be the borrower or lender of the obligation."
            )
        }

        if (respondent == ourIdentity) {
            throw FlowException(
                "The initiator and respondent cannot be the same party."
            )
        }

        if (reason.isBlank()) {
            throw FlowException(
                "A dispute reason must be provided."
            )
        }

        // ---------------------------------------------------------
        // 3. Build DisputeState
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val disputeState =
            DisputeState(
                initiator = ourIdentity,
                respondent = respondent,
                obligationId = obligationId,
                reason = reason,
                status = DisputeState.Status.OPEN
            )

        // ---------------------------------------------------------
        // 4. Build command
        // ---------------------------------------------------------

        val command =
            Command(
                DisputeContract.Commands.Open(),
                listOf(
                    ourIdentity.owningKey,
                    respondent.owningKey
                )
            )

        // ---------------------------------------------------------
        // 5. Build transaction
        // ---------------------------------------------------------

        val txBuilder =
            TransactionBuilder(
                obligation.state.notary
            )
                .addOutputState(
                    disputeState,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )
                .addCommand(command)

        // ---------------------------------------------------------
        // 6. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 7. Sign as initiator
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 8. Collect respondent signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val respondentSession =
            initiateFlow(respondent)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(respondentSession)
                )
            )

        // ---------------------------------------------------------
        // 9. Finalise
        // ---------------------------------------------------------

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(respondentSession)
            )
        )
    }
}


/**
 * Responder for OpenDisputeFlow.
 *
 * The respondent verifies the dispute before signing.
 */
@InitiatedBy(OpenDisputeFlow::class)
class OpenDisputeFlowResponder(
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
                    // Check output
                    // -------------------------------------------------

                    val dispute =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<DisputeState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one DisputeState output."
                            )

                    // -------------------------------------------------
                    // Check status
                    // -------------------------------------------------

                    if (
                        dispute.status !=
                        DisputeState.Status.OPEN
                    ) {
                        throw FlowException(
                            "A new dispute must have OPEN status."
                        )
                    }

                    // -------------------------------------------------
                    // Check respondent
                    // -------------------------------------------------

                    if (
                        dispute.respondent != ourIdentity
                    ) {
                        throw FlowException(
                            "The responder is not the respondent of the dispute."
                        )
                    }

                    // -------------------------------------------------
                    // Check initiator / respondent
                    // -------------------------------------------------

                    if (
                        dispute.initiator ==
                        dispute.respondent
                    ) {
                        throw FlowException(
                            "The initiator and respondent cannot be the same party."
                        )
                    }

                    // -------------------------------------------------
                    // Check reason
                    // -------------------------------------------------

                    if (
                        dispute.reason.isBlank()
                    ) {
                        throw FlowException(
                            "The dispute reason cannot be empty."
                        )
                    }

                    // -------------------------------------------------
                    // Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is DisputeContract.Commands.Open
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a DisputeContract.Commands.Open command."
                            )

                    // -------------------------------------------------
                    // Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            dispute.initiator.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute initiator must sign."
                        )
                    }

                    if (
                        !command.signers.contains(
                            dispute.respondent.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute respondent must sign."
                        )
                    }
                }
            }

        // Sign transaction.
        subFlow(signTransactionFlow)

        // Receive finality.
        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}
