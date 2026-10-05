package net.corda.samples.obligation.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.obligation.contract.DisputeContract
import java.util.Currency

data class DisputeState(
    val claimant: Party,
    val respondent: Party,
    val obligationId: UniqueIdentifier,
    val disputedAmount: Amount<Currency>,
    val reason: String,
    val status: Status = Status.OPEN,
    val resolution: String? = null,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

    enum class Status {
        OPEN,
        RESPONDED,
        RESOLVED
    }

    override val participants: List<AbstractParty>
        get() = listOf(
            claimant,
            respondent
        )
}
