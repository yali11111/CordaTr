package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.CollateralContract
import net.corda.samples.obligation.states.CollateralState

/**
 * Releases an existing collateral.
 *
 * The collateral must currently be LOCKED.
 *
 * The owner or the beneficiary can initiate the flow, but both
 * parties must sign the transaction.
 *
 * Transaction:
 *
 *     CollateralState(LOCKED)
 *                  |
 *                  | Release
 *                  v
 *     CollateralState(RELEASED)
 */
@InitiatingFlow
@StartableByRPC
class ReleaseCollateralFlow(
    private val collateralId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {

        object FINDING_COLLATERAL : ProgressTracker.Step(
            "Finding the collateral."
        )

        object VALIDATING_COLLATERAL : ProgressTracker.Step(
            "Validating the collateral release."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the collateral release transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the collateral release transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the collateral release transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the other party's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the collateral release transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_COLLATERAL,
            VALIDATING_COLLATERAL,
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
        // 1. Find collateral
        // ---------------------------------------------------------

        progressTracker.currentStep = FINDING_COLLATERAL

        val collateralStateAndRef =
            serviceHub.vaultService
                .queryBy<CollateralState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == collateralId
                }
                ?: throw FlowException(
                    "Collateral $collateralId was not found."
                )

        val inputCollateral =
            collateralStateAndRef.state.data

        // ---------------------------------------------------------
        // 2. Validate collateral
        // ---------------------------------------------------------

        progressTracker.currentStep = VALIDATING_COLLATERAL

        if (
            ourIdentity != inputCollateral.owner &&
            ourIdentity != inputCollateral.beneficiary
        ) {
            throw FlowException(
                "Only the collateral owner or beneficiary can release the collateral."
            )
        }

        if (
            inputCollateral.status !=
            CollateralState.Status.LOCKED
        ) {
            throw FlowException(
                "Only LOCKED collateral can be released."
            )
        }

        if (
            inputCollateral.owner ==
            inputCollateral.beneficiary
        ) {
            throw FlowException(
                "The collateral owner and beneficiary cannot be the same party."
            )
        }

        // ---------------------------------------------------------
        // 3. Determine counterparty
        // ---------------------------------------------------------

        val counterparty =
            if (ourIdentity == inputCollateral.owner) {
                inputCollateral.beneficiary
            } else {
                inputCollateral.owner
            }

        // ---------------------------------------------------------
        // 4. Create output state
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val outputCollateral =
            inputCollateral.copy(
                status = CollateralState.Status.RELEASED
            )

        // ---------------------------------------------------------
        // 5. Create command
        // ---------------------------------------------------------

        val command =
            Command(
                CollateralContract.Commands.Release(),
                listOf(
                    inputCollateral.owner.owningKey,
                    inputCollateral.beneficiary.owningKey
                )
            )

        // ---------------------------------------------------------
        // 6. Build transaction
        // ---------------------------------------------------------

        val txBuilder =
            TransactionBuilder(
                collateralStateAndRef.state.notary
            )
                .addInputState(collateralStateAndRef)
                .addOutputState(
                    outputCollateral,
                    CollateralContract.COLLATERAL_CONTRACT_ID
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
 * Responder for ReleaseCollateralFlow.
 *
 * The other participant verifies the release and signs the transaction.
 */
@InitiatedBy(ReleaseCollateralFlow::class)
class ReleaseCollateralFlowResponder(
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

                    val inputCollateral =
                        stx.tx.inputs
                            .map {
                                serviceHub
                                    .toStateAndRef<CollateralState>(it)
                            }
                            .singleOrNull()
                            ?.state
                            ?.data
                            ?: throw FlowException(
                                "Expected exactly one CollateralState input."
                            )

                    // -------------------------------------------------
                    // 2. Check output
                    // -------------------------------------------------

                    val outputCollateral =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<CollateralState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one CollateralState output."
                            )

                    // -------------------------------------------------
                    // 3. Check current status
                    // -------------------------------------------------

                    if (
                        inputCollateral.status !=
                        CollateralState.Status.LOCKED
                    ) {
                        throw FlowException(
                            "Only LOCKED collateral can be released."
                        )
                    }

                    // -------------------------------------------------
                    // 4. Check output status
                    // -------------------------------------------------

                    if (
                        outputCollateral.status !=
                        CollateralState.Status.RELEASED
                    ) {
                        throw FlowException(
                            "Released collateral must have RELEASED status."
                        )
                    }

                    // -------------------------------------------------
                    // 5. Check linearId
                    // -------------------------------------------------

                    if (
                        inputCollateral.linearId !=
                        outputCollateral.linearId
                    ) {
                        throw FlowException(
                            "The collateral linearId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 6. Check obligation
                    // -------------------------------------------------

                    if (
                        inputCollateral.obligationId !=
                        outputCollateral.obligationId
                    ) {
                        throw FlowException(
                            "The obligationId cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 7. Check owner
                    // -------------------------------------------------

                    if (
                        inputCollateral.owner !=
                        outputCollateral.owner
                    ) {
                        throw FlowException(
                            "The collateral owner cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 8. Check beneficiary
                    // -------------------------------------------------

                    if (
                        inputCollateral.beneficiary !=
                        outputCollateral.beneficiary
                    ) {
                        throw FlowException(
                            "The collateral beneficiary cannot change."
                        )
                    }

                    // -------------------------------------------------
                    // 9. Check amount
                    // -------------------------------------------------

                    if (
                        inputCollateral.collateralAmount !=
                        outputCollateral.collateralAmount
                    ) {
                        throw FlowException(
                            "The collateral amount cannot change during release."
                        )
                    }

                    // -------------------------------------------------
                    // 10. Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is CollateralContract.Commands.Release
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a CollateralContract.Commands.Release command."
                            )

                    // -------------------------------------------------
                    // 11. Check signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            inputCollateral.owner.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The collateral owner must sign the release."
                        )
                    }

                    if (
                        !command.signers.contains(
                            inputCollateral.beneficiary.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The collateral beneficiary must sign the release."
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
