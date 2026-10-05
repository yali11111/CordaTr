package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.GuaranteeContract
import net.corda.samples.obligation.states.GuaranteeState

/**
 * Releases an existing guarantee.
 *
 * The guarantee must currently be ACTIVE.
 *
 * Both the owner and the beneficiary must sign the transaction.
 *
 * Transaction:
 *
 *     GuaranteeState(ACTIVE)
 *              |
 *              | Release
 *              v
 *     GuaranteeState(RELEASED)
 */
@InitiatingFlow
@StartableByRPC
class ReleaseGuaranteeFlow(
    private val guaranteeId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_GUARANTEE : ProgressTracker.Step(
            "Finding the guarantee."
        )

        object VALIDATING_GUARANTEE : ProgressTracker.Step(
            "Validating the guarantee release."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the guarantee release transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the guarantee release transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the guarantee release transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the other party's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the guarantee release transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_GUARANTEE,
            VALIDATING_GUARANTEE,
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
        // 1. Find guarantee
        // ---------------------------------------------------------

        progressTracker.currentStep = FINDING_GUARANTEE

        val guaranteeStateAndRef =
            serviceHub.vaultService
                .queryBy<GuaranteeState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == guaranteeId
                }
                ?: throw FlowException(
                    "Guarantee $guaranteeId was not found."
                )

        val inputGuarantee =
            guaranteeStateAndRef.state.data

        // ---------------------------------------------------------
        // 2. Validate guarantee
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_GUARANTEE

        if (
            ourIdentity != inputGuarantee.owner &&
            ourIdentity != inputGuarantee.beneficiary
        ) {
            throw FlowException(
                "Only the guarantee owner or beneficiary can release the guarantee."
            )
        }

        if (
            inputGuarantee.status !=
            GuaranteeState.Status.ACTIVE
        ) {
            throw FlowException(
                "Only an ACTIVE guarantee can be released."
            )
        }

        if (
            inputGuarantee.owner ==
            inputGuarantee.beneficiary
        ) {
            throw FlowException(
                "The guarantee owner and beneficiary cannot be the same party."
            )
        }

        // ---------------------------------------------------------
        // 3. Determine counterparty
        // ---------------------------------------------------------

        val counterparty =
            if (ourIdentity == inputGuarantee.owner) {
                inputGuarantee.beneficiary
            } else {
                inputGuarantee.owner
            }

        // ---------------------------------------------------------
        // 4. Create output state
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val outputGuarantee =
            inputGuarantee.copy(
                status = GuaranteeState.Status.RELEASED
            )

        // ---------------------------------------------------------
        // 5. Create command
        // ---------------------------------------------------------

        val command =
            Command(
                GuaranteeContract.Commands.Release(),
                listOf(
                    inputGuarantee.owner.owningKey,
                    inputGuarantee.beneficiary.owningKey
                )
            )

        // ---------------------------------------------------------
        // 6. Build transaction
        // ---------------------------------------------------------

        val txBuilder =
            TransactionBuilder(
                guaranteeStateAndRef.state.notary
            )
                .addInputState(guaranteeStateAndRef)
                .addOutputState(
                    outputGuarantee,
                    GuaranteeContract.GUARANTEE_CONTRACT_ID
                )
                .addCommand(command)

        // ---------------------------------------------------------
        // 7. Verify
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 8. Sign
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
 * Responder for ReleaseGuaranteeFlow.
 *
 * The other participant verifies and signs the release.
 */
@InitiatedBy(ReleaseGuaranteeFlow::class)
class ReleaseGuaranteeFlowResponder(
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

                    val inputGuarantee =
                        stx.tx.inputs
                            .map {
                                serviceHub
                                    .toStateAndRef<GuaranteeState>(it)
                            }
                            .singleOrNull()
                            ?.state
                            ?.data
                            ?: throw FlowException(
                                "Expected exactly one GuaranteeState input."
                            )

                    // -------------------------------------------------
                    // 2. Check output
                    // -------------------------------------------------

                    val outputGuarantee =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<GuaranteeState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one GuaranteeState output."
                            )

                    // -------------------------------------------------
                    // 3. Check input status
                    // -------------------------------------------------

                    if (
                        inputGuarantee.status !=
                        GuaranteeState.Status.ACTIVE
                    ) {
                        throw FlowException(
                            "Only an ACTIVE guarantee can be released."
                        )
                    }

                    // -------------------------------------------------
                    // 4. Check output status
                    // -------------------------------------------------

                    if (
                        outputGuarantee.status !=
                        GuaranteeState.Status.RELEASED
                    ) {
                        throw FlowException(
                            "The output guarantee must have RELEASED status."
                        )
                    }

                    // -------------------------------------------------
                    // 5. Check linearId
                    // -------------------------------------------------

                    if (
                        inputGuarantee.linearId !=
                        outputGuarantee.linearId
                    ) {
                        throw FlowException(
                            "The guarantee linearId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 6. Check obligationId
                    // -------------------------------------------------

                    if (
                        inputGuarantee.obligationId !=
                        outputGuarantee.obligationId
                    ) {
                        throw FlowException(
                            "The obligationId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 7. Check owner
                    // -------------------------------------------------

                    if (
                        inputGuarantee.owner !=
                        outputGuarantee.owner
                    ) {
                        throw FlowException(
                            "The guarantee owner cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 8. Check beneficiary
                    // -------------------------------------------------

                    if (
                        inputGuarantee.beneficiary !=
                        outputGuarantee.beneficiary
                    ) {
                        throw FlowException(
                            "The guarantee beneficiary cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 9. Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is GuaranteeContract.Commands.Release
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a GuaranteeContract.Commands.Release command."
                            )

                    // -------------------------------------------------
                    // 10. Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            inputGuarantee.owner.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The guarantee owner must sign the release."
                        )
                    }

                    if (
                        !command.signers.contains(
                            inputGuarantee.beneficiary.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The guarantee beneficiary must sign the release."
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
