package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.DisputeContract
import net.corda.samples.obligation.states.DisputeState

/**
 * Responds to an existing dispute.
 *
 * Only the respondent of the dispute can initiate this flow.
 *
 * The transaction consumes:
 *
 *     DisputeState(OPEN)
 *
 * and creates:
 *
 *     DisputeState(RESPONDED)
 *
 * Both parties sign the transaction.
 */
@InitiatingFlow
@StartableByRPC
class RespondDisputeFlow(
    private val disputeId: UniqueIdentifier,
    private val response: String
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_DISPUTE : ProgressTracker.Step(
            "Finding the dispute."
        )

        object VALIDATING_RESPONSE : ProgressTracker.Step(
            "Validating the dispute response."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the dispute response transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the dispute response transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the dispute response transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the initiator's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the dispute response transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_DISPUTE,
            VALIDATING_RESPONSE,
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
        // 1. Find the dispute
        // ---------------------------------------------------------

        progressTracker.currentStep = FINDING_DISPUTE

        val disputeStateAndRef =
            serviceHub.vaultService
                .queryBy<DisputeState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == disputeId
                }
                ?: throw FlowException(
                    "Dispute $disputeId was not found."
                )

        val inputDispute =
            disputeStateAndRef.state.data

        // ---------------------------------------------------------
        // 2. Validate response
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_RESPONSE

        if (ourIdentity != inputDispute.respondent) {
            throw FlowException(
                "Only the respondent can respond to this dispute."
            )
        }

        if (
            inputDispute.status !=
            DisputeState.Status.OPEN
        ) {
            throw FlowException(
                "Only an OPEN dispute can be answered."
            )
        }

        if (response.isBlank()) {
            throw FlowException(
                "A dispute response cannot be empty."
            )
        }

        // ---------------------------------------------------------
        // 3. Create updated dispute state
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val outputDispute =
            inputDispute.copy(
                status = DisputeState.Status.RESPONDED,
                response = response
            )

        // ---------------------------------------------------------
        // 4. Create command
        // ---------------------------------------------------------

        val command =
            Command(
                DisputeContract.Commands.Respond(),
                listOf(
                    inputDispute.initiator.owningKey,
                    inputDispute.respondent.owningKey
                )
            )

        // ---------------------------------------------------------
        // 5. Build transaction
        // ---------------------------------------------------------

        val txBuilder =
            TransactionBuilder(
                disputeStateAndRef.state.notary
            )
                .addInputState(disputeStateAndRef)
                .addOutputState(
                    outputDispute,
                    DisputeContract.DISPUTE_CONTRACT_ID
                )
                .addCommand(command)

        // ---------------------------------------------------------
        // 6. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 7. Sign as respondent
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 8. Collect initiator signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val initiatorSession =
            initiateFlow(inputDispute.initiator)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(initiatorSession)
                )
            )

        // ---------------------------------------------------------
        // 9. Finalise
        // ---------------------------------------------------------

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(initiatorSession)
            )
        )
    }
}


/**
 * Responder for RespondDisputeFlow.
 *
 * The dispute initiator verifies the response and signs it.
 */
@InitiatedBy(RespondDisputeFlow::class)
class RespondDisputeFlowResponder(
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
                    // 1. Check input
                    // -------------------------------------------------

                    val inputDispute =
                        stx.tx.inputs
                            .map {
                                serviceHub
                                    .toStateAndRef<DisputeState>(it)
                            }
                            .singleOrNull()
                            ?.state
                            ?.data
                            ?: throw FlowException(
                                "Expected exactly one DisputeState input."
                            )

                    // -------------------------------------------------
                    // 2. Check output
                    // -------------------------------------------------

                    val outputDispute =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<DisputeState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one DisputeState output."
                            )

                    // -------------------------------------------------
                    // 3. Check input status
                    // -------------------------------------------------

                    if (
                        inputDispute.status !=
                        DisputeState.Status.OPEN
                    ) {
                        throw FlowException(
                            "Only an OPEN dispute can be answered."
                        )
                    }

                    // -------------------------------------------------
                    // 4. Check output status
                    // -------------------------------------------------

                    if (
                        outputDispute.status !=
                        DisputeState.Status.RESPONDED
                    ) {
                        throw FlowException(
                            "The output dispute must have RESPONDED status."
                        )
                    }

                    // -------------------------------------------------
                    // 5. Check linearId
                    // -------------------------------------------------

                    if (
                        inputDispute.linearId !=
                        outputDispute.linearId
                    ) {
                        throw FlowException(
                            "The dispute linearId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 6. Check obligationId
                    // -------------------------------------------------

                    if (
                        inputDispute.obligationId !=
                        outputDispute.obligationId
                    ) {
                        throw FlowException(
                            "The obligationId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 7. Check initiator
                    // -------------------------------------------------

                    if (
                        inputDispute.initiator !=
                        outputDispute.initiator
                    ) {
                        throw FlowException(
                            "The dispute initiator cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 8. Check respondent
                    // -------------------------------------------------

                    if (
                        inputDispute.respondent !=
                        outputDispute.respondent
                    ) {
                        throw FlowException(
                            "The dispute respondent cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 9. Check response
                    // -------------------------------------------------

                    if (
                        outputDispute.response.isNullOrBlank()
                    ) {
                        throw FlowException(
                            "A RESPONDED dispute must contain a response."
                        )
                    }

                    // -------------------------------------------------
                    // 10. Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is DisputeContract.Commands.Respond
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a DisputeContract.Commands.Respond command."
                            )

                    // -------------------------------------------------
                    // 11. Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            inputDispute.initiator.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute initiator must sign the response."
                        )
                    }

                    if (
                        !command.signers.contains(
                            inputDispute.respondent.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute respondent must sign the response."
                        )
                    }

                    // -------------------------------------------------
                    // 12. Ensure no IOU is modified
                    // -------------------------------------------------

                    val iouOutputs =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<
                                net.corda.samples.obligation.states.IOUState
                            >()

                    if (iouOutputs.isNotEmpty()) {
                        throw FlowException(
                            "Responding to a dispute must not modify the IOU."
                        )
                    }
                }
            }

        // Sign the transaction.
        subFlow(signTransactionFlow)

        // Receive finality.
        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}
