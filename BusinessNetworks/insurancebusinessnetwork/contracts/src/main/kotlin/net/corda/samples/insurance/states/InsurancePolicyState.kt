package net.corda.samples.insurance.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.insurance.contracts.InsurancePolicyContract

@BelongsToContract(InsurancePolicyContract::class)
data class InsurancePolicyState(
    val insurer: Party,
    val insuree: String,
    val careProvider: String,
    val networkId: String,
    val status: String = "ACTIVE",
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

    override val participants: List<AbstractParty>
        get() = listOf(insurer)
}