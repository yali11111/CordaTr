package net.corda.samples.insurance.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InsuranceApiApplication

fun main(args: Array<String>) {
    runApplication<InsuranceApiApplication>(*args)
}
