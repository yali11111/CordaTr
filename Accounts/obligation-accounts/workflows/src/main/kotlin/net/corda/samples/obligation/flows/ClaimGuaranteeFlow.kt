package net.corda.samples.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.samples.obligation.contract.GuaranteeContract
import net.corda.samples.obligation.states.GuaranteeState
import java.security.PublicKey

/**
 * Claims an active guarantee.
 *
 * The guarantee changes from:
 *
 *      ACTIVE
 *         |
 *         | Claim
 *         v
 *      CLAIMED
 *
 * The creditor/beneficiary initiates the claim.
 */
@InitiatingFlow
@StartableByRPC
class ClaimGuaranteeFlow(
    private val guaranteeLinearId: GuaranteeState
        .() -> net.corda.core.contracts.UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {
        object FINDING_GUARANTEE : ProgressTracker.Step(
            "Finding the guarantee."
        )

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the guarantee claim transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the guarantee claim transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the guarantee claim transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the guarantor's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the guarantee claim transaction."
        )

        fun tracker() = ProgressTracker(
            FINDING_GUARANTEE,
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

        /*
         * -------------------------------------------------------------
         * 1. Find the guarantee
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = FINDING_GUARANTEE

        val linearId = guaranteeLinearId(
            GuaranteeState(
                guarantor = ourIdentity,
                debtor = ourIdentity,
                creditor = ourIdentity,
                guaranteedAmount = net.corda.core.contracts.Amount(
                    1,
                    java.util.Currency.getInstance("EUR")
                ),
                obligationId = net.corda.core.contracts.UniqueIdentifier()
            )
        )

        val guaranteeStateAndRef =
            serviceHub.vaultService
                .queryBy<GuaranteeState>()
                .states
                .singleOrNull {
                    it.state.data.linearId == linearId
                }
                ?: throw FlowException(
                    "Guarantee with linearId $linearId was not found."
                )

        val input = guaranteeStateAndRef.state.data

        /*
         * -------------------------------------------------------------
         * 2. Validate the guarantee
         * -------------------------------------------------------------
         */

        if (input.status != GuaranteeState.Status.ACTIVE) {
            throw FlowException(
                "Only an ACTIVE guarantee can be claimed. " +
                        "Current status: ${input.status}"
            )
        }

        if (ourIdentity != input.creditor) {
            throw FlowException(
                "Only the creditor can claim this guarantee."
            )
        }

        /*
         * -------------------------------------------------------------
         * 3. Determine the guarantor
         * -------------------------------------------------------------
         */

        val guarantor = input.guarantor

        /*
         * -------------------------------------------------------------
         * 4. Build the output state
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = BUILDING_TRANSACTION

        val output = input.copy(
            status = GuaranteeState.Status.CLAIMED
        )

        val notary = guaranteeStateAndRef.state.notary

        val command = net.corda.core.contracts.Command(
            GuaranteeContract.Commands.Claim(),
            listOf(
                input.creditor.owningKey,
                input.guarantor.owningKey
            )
        )

        val txBuilder = TransactionBuilder(notary)
            .addInputState(guaranteeStateAndRef)
            .addOutputState(
                output,
                GuaranteeContract.GUARANTEE_CONTRACT_ID
            )
            .addCommand(command)

        /*
         * -------------------------------------------------------------
         * 5. Verify the transaction
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        /*
         * -------------------------------------------------------------
         * 6. Sign with creditor's key
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        /*
         * -------------------------------------------------------------
         * 7. Collect guarantor signature
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val guarantorSession = initiateFlow(guarantor)

        val fullySignedTx = subFlow(
            CollectSignaturesFlow(
                signedTx,
                setOf(guarantorSession)
            )
        )

        /*
         * -------------------------------------------------------------
         * 8. Finalise
         * -------------------------------------------------------------
         */

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTx,
                setOf(guarantorSession)
            )
        )
    }
}


/**
 * Responder for ClaimGuaranteeFlow.
 *
 * The guarantor verifies the transaction before signing it.
 */
@InitiatedBy(ClaimGuaranteeFlow::class)
class ClaimGuaranteeFlowResponder(
    private val counterpartySession: FlowSession
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

        val signTransactionFlow = object :
            SignTransactionFlow(counterpartySession) {

            override fun checkTransaction(
                stx: SignedTransaction
            ) {
                val tx = stx.tx

                val command = tx.commands
                    .filter {
                        it.value is GuaranteeContract.Commands.Claim
                    }
                    .singleOrNull()
                    ?: throw FlowException(
                        "Expected a GuaranteeContract.Commands.Claim command."
                    )

                val output = tx.outputs
                    .map { it.data }
                    .filterIsInstance<GuaranteeState>()
                    .singleOrNull()
                    ?: throw FlowException(
                        "Expected one GuaranteeState output."
                    )

                if (output.status != GuaranteeState.Status.CLAIMED) {
                    throw FlowException(
                        "The guarantee must be in CLAIMED status."
                    )
                }

                if (ourIdentity != output.guarantor) {
                    throw FlowException(
                        "The responder is not the guarantor of this guarantee."
                    )
                }

                if (
                    !command.signers.contains(
                        output.creditor.owningKey
                    )
                ) {
                    throw FlowException(
                        "The creditor must sign the guarantee claim."
                    )
                }

                if (
                    !command.signers.contains(
                        output.guarantor.owningKey
                    )
                ) {
                    throw FlowException(
                        "The guarantor must sign the guarantee claim."
                    )
                }
            }
        }

        subFlow(
            object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(
                    stx: SignedTransaction
                ) {
                    val output = stx.tx.outputs
                        .map { it.data }
                        .filterIsInstance<GuaranteeState>()
                        .singleOrNull()
                        ?: throw FlowException(
                            "Expected one GuaranteeState output."
                        )

                    if (
                        output.status !=
                        GuaranteeState.Status.CLAIMED
                    ) {
                        throw FlowException(
                            "Invalid guarantee claim."
                        )
                    }
                }
            }
        )

        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}