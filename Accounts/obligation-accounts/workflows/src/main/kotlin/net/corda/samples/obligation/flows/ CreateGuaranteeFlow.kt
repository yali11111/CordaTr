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
import net.corda.samples.obligation.contract.GuaranteeContract
import net.corda.samples.obligation.states.GuaranteeState
import java.util.Currency

/**
 * Creates a new guarantee for an existing obligation.
 *
 * The flow is initiated by the debtor.
 *
 * Example:
 *
 * Alice = debtor
 * Bob   = creditor
 * Charlie = guarantor
 *
 * Alice -> Bob : IOU
 * Charlie -> guarantees Alice's obligation
 *
 * Result:
 *
 * GuaranteeState(
 *     guarantor = Charlie,
 *     debtor = Alice,
 *     creditor = Bob,
 *     status = CREATED
 * )
 */
@InitiatingFlow
@StartableByRPC
class CreateGuaranteeFlow(
    private val debtor: Party,
    private val creditor: Party,
    private val guarantor: Party,
    private val guaranteedAmount: Amount<Currency>,
    private val obligationId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    companion object {

        object BUILDING_TRANSACTION : ProgressTracker.Step(
            "Building the guarantee transaction."
        )

        object VERIFYING_TRANSACTION : ProgressTracker.Step(
            "Verifying the guarantee transaction."
        )

        object SIGNING_TRANSACTION : ProgressTracker.Step(
            "Signing the guarantee transaction."
        )

        object COLLECTING_SIGNATURES : ProgressTracker.Step(
            "Collecting the guarantor's signature."
        )

        object FINALISING : ProgressTracker.Step(
            "Finalising the guarantee transaction."
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

        /*
         * ---------------------------------------------------------
         * 1. Validate the flow parameters
         * ---------------------------------------------------------
         */

        if (ourIdentity != debtor) {
            throw FlowException(
                "Only the debtor can initiate the creation of this guarantee."
            )
        }

        if (debtor == creditor) {
            throw FlowException(
                "Debtor and creditor cannot be the same party."
            )
        }

        if (debtor == guarantor) {
            throw FlowException(
                "Debtor and guarantor cannot be the same party."
            )
        }

        if (creditor == guarantor) {
            throw FlowException(
                "Creditor and guarantor cannot be the same party."
            )
        }

        if (guaranteedAmount.quantity <= 0) {
            throw FlowException(
                "The guaranteed amount must be positive."
            )
        }

        /*
         * ---------------------------------------------------------
         * 2. Build the GuaranteeState
         * ---------------------------------------------------------
         */

        progressTracker.currentStep = BUILDING_TRANSACTION

        val guaranteeState = GuaranteeState(
            guarantor = guarantor,
            debtor = debtor,
            creditor = creditor,
            guaranteedAmount = guaranteedAmount,
            obligationId = obligationId,
            status = GuaranteeState.Status.CREATED
        )

        /*
         * ---------------------------------------------------------
         * 3. Determine the notary
         * ---------------------------------------------------------
         *
         * A transaction creating a new state needs a notary.
         * The notary is selected from the network map.
         *
         * In a production application, you may want to make
         * the notary an explicit flow parameter or obtain it
         * from an existing IOU state.
         */

        val notary = serviceHub.networkMapCache.notaryIdentities
            .firstOrNull()
            ?: throw FlowException(
                "No notary is available on the network."
            )

        /*
         * ---------------------------------------------------------
         * 4. Create the command
         * ---------------------------------------------------------
         *
         * According to GuaranteeContract:
         *
         *     Create
         *
         * must be signed by:
         *
         *     debtor + guarantor
         */

        val command = Command(
            GuaranteeContract.Commands.Create(),
            listOf(
                debtor.owningKey,
                guarantor.owningKey
            )
        )

        /*
         * ---------------------------------------------------------
         * 5. Build transaction
         * ---------------------------------------------------------
         */

        val txBuilder = TransactionBuilder(notary)
            .addOutputState(
                guaranteeState,
                GuaranteeContract.GUARANTEE_CONTRACT_ID
            )
            .addCommand(command)

        /*
         * ---------------------------------------------------------
         * 6. Verify transaction
         * ---------------------------------------------------------
         */

        progressTracker.currentStep = VERIFYING_TRANSACTION

        txBuilder.verify(serviceHub)

        /*
         * ---------------------------------------------------------
         * 7. Sign transaction as debtor
         * ---------------------------------------------------------
         */

        progressTracker.currentStep = SIGNING_TRANSACTION

        val signedTransaction =
            serviceHub.signInitialTransaction(txBuilder)

        /*
         * ---------------------------------------------------------
         * 8. Collect guarantor signature
         * ---------------------------------------------------------
         */

        progressTracker.currentStep = COLLECTING_SIGNATURES

        val guarantorSession =
            initiateFlow(guarantor)

        val fullySignedTransaction =
            subFlow(
                CollectSignaturesFlow(
                    signedTransaction,
                    setOf(guarantorSession)
                )
            )

        /*
         * ---------------------------------------------------------
         * 9. Finalise transaction
         * ---------------------------------------------------------
         */

        progressTracker.currentStep = FINALISING

        return subFlow(
            FinalityFlow(
                fullySignedTransaction,
                setOf(guarantorSession)
            )
        )
    }
}


/**
 * Responder for CreateGuaranteeFlow.
 *
 * The responder is normally the guarantor.
 */
@InitiatedBy(CreateGuaranteeFlow::class)
class CreateGuaranteeFlowResponder(
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

                    val output =
                        stx.tx.outputs
                            .map { it.data }
                            .filterIsInstance<GuaranteeState>()
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected exactly one GuaranteeState output."
                            )

                    /*
                     * Verify the state.
                     */

                    if (
                        output.status !=
                        GuaranteeState.Status.CREATED
                    ) {
                        throw FlowException(
                            "A newly created guarantee must have CREATED status."
                        )
                    }

                    if (
                        output.guarantor != ourIdentity
                    ) {
                        throw FlowException(
                            "The responder is not the guarantor of this guarantee."
                        )
                    }

                    if (
                        output.guaranteedAmount.quantity <= 0
                    ) {
                        throw FlowException(
                            "The guaranteed amount must be positive."
                        )
                    }

                    if (
                        output.debtor == output.creditor
                    ) {
                        throw FlowException(
                            "Debtor and creditor cannot be the same party."
                        )
                    }

                    if (
                        output.debtor == output.guarantor
                    ) {
                        throw FlowException(
                            "Debtor and guarantor cannot be the same party."
                        )
                    }

                    if (
                        output.creditor == output.guarantor
                    ) {
                        throw FlowException(
                            "Creditor and guarantor cannot be the same party."
                        )
                    }

                    /*
                     * Verify the command.
                     */

                    val command =
                        stx.tx.commands
                            .filter {
                                it.value is GuaranteeContract.Commands.Create
                            }
                            .singleOrNull()
                            ?: throw FlowException(
                                "Expected a GuaranteeContract.Commands.Create command."
                            )

                    if (
                        !command.signers.contains(
                            output.debtor.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The debtor must sign the guarantee creation."
                        )
                    }

                    if (
                        !command.signers.contains(
                            output.guarantor.owningKey
                        )
                    ) {
                        throw FlowException(
                            "The guarantor must sign the guarantee creation."
                        )
                    }
                }
            }

        /*
         * Sign the transaction.
         */
        subFlow(signTransactionFlow)

        /*
         * Receive the finalised transaction.
         */
        subFlow(
            ReceiveFinalityFlow(
                counterpartySession
            )
        )
    }
}