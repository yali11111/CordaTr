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
import net.corda.samples.obligation.contract.CollateralContract
import net.corda.samples.obligation.states.CollateralState
import java.util.Currency

/**
 * Creates a new collateral for an existing obligation.
 *
 * The owner of the collateral initiates the flow.
 *
 * Example:
 *
 * Alice = owner / debtor
 * Bob   = beneficiary / creditor
 *
 * Alice locks 20,000 EUR as collateral for an IOU.
 *
 * Result:
 *
 * CollateralState(
 *     owner = Alice,
 *     beneficiary = Bob,
 *     collateralAmount = 20,000 EUR,
 *     obligationId = ...
 *     status = LOCKED
 * )
 */
@InitiatingFlow
@StartableByRPC
class CreateCollateralFlow(
    private val owner: Party,
    private val beneficiary: Party,
    private val collateralAmount: Amount<Currency>,
    private val obligationId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the collateral creation transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the collateral creation transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the collateral creation transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the beneficiary's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the collateral creation transaction."
        )

        fun tracker() = ProgressTracker(
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
        // 1. Validate parameters
        // ---------------------------------------------------------

        if (ourIdentity != owner) {
            throw FlowException(
                "Only the collateral owner can initiate this flow."
            )
        }

        if (owner == beneficiary) {
            throw FlowException(
                "The collateral owner and beneficiary cannot be the same party."
            )
        }

        if (collateralAmount.quantity <= 0) {
            throw FlowException(
                "The collateral amount must be positive."
            )
        }

        // ---------------------------------------------------------
        // 2. Build the CollateralState
        // ---------------------------------------------------------

        progressTracker.currentStep = BUILDING_TRANSACTION

        val collateralState = CollateralState(
            owner = owner,
            beneficiary = beneficiary,
            collateralAmount = collateralAmount,
            obligationId = obligationId,
            status = CollateralState.Status.LOCKED
        )

        // ---------------------------------------------------------
        // 3. Determine the notary
        // ---------------------------------------------------------

        val notary =
            serviceHub.networkMapCache.notaryIdentities
                .firstOrNull()
                ?: throw FlowException(
                    "No notary is available on the network."
                )

        // ---------------------------------------------------------
        // 4. Create the command
        // ---------------------------------------------------------

        val command = Command(
            CollateralContract.Commands.Create(),
            listOf(
                owner.owningKey,
                beneficiary.owningKey
            )
        )

        // ---------------------------------------------------------
        // 5. Build transaction
        // ---------------------------------------------------------

        val txBuilder = TransactionBuilder(notary)
            .addOutputState(
                collateralState,
                CollateralContract.COLLATERAL_CONTRACT_ID
            )
            .addCommand(command)

        // ---------------------------------------------------------
        // 6. Verify transaction
        // ---------------------------------------------------------

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        // ---------------------------------------------------------
        // 7. Sign as owner
        // ---------------------------------------------------------

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        // ---------------------------------------------------------
        // 8. Collect beneficiary signature
        // ---------------------------------------------------------

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val beneficiarySession =
            initiateFlow(beneficiary)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(beneficiarySession)
                )
            )

        // ---------------------------------------------------------
        // 9. Finalise
        // ---------------------------------------------------------

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(beneficiarySession)
            )
        )
    }
}


/**
 * Responder for CreateCollateralFlow.
 *
 * The beneficiary verifies and signs the collateral creation.
 */
@InitiatedBy(CreateCollateralFlow::class)
class CreateCollateralFlowResponder(
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

                    val output =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<CollateralState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one CollateralState output."
                            )

                    // -------------------------------------------------
                    // Check status
                    // -------------------------------------------------

                    if (
                        output.status !=
                        CollateralState.Status.LOCKED
                    ) {
                        throw FlowException(
                            "A newly created collateral must have LOCKED status."
                        )
                    }

                    // -------------------------------------------------
                    // Check beneficiary
                    // -------------------------------------------------

                    if (
                        output.beneficiary != ourIdentity
                    ) {
                        throw FlowException(
                            "The responder is not the beneficiary of the collateral."
                        )
                    }

                    // -------------------------------------------------
                    // Check amount
                    // -------------------------------------------------

                    if (
                        output.collateralAmount.quantity <= 0
                    ) {
                        throw FlowException(
                            "The collateral amount must be positive."
                        )
                    }

                    // -------------------------------------------------
                    // Check owner / beneficiary
                    // -------------------------------------------------

                    if (
                        output.owner == output.beneficiary
                    ) {
                        throw FlowException(
                            "The collateral owner and beneficiary cannot be the same party."
                        )
                    }

                    // -------------------------------------------------
                    // Check command
                    // -------------------------------------------------

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is CollateralContract.Commands.Create
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a CollateralContract.Commands.Create command."
                            )

                    // -------------------------------------------------
                    // Check required signatures
                    // -------------------------------------------------

                    if (
                        !command.signers.contains(
                            output.owner.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The collateral owner must sign the creation."
                        )
                    }

                    if (
                        !command.signers.contains(
                            output.beneficiary.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The collateral beneficiary must sign the creation."
                        )
                    }
                }
            }

        // Sign the transaction.
        subFlow(signTransactionFlow)

        // Receive the finalised transaction.
        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}
