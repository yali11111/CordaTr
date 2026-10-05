package net.corda.samples.obligation.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.util.Currency

/**
 * State representing collateral pledged to secure an obligation.
 *
 * The collateral is linked to an existing IOU through [obligationId].
 *
 * Example:
 *
 * Alice owes Bob 100,000 EUR.
 * Alice locks 20,000 EUR as collateral.
 *
 * The CollateralState represents the 20,000 EUR that is locked
 * in relation to the IOU.
 */
data class CollateralState(

    /**
     * Party that owns/provides the collateral.
     */
    val owner: Party,

    /**
     * Party that benefits from the collateral.
     * Usually this is the lender/creditor of the IOU.
     */
    val beneficiary: Party,

    /**
     * Amount of collateral.
     */
    val collateralAmount: Amount<Currency>,

    /**
     * Identifier of the IOU secured by this collateral.
     */
    val obligationId: UniqueIdentifier,

    /**
     * Current lifecycle status of the collateral.
     */
    val status: Status = Status.LOCKED,

    /**
     * Unique identifier of this collateral state.
     */
    override val linearId: UniqueIdentifier = UniqueIdentifier()

) : LinearState {

    /**
     * Lifecycle of the collateral.
     */
    enum class Status {

        /**
         * Collateral has been created and locked.
         */
        LOCKED,

        /**
         * Collateral has been released back to its owner.
         */
        RELEASED,

        /**
         * Collateral has been claimed by the beneficiary.
         */
        CLAIMED
    }

    /**
     * Parties involved in this state.
     *
     * Both the owner and beneficiary need to be able
     * to see and sign transactions involving the collateral.
     */
    override val participants: List<AbstractParty>
        get() = listOf(
            owner,
            beneficiary
        )
}
