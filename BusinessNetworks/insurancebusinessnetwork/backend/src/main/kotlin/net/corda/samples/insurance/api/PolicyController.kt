package net.corda.samples.insurance.api

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import net.corda.samples.insurance.flows.CreateInsurancePolicyFlow
import net.corda.samples.insurance.states.InsurancePolicyState
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.Sort

data class CreatePolicyRequest(
    val networkId: String,
    val careProvider: String,
    val insuree: String
)

data class PolicyResponse(
    val id: String,
    val insurer: String,
    val insuree: String,
    val careProvider: String,
    val networkId: String,
    val status: String
)

@RestController
@RequestMapping("/api/policies")
class PolicyController(
    private val cordaRpcOps: CordaRPCOps
) {

    
    @GetMapping
fun getPolicies(): List<PolicyResponse> {
    val criteria = QueryCriteria.VaultQueryCriteria(
        status = Vault.StateStatus.UNCONSUMED
    )

    return cordaRpcOps
        .vaultQueryBy(
            criteria,
            PageSpecification(1, 200),
            Sort(emptySet()),
            InsurancePolicyState::class.java
        )
        .states
        .map { stateAndRef -> stateAndRef.state.data.toResponse() }
}

    @GetMapping("/{id}")
    fun getPolicy(@PathVariable id: String): PolicyResponse {
        return getPolicies().firstOrNull { it.id == id }
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Police introuvable : $id"
            )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPolicy(
        @RequestBody request: CreatePolicyRequest
    ): PolicyResponse {
        if (
            request.networkId.isBlank() ||
            request.careProvider.isBlank() ||
            request.insuree.isBlank()
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "networkId, careProvider et insuree sont obligatoires"
            )
        }

        val policy = cordaRpcOps
            .startFlowDynamic(
                CreateInsurancePolicyFlow::class.java,
                request.networkId,
                request.careProvider,
                request.insuree
            )
            .returnValue
            .getOrThrow()

        return policy.toResponse()
    }

    private fun InsurancePolicyState.toResponse() = PolicyResponse(
        id = linearId.id.toString(),
        insurer = insurer.name.toString(),
        insuree = insuree,
        careProvider = careProvider,
        networkId = networkId,
        status = status
    )
}