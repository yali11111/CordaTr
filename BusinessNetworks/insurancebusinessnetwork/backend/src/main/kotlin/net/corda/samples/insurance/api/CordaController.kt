
package net.corda.samples.insurance.api

import net.corda.samples.insurance.api.service.CordaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/corda")
class CordaController(
    private val cordaService: CordaService
) {
    @GetMapping("/status")
    fun status() = mapOf(
        "status" to "connected",
        "legalName" to cordaService.nodeInfo().legalIdentities.first().name.toString()
    )

    @GetMapping("/nodes")
    fun nodes() = cordaService.networkNodes().map {
        it.legalIdentities.first().name.toString()
    }
}
