package com.reveila.cluster

import com.reveila.service.RemoteService
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
 * Primary production [ClusterTransport] backed by [RemoteService] (`remote.reveila`).
 * Ensures all dynamic Reveila nodes use [RemoteService] to contact the Lead Node and peer nodes,
 * benefiting from unified auth headers, timeout configuration, and [com.reveila.system.PerformanceTracker]
 * latency/penalty tracking.
 */
class RemoteServiceClusterTransport(
    val remoteService: RemoteService,
    private val clusterSecret: String? = null
) : ClusterTransport {

    override fun invokeClusterMethod(
        targetBaseUrl: String,
        methodName: String,
        args: Array<Any?>
    ): Any? {
        val headers = if (!clusterSecret.isNullOrBlank()) {
            mapOf(Constants.CLUSTER_TOKEN_HEADER to clusterSecret)
        } else {
            emptyMap()
        }
        return remoteService.invokeOnNode(
            baseUrl = targetBaseUrl,
            componentName = Constants.CLUSTER_SERVICE,
            methodName = methodName,
            args = args,
            extraHeaders = headers
        )
    }
}

/**
 * Direct [ReveilaClient]-backed [ClusterTransport] retained for backwards compatibility.
 * Production cluster nodes default to [RemoteServiceClusterTransport].
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
