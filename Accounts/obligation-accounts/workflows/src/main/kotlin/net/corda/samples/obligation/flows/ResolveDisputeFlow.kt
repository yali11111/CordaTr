package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.DisputeContract
import net.corda.samples.obligation.states.DisputeState

/**
 * Resolves an existing dispute.
 *
 * Both the initiator and the respondent must agree to the resolution.
 *
 * The transaction consumes the OPEN DisputeState and creates a new
 * DisputeState with status RESOLVED.
 */
@InitiatingFlow
@StartableByRPC
class ResolveDisputeFlow(
    private val disputeId: net.corda.core.contracts.UniqueIdentifier,
    private val resolution: String
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_DISPUTE : ProgressTracker.Step(
            "Finding the dispute."
        )

        object VALIDATING_RESOLUTION : ProgressTracker.Step(
            "Validating the resolution."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the dispute resolution transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the dispute resolution transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the dispute resolution transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the other party's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the dispute resolution transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_DISPUTE,
            VALIDATING_RESOLUTION,
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
        // 2. Validate resolution
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_RESOLUTION

        if (
            ourIdentity != inputDispute.initiator &&
            ourIdentity != inputDispute.respondent
        ) {
            throw FlowException(
                "Only a party involved in the dispute can resolve it."
            )
        }

        if (
            inputDispute.status !=
            DisputeState.Status.OPEN
        ) {
            throw FlowException(
                "Only an OPEN dispute can be resolved."
            )
        }

        if (resolution.isBlank()) {
            throw FlowException(
                "A resolution must be provided."
            )
        }

        // ---------------------------------------------------------
        // 3. Determine counterparty
        // ---------------------------------------------------------

        val counterparty =
            if (ourIdentity == inputDispute.initiator) {
                inputDispute.respondent
            } else {
                inputDispute.initiator
            }

        // ---------------------------------------------------------
        // 4. Create resolved state
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val outputDispute =
            inputDispute.copy(
                status = DisputeState.Status.RESOLVED,
                resolution = resolution
            )

        // ---------------------------------------------------------
        // 5. Create command
        // ---------------------------------------------------------

        val command =
            Command(
                DisputeContract.Commands.Resolve(),
                listOf(
                    inputDispute.initiator.owningKey,
                    inputDispute.respondent.owningKey
                )
            )

        // ---------------------------------------------------------
        // 6. Build transaction
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
        // 7. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 8. Sign as initiator/resolver
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 9. Collect counterparty signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val counterpartySession =
            initiateFlow(counterparty)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(counterpartySession)
                )
            )

        // ---------------------------------------------------------
        // 10. Finalise
        // ---------------------------------------------------------

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(counterpartySession)
            )
        )
    }
}


/**
 * Responder for ResolveDisputeFlow.
 *
 * The other party verifies and signs the resolution.
 */
@InitiatedBy(ResolveDisputeFlow::class)
class ResolveDisputeFlowResponder(
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
                    // Check input
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
                    // Check output
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
                    // Check input status
                    // -------------------------------------------------

                    if (
                        inputDispute.status !=
                        DisputeState.Status.OPEN
                    ) {
                        throw FlowException(
                            "Only an OPEN dispute can be resolved."
                        )
                    }

                    // -------------------------------------------------
                    // Check output status
                    // -------------------------------------------------

                    if (
                        outputDispute.status !=
                        DisputeState.Status.RESOLVED
                    ) {
                        throw FlowException(
                            "The output dispute must have RESOLVED status."
                        )
                    }

                    // -------------------------------------------------
                    // Check identity
                    // -------------------------------------------------

                    if (
                        inputDispute.linearId !=
                        outputDispute.linearId
                    ) {
                        throw FlowException(
                            "The dispute linearId must not change."
                        )
                    }

                    // -------------------------------------------------
                    // Check obligation
                    // -------------------------------------------------

                    if (
                        inputDispute.obligationId !=
                        outputDispute.obligationId
                    ) {
                        throw FlowException(
                            "The obligationId must not change."
                        )
                    }

                    // -------------------------------------------------
                    // Check parties
                    // -------------------------------------------------

                    if (
                        inputDispute.initiator !=
                        outputDispute.initiator
                    ) {
                        throw FlowException(
                            "The dispute initiator cannot change."
                        )
                    }

                    if (
                        inputDispute.respondent !=
                        outputDispute.respondent
                    ) {
                        throw FlowException(
                            "The dispute respondent cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // Check resolution
                    // -------------------------------------------------

                    if (
                        outputDispute.resolution.isNullOrBlank()
                    ) {
                        throw FlowException(
                            "A resolved dispute must contain a resolution."
                        )
                    }

                    // -------------------------------------------------
                    // Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is DisputeContract.Commands.Resolve
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a DisputeContract.Commands.Resolve command."
                            )

                    // -------------------------------------------------
                    // Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            inputDispute.initiator.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute initiator must sign the resolution."
                        )
                    }

                    if (
                        !command.signers.contains(
                            inputDispute.respondent.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The dispute respondent must sign the resolution."
                        )
                    }

                    // -------------------------------------------------
                    // No unexpected IOU output
                    // -------------------------------------------------

                    val iouOutputs =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<
                                net.corda.samples.obligation.states.IOUState
                            >()

                    if (iouOutputs.isNotEmpty()) {
                        throw FlowException(
                            "Resolving a dispute must not modify the IOU."
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
