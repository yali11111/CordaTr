package net.corda.samples.insurance.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import net.corda.samples.insurance.states.InsurancePolicyState

class InsurancePolicyContract : Contract {

    companion object {
        const val ID = "net.corda.samples.insurance.contracts.InsurancePolicyContract"
    }

    interface Commands : CommandData {
        class Create : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.single()

        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    "Aucune entrée ne doit exister" using
                        (tx.inputs.isEmpty())

                    "Une seule police doit être créée" using
                        (tx.outputs.size == 1)

                    val policy = tx.outputsOfType<InsurancePolicyState>().single()

                    "L'assuré est obligatoire" using
                        policy.insuree.isNotBlank()

                    "Le prestataire est obligatoire" using
                        policy.careProvider.isNotBlank()

                    "Le réseau est obligatoire" using
                        policy.networkId.isNotBlank()

                    "Le statut initial doit être ACTIVE" using
                        (policy.status == "ACTIVE")
                }

                requireThat {
                    "L'assureur doit signer la transaction" using
                        (command.signers.contains(
                            tx.outputsOfType<InsurancePolicyState>()
                                .single()
                                .insurer.owningKey
                        ))
                }
            }
        }
    }
}