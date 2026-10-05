package net.corda.samples.obligation.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * Represents an account participating in the obligation system.
 *
 * This state keeps the business identity of an obligation account.
 * The actual Corda account/key management remains handled by
 * the Corda Accounts framework.
 */
data class ObligationAccountState(
    val accountName: String,
    val host: Party,
    val accountParty: AbstractParty,
    override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

    override val participants: List<AbstractParty>
        get() = listOf(accountParty, host)
}
