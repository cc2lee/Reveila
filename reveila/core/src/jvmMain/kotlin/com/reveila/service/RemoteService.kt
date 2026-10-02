package com.reveila.service

import java.io.IOException
import java.net.URI
import java.net.URL
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.TimeUnit
import com.reveila.error.ConfigurationException
import com.reveila.error.SystemException
import com.reveila.system.PerformanceTracker
import com.reveila.system.SystemComponent
import com.reveila.util.json.JsonException
import com.reveila.util.json.JsonUtil
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

/**
 * A system component that can invoke methods on a remote Reveila instance via its REST API.
 * This acts as a proxy, allowing any Reveila client (mobile or backend) to interact
 * with another Reveila instance, enabling clustered or distributed setups.
 */
open class RemoteService : SystemComponent() {

    companion object {
        @JvmField
        val JSON: MediaType = "application/json; charset=utf-8".toMediaType()

        // OkHttpClient is thread-safe and designed to be shared.
        @JvmStatic
        protected val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val configs: MutableMap<URL, Number> = Collections.synchronizedMap(HashMap<URL, Number>())
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
            val url = URI(array[0].trim()).toURL()
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

    @Synchronized
    @Throws(Exception::class)
    override fun onStart() {
        for (entry in configs.entries) {
            nodePerformanceTracker.track(entry.value, entry.key)
        }
    }

    @Synchronized
    @Throws(Exception::class)
    override fun onStop() {
        nodePerformanceTracker.clear()
    }

    @Throws(IOException::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String): Any? {
        return invokeInternal(arrayOf(componentName, methodName))
    }

    @Throws(IOException::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1))
    }

    @Throws(IOException::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?, arg2: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1, arg2))
    }

    @Throws(IOException::class, JsonException::class)
    open fun invoke(componentName: String, methodName: String, arg1: Any?, arg2: Any?, arg3: Any?): Any? {
        return invokeInternal(arrayOf(componentName, methodName, arg1, arg2, arg3))
    }

    @Throws(IOException::class, JsonException::class)
    open fun invoke(vararg remoteCallArgs: Any?): Any? {
        return invokeInternal(remoteCallArgs)
    }

    private fun invokeInternal(remoteCallArgs: Array<out Any?>?): Any? {
        if (remoteCallArgs == null || remoteCallArgs.size < 2) {
            throw IllegalArgumentException(
                "The remote 'invoke' requires at least 2 arguments: componentName, and methodName"
            )
        }

        val startTime = System.currentTimeMillis()
        var argOffset = 2
        val componentName: String
        val methodName: String
        val baseUrl: URL

        if (remoteCallArgs[0] is URL) {
            baseUrl = remoteCallArgs[0] as URL
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
                System.arraycopy(remoteCallArgs, argOffset, args, 0, remoteCallArgs.size - argOffset)
            }
        }

        var url = baseUrl.toExternalForm()
        if (!url.endsWith("/")) {
            url += "/"
        }
        url += "api/components/$componentName/invoke"

        val requestPayload = mapOf("methodName" to methodName, "args" to args)
        val jsonBody = JsonUtil.toJsonString(requestPayload)
        val body = jsonBody.toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).build()

        logger?.info("Remote invocation: URL: $url target component: $componentName target method: $methodName")
        val response = client.newCall(request).execute()

        val responseBody: ResponseBody? = response.body
        val responseBodyString: String? = responseBody?.string()

        val timeUsed = System.currentTimeMillis() - startTime
        if (!response.isSuccessful) {
            nodePerformanceTracker.track(timeUsed + PerformanceTracker.DEFAULT_PENALTY_MS, baseUrl)
            throw IOException(
                "Remote invocation failed with HTTP code ${response.code} for $url. Body: $responseBodyString"
            )
        }
        nodePerformanceTracker.track(timeUsed, baseUrl)
        return if (responseBodyString.isNullOrEmpty()) null
        else JsonUtil.toObject(responseBodyString, Any::class.java)
    }
}
