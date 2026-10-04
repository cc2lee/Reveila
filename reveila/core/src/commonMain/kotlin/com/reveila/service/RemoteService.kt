package com.reveila.service

import com.reveila.error.ConfigurationException
import com.reveila.error.SystemException
import com.reveila.system.PerformanceTracker
import com.reveila.system.SystemComponent
import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil
import kotlin.time.TimeSource

open class RemoteService : SystemComponent() {

    private val configs: MutableMap<String, Long> = mutableMapOf()
    private val nodePerformanceTracker: PerformanceTracker = PerformanceTracker.getInstance()

    @Throws(ConfigurationException::class)
    open fun setRemoteURLs(baseUrls: Array<String>) {
        for (url in baseUrls) {
            try {
                addRemoteNode(url)
            } catch (e: SystemException) {
                throw ConfigurationException(
                    "Error setting remote instance base URL: '$url'. Check format: <URL>, <priority>\n" +
                    "Example: http://127.0.0.1:8080, 1\n" +
                    "Error details: $e", e
                )
            }
        }
    }

    @Throws(SystemException::class)
    open fun addRemoteNode(urlAndPriority: String) {
        try {
            val array = urlAndPriority.split(",")
            val url = array[0].trim()
            val priority = array[1].trim().toLong()
            configs[url] = priority
        } catch (e: Exception) {
            throw SystemException(
                "Error setting remote instance base URL. Check format: <URL>, <priority>\n" +
                "Example: http://127.0.0.1:8080, 1\n" +
                "Error details: $e", e
            )
        }
    }

    @Throws(Exception::class)
    override fun onStart() {
        for (entry in configs.entries) {
            nodePerformanceTracker.track(entry.value, entry.key)
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
        nodePerformanceTracker.clear()
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

        val startMark = TimeSource.Monotonic.markNow()
        var argOffset = 2
        val componentName: String
        val methodName: String
        val baseUrl: String

        if (remoteCallArgs[0] is String && (remoteCallArgs[0] as String).startsWith("http")) {
            baseUrl = remoteCallArgs[0] as String
            argOffset = 3
            componentName = remoteCallArgs[1] as String
            methodName = remoteCallArgs[2] as String
        } else {
            val bestUrl = nodePerformanceTracker.getBestNodeUrl()
                ?: throw IllegalStateException(
                    "No 'BaseURL' argument specified in the component configuration. " +
                    "To use ReveilaRemote, you must specify at least one valid end-point URL."
                )
            baseUrl = bestUrl
            componentName = remoteCallArgs[0] as String
            methodName = remoteCallArgs[1] as String
        }

        val args: Array<Any?>
        if (remoteCallArgs.size > argOffset && remoteCallArgs[argOffset] is Array<*>) {
            @Suppress("UNCHECKED_CAST")
            args = remoteCallArgs[argOffset] as Array<Any?>
        } else {
            args = arrayOfNulls(remoteCallArgs.size - argOffset)
            if (remoteCallArgs.size > argOffset) {
                var j = 0
                for (i in argOffset until remoteCallArgs.size) {
                    args[j++] = remoteCallArgs[i]
                }
            }
        }

        var url = baseUrl
        if (!url.endsWith("/")) {
            url += "/"
        }
        url += "api/components/$componentName/invoke"

        val requestPayload = mapOf("methodName" to methodName, "args" to args.toList())
        val jsonBody = JsonUtil.toJsonString(requestPayload)

        logger.info("Remote invocation: URL: $url target component: $componentName target method: $methodName")
        val httpEngine = PlatformHttpEngine()
        val response = httpEngine.execute(
            url = url,
            method = "POST",
            payload = jsonBody,
            contentType = HttpClientService.JSON,
            headers = mapOf("Content-Type" to HttpClientService.JSON),
            timeoutSeconds = 30
        )

        val timeUsed = startMark.elapsedNow().inWholeMilliseconds
        if (response.statusCode !in 200..299) {
            nodePerformanceTracker.track(timeUsed + PerformanceTracker.DEFAULT_PENALTY_MS, baseUrl)
            throw Exception(
                "Remote invocation failed with HTTP code ${response.statusCode} for $url. Body: ${response.body}"
            )
        }
        nodePerformanceTracker.track(timeUsed, baseUrl)
        return if (response.body.isNullOrEmpty()) null
        else JsonUtil.parseJsonStringToMap(response.body)
    }
}
