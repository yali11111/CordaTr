package net.corda.samples.insurance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder
import net.corda.samples.insurance.contracts.InsurancePolicyContract
import net.corda.samples.insurance.contracts.InsurancePolicyContract.Commands
import net.corda.samples.insurance.states.InsurancePolicyState

@StartableByRPC
class CreateInsurancePolicyFlow(
    private val networkId: String,
    private val careProvider: String,
    private val insuree: String
) : FlowLogic<InsurancePolicyState>() {

    @Suspendable
    override fun call(): InsurancePolicyState {
        require(networkId.isNotBlank()) {
            "networkId est obligatoire"
        }
        require(careProvider.isNotBlank()) {
            "careProvider est obligatoire"
        }
        require(insuree.isNotBlank()) {
            "insuree est obligatoire"
        }

        val insurer = ourIdentity

        val policy = InsurancePolicyState(
            insurer = insurer,
            insuree = insuree,
            careProvider = careProvider,
            networkId = networkId
        )

        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
            ?: throw IllegalStateException("Aucun notary disponible")

        val transaction = TransactionBuilder(notary)
            .addOutputState(policy, InsurancePolicyContract.ID)
            .addCommand(
                Command(
                    Commands.Create(),
                    policy.participants.map { it.owningKey }
                )
            )

        transaction.verify(serviceHub)

        val signedTransaction =
            serviceHub.signInitialTransaction(transaction)

        subFlow(FinalityFlow(signedTransaction, emptyList()))

        return policy
    }
}