
package net.corda.samples.insurance.api.config

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CordaRpcConfig(
    @Value("\${corda.rpc.host:localhost}")
    private val host: String,

    @Value("\${corda.rpc.port:10006}")
    private val port: Int,

    @Value("\${corda.rpc.username}")
    private val username: String,

    @Value("\${corda.rpc.password}")
    private val password: String
) {

    @Bean(destroyMethod = "close")
    fun cordaRpcConnection(): CordaRPCConnection {
        val address = NetworkHostAndPort(host, port)
        val client = CordaRPCClient(address)
        return client.start(username, password)
    }

    @Bean
    fun cordaRpcOps(connection: CordaRPCConnection): CordaRPCOps =
        connection.proxy
}
