package com.reveila.service

import com.reveila.error.ConfigurationException
import com.reveila.error.SystemException
import com.reveila.system.Constants
import com.reveila.system.PerformanceTracker
import com.reveila.system.ReveilaClient
import com.reveila.system.SystemComponent
import com.reveila.util.json.JsonException
import kotlin.time.TimeSource

/**
 * Invokes services, components, and plugins hosted on remote Reveila instances.
 *
 * Supports both:
 * 1. Static component configuration (`RemoteURLs` argument: `["http://10.0.2.2:8080, 1"]`).
 * 2. Dynamic cluster membership (`cluster.leader.url` / `cluster.seeds` auto-seeding,
 *    runtime [addRemoteNode] / [removeRemoteNode], cluster auth headers, and direct
 *    targeted invocation via [invokeOnNode]).
 */
open class RemoteService @kotlin.jvm.JvmOverloads constructor(
    private val clientInvoker: ((
        baseUrl: String,
        componentName: String,
        methodName: String,
        args: Array<Any?>,
        headers: Map<String, String>,
        version: String,
        timeoutSeconds: Long
    ) -> Any?)? = null
) : SystemComponent() {

    private val configs: MutableMap<String, Long> = LinkedHashMap()
    private val defaultHeaders: MutableMap<String, String> = LinkedHashMap()
    private val nodePerformanceTracker: PerformanceTracker = PerformanceTracker.getInstance()

    var timeoutSeconds: Long = 10L

    @Throws(ConfigurationException::class)
    open fun setRemoteURLs(baseUrls: Array<String>) {
        for (url in baseUrls) {
            try {
                addRemoteNode(url)
            } catch (e: SystemException) {
                throw ConfigurationException(
                    "Error setting remote instance base URL: '$url'. Check format: <URL>[, <priority>]\n" +
                        "Example: http://127.0.0.1:8080, 1\n" +
                        "Error details: $e",
                    e
                )
            }
        }
    }

    @Throws(SystemException::class)
    open fun addRemoteNode(urlAndPriority: String) {
        try {
            val parts = urlAndPriority.split(",")
            val url = normalizeUrl(parts[0])
            require(url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                "Invalid remote URL: '$urlAndPriority'"
            }
            val priority = if (parts.size >= 2 && parts[1].trim().isNotEmpty()) {
                parts[1].trim().toLong()
            } else {
                1L
            }
            addRemoteNode(url, priority)
        } catch (e: Exception) {
            throw SystemException(
                "Error setting remote instance base URL. Check format: <URL>[, <priority>]\n" +
                    "Example: http://127.0.0.1:8080, 1\n" +
                    "Error details: $e",
                e
            )
        }
    }

    open fun addRemoteNode(url: String, priority: Long = 1L) {
        val normalized = normalizeUrl(url)
        if (normalized.isBlank()) return
        configs[normalized] = priority
        nodePerformanceTracker.track(priority, normalized)
    }

    open fun removeRemoteNode(url: String) {
        val normalized = normalizeUrl(url)
        configs.remove(normalized)
    }

    open fun getRemoteUrls(): Map<String, Long> = LinkedHashMap(configs)

    open fun setDefaultHeaders(headers: Map<String, String>) {
        defaultHeaders.clear()
        defaultHeaders.putAll(headers)
    }

    open fun putDefaultHeader(name: String, value: String) {
        if (name.isNotBlank() && value.isNotBlank()) {
            defaultHeaders[name] = value
        }
    }

    @Throws(Exception::class)
    override fun onStart() {
        val props = context?.properties
        val clusterSecret = props?.getProperty(Constants.CLUSTER_SECRET)?.takeIf { it.isNotBlank() }
        if (clusterSecret != null && !defaultHeaders.containsKey(Constants.CLUSTER_TOKEN_HEADER)) {
            defaultHeaders[Constants.CLUSTER_TOKEN_HEADER] = clusterSecret
        }

        // Auto-seed preset Lead Node URL and cluster seeds for dynamic cluster nodes
        val leaderUrl = props?.getProperty(Constants.CLUSTER_LEADER_URL)?.let { normalizeUrl(it) }
        if (!leaderUrl.isNullOrBlank() && !configs.containsKey(leaderUrl)) {
            configs[leaderUrl] = 1L
        }
        props?.getProperty(Constants.CLUSTER_SEEDS)
            ?.split(",")
            ?.map { normalizeUrl(it) }
            ?.filter { it.isNotBlank() }
            ?.forEach { seed ->
                if (!configs.containsKey(seed)) {
                    configs[seed] = 2L
                }
            }

        for (entry in configs.entries) {
            nodePerformanceTracker.track(entry.value, entry.key)
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
        nodePerformanceTracker.clear()
    }

    /**
     * Directly invokes a component/service/plugin method on a specific remote Reveila instance.
     */
    @Throws(Exception::class, JsonException::class)
    open fun invokeOnNode(
        baseUrl: String,
        componentName: String,
        methodName: String,
        args: Array<Any?> = emptyArray(),
        extraHeaders: Map<String, String> = emptyMap()
    ): Any? {
        val normalizedUrl = normalizeUrl(baseUrl)
        require(normalizedUrl.isNotBlank()) { "Target remote baseUrl cannot be blank." }
        require(componentName.isNotBlank()) { "Target componentName cannot be blank." }
        require(methodName.isNotBlank()) { "Target methodName cannot be blank." }

        val startMark = TimeSource.Monotonic.markNow()
        val systemVersion = context?.properties?.getProperty(Constants.SYSTEM_VERSION, Constants.DEFAULT_SYSTEM_VERSION)
            ?.takeIf { it.isNotBlank() }
            ?: Constants.DEFAULT_SYSTEM_VERSION

        val mergedHeaders = LinkedHashMap<String, String>()
        mergedHeaders.putAll(defaultHeaders)
        val clusterSecret = context?.properties?.getProperty(Constants.CLUSTER_SECRET)?.takeIf { it.isNotBlank() }
        if (clusterSecret != null && !mergedHeaders.containsKey(Constants.CLUSTER_TOKEN_HEADER)) {
            mergedHeaders[Constants.CLUSTER_TOKEN_HEADER] = clusterSecret
        }
        mergedHeaders.putAll(extraHeaders)

        val url = ReveilaClient.buildInvokeUrl(normalizedUrl, componentName)
        logger.info("Remote invocation: URL: $url target component: $componentName target method: $methodName version: $systemVersion")

        return try {
            val result = if (clientInvoker != null) {
                clientInvoker.invoke(
                    normalizedUrl,
                    componentName,
                    methodName,
                    args,
                    mergedHeaders,
                    systemVersion,
                    timeoutSeconds
                )
            } else {
                val client = ReveilaClient(
                    baseUrl = normalizedUrl,
                    version = systemVersion,
                    defaultHeaders = mergedHeaders,
                    timeoutSeconds = timeoutSeconds
                )
                client.invoke(componentName, methodName, args, normalizedUrl)
            }
            val timeUsed = startMark.elapsedNow().inWholeMilliseconds
            nodePerformanceTracker.track(timeUsed, normalizedUrl)
            result
        } catch (e: Exception) {
            val timeUsed = startMark.elapsedNow().inWholeMilliseconds
            nodePerformanceTracker.track(timeUsed + PerformanceTracker.DEFAULT_PENALTY_MS, normalizedUrl)
            throw e
        }
    }

    @Throws(Exception::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String): Any? {
        return invokeInternal(arrayOf(componentName, methodName))
    }

    @Throws(Exception::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1))
    }

    @Throws(Exception::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?, arg2: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1, arg2))
    }

    @Throws(Exception::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?, arg2: Any?, arg3: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1, arg2, arg3))
    }

    @Throws(Exception::class, JsonException::class)
    open fun invoke(vararg remoteCallArgs: Any?): Any? {
        return invokeInternal(remoteCallArgs)
    }

    private fun invokeInternal(remoteCallArgs: Array<out Any?>?): Any? {
        if (remoteCallArgs == null || remoteCallArgs.size < 2) {
            throw IllegalArgumentException(
                "The remote 'invoke' requires at least 2 arguments: componentName, and methodName"
            )
        }

        var argOffset = 2
        val componentName: String
        val methodName: String
        val baseUrl: String

        if (remoteCallArgs[0] is String && (remoteCallArgs[0] as String).startsWith("http")) {
            if (remoteCallArgs.size < 3) {
                throw IllegalArgumentException(
                    "Targeted remote 'invoke' requires at least 3 arguments: baseUrl, componentName, and methodName"
                )
            }
            baseUrl = remoteCallArgs[0] as String
            argOffset = 3
            componentName = remoteCallArgs[1] as String
            methodName = remoteCallArgs[2] as String
        } else {
            val bestUrl = nodePerformanceTracker.getBestNodeUrl()
                ?: configs.minByOrNull { it.value }?.key
                ?: throw IllegalStateException(
                    "No 'RemoteURLs' or cluster leader URL configured for RemoteService. " +
                        "Specify at least one valid endpoint URL."
                )
            baseUrl = bestUrl
            componentName = remoteCallArgs[0] as String
            methodName = remoteCallArgs[1] as String
        }

        val args: Array<Any?> = if (remoteCallArgs.size > argOffset && remoteCallArgs[argOffset] is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            remoteCallArgs[argOffset] as Array<Any?>
        } else {
            Array(maxOf(0, remoteCallArgs.size - argOffset)) { i ->
                remoteCallArgs[argOffset + i]
            }
        }

        return invokeOnNode(
            baseUrl = baseUrl,
            componentName = componentName,
            methodName = methodName,
            args = args
        )
    }

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
}
