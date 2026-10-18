
package net.corda.samples.insurance.api.service

import net.corda.core.messaging.CordaRPCOps
import org.springframework.stereotype.Service

@Service
class CordaService(
    private val cordaRpcOps: CordaRPCOps
) {
    fun nodeInfo() = cordaRpcOps.nodeInfo()

    fun networkNodes() = cordaRpcOps.networkMapSnapshot()
}
