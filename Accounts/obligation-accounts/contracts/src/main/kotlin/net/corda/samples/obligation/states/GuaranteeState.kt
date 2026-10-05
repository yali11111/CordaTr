package net.corda.samples.obligation.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.samples.obligation.contract.GuaranteeContract
import java.util.Currency

data class GuaranteeState(
    val guarantor: Party,
    val debtor: Party,
    val creditor: Party,
    val guaranteedAmount: Amount<Currency>,
    val obligationId: UniqueIdentifier,
    val status: Status = Status.CREATED,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : ContractState {

    enum class Status {
        CREATED,
        ACTIVE,
        CLAIMED,
        RELEASED
    }

    override val participants: List<AbstractParty>
        get() = listOf(
            guarantor,
            debtor,
            creditor
        )
}
