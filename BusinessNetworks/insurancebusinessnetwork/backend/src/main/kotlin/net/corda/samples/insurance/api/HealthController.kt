package net.corda.samples.insurance.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health() = mapOf(
        "status" to "UP",
        "application" to "Insurance Business Network API",
        "cordaVersion" to "4.12.5"
    )
}
