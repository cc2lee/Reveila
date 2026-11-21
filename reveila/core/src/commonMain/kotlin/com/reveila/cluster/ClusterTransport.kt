package com.reveila.cluster

import com.reveila.system.Constants
import com.reveila.system.ReveilaClient

/**
 * Hexagonal transport port for node-to-node ClusterService RPCs.
 * Allows production clusters to communicate over Reveila's unified HTTP invoke endpoint
 * while enabling deterministic in-memory multi-node simulation in unit tests.
 */
interface ClusterTransport {
    @Throws(Exception::class)
    fun invokeClusterMethod(
        targetBaseUrl: String,
        methodName: String,
        args: Array<Any?> = emptyArray()
    ): Any?
}

/**
 * Default production [ClusterTransport] backed by [ReveilaClient].
 * Invokes `POST {targetBaseUrl}/api/components/ClusterService/invoke`.
 */
class ReveilaClientClusterTransport(
    private val version: String = Constants.DEFAULT_SYSTEM_VERSION,
    private val clusterSecret: String? = null,
    private val timeoutSeconds: Long = 5L
) : ClusterTransport {

    override fun invokeClusterMethod(
        targetBaseUrl: String,
        methodName: String,
        args: Array<Any?>
    ): Any? {
        val headers = LinkedHashMap<String, String>()
        if (!clusterSecret.isNullOrBlank()) {
            headers[Constants.CLUSTER_TOKEN_HEADER] = clusterSecret
        }
        val client = ReveilaClient(
            baseUrl = targetBaseUrl,
            version = version,
            defaultHeaders = headers,
            timeoutSeconds = timeoutSeconds
        )
        return client.invoke(
            componentName = Constants.CLUSTER_SERVICE,
            methodName = methodName,
            args = args,
            targetBaseUrl = targetBaseUrl
        )
    }
}
