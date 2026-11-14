package com.reveila.system

import com.reveila.error.SystemException
import com.reveila.error.VersionConflictException
import com.reveila.service.HttpClientService
import com.reveila.service.PlatformHttpEngine
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

/**
 * Unified Kotlin Multiplatform client for distributed Reveila nodes and containerized
 * sandbox plugins (including AI Agents) to invoke components via the standardized
 * `/api/components/{componentName}/invoke` endpoint.
 */
open class ReveilaClient @kotlin.jvm.JvmOverloads constructor(
    private val baseUrl: String? = PlatformSystem.getEnv(Constants.ENV_CALLBACK_URL),
    private val jitToken: String? = PlatformSystem.getEnv(Constants.ENV_JIT_TOKEN),
    private val version: String = PlatformSystem.getEnv(Constants.ENV_REVEILA_VERSION)
        ?.takeIf { it.isNotBlank() }
        ?: Constants.DEFAULT_SYSTEM_VERSION,
    private val defaultHeaders: Map<String, String> = emptyMap(),
    private val timeoutSeconds: Long = 30L
) {

    companion object {
        const val UNIFIED_INVOKE_PATH_PREFIX = "api/components/"
        const val UNIFIED_INVOKE_PATH_SUFFIX = "/invoke"
        const val MANAGED_INVOCATION_COMPONENT = "ManagedInvocation"
        const val CALLBACK_METHOD = "handleCallback"
        const val CALLBACK_RESULT_METHOD = "handleCallbackResult"

        @kotlin.jvm.JvmStatic
        fun buildInvokeUrl(baseUrl: String, componentName: String): String {
            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return "$normalizedBase$UNIFIED_INVOKE_PATH_PREFIX$componentName$UNIFIED_INVOKE_PATH_SUFFIX"
        }

        @kotlin.jvm.JvmStatic
        fun buildMethodPayload(
            methodName: String,
            args: Array<out Any?>?,
            version: String = Constants.DEFAULT_SYSTEM_VERSION
        ): Map<String, Any?> {
            return linkedMapOf(
                Constants.VERSION to version,
                "methodName" to methodName,
                "args" to (args?.toList() ?: emptyList<Any?>())
            )
        }
    }

    /**
     * Invokes a component method on the target Reveila node using the unified
     * `POST /api/components/{componentName}/invoke` endpoint and versioned MethodDTO payload.
     */
    @kotlin.jvm.JvmOverloads
    open fun invoke(
        componentName: String,
        methodName: String,
        args: Array<out Any?>? = null,
        targetBaseUrl: String? = this.baseUrl,
        extraHeaders: Map<String, String> = emptyMap()
    ): Any? {
        val effectiveBaseUrl = targetBaseUrl?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Reveila target base URL is not configured.")

        val url = buildInvokeUrl(effectiveBaseUrl, componentName)
        val payloadMap = buildMethodPayload(methodName, args, version)
        val jsonBody = JsonUtil.toJsonString(payloadMap)

        val headers = LinkedHashMap<String, String>()
        headers["Content-Type"] = HttpClientService.JSON
        if (!jitToken.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $jitToken"
        }
        headers.putAll(defaultHeaders)
        headers.putAll(extraHeaders)

        val resp = PlatformHttpEngine().execute(
            url = url,
            method = "POST",
            payload = jsonBody,
            contentType = HttpClientService.JSON,
            headers = headers,
            timeoutSeconds = timeoutSeconds
        )

        if (resp.statusCode == 409) {
            throw VersionConflictException(
                clientVersion = version,
                backendVersion = "remote",
                message = "Remote invocation rejected due to API version conflict (HTTP 409) for $url: ${resp.body}"
            )
        }

        if (resp.statusCode !in 200..299) {
            throw SystemException("Remote invocation failed with HTTP code ${resp.statusCode} for $url. Body: ${resp.body}")
        }

        return JsonUtil.parseJsonValue(resp.body)
    }

    /**
     * Invokes a host component/tool from a sandboxed plugin container via the governed
     * `ManagedInvocation.handleCallback` method on the unified `/api/components/ManagedInvocation/invoke` endpoint.
     */
    open fun invokeHost(component: String, method: String, arguments: Map<String, Any?>?): Any? {
        if (baseUrl.isNullOrBlank() || jitToken.isNullOrBlank()) {
            throw IllegalStateException("Reveila callback environment (REVEILA_CALLBACK_URL / REVEILA_JIT_TOKEN) not configured.")
        }

        val result = invoke(
            componentName = MANAGED_INVOCATION_COMPONENT,
            methodName = CALLBACK_METHOD,
            args = arrayOf(jitToken, component, method, arguments ?: emptyMap<String, Any?>()),
            targetBaseUrl = baseUrl
        )

        if (result is Map<*, *> && result.size == 1 && result.containsKey("data")) {
            return result["data"]
        }
        return result
    }

    /**
     * Reports the final execution result (or error) of a containerized plugin back to the host
     * via `ManagedInvocation.handleCallbackResult` on the unified `/api/components/ManagedInvocation/invoke` endpoint.
     */
    @kotlin.jvm.JvmOverloads
    open fun reportResult(
        traceId: String?,
        pluginId: String?,
        methodName: String?,
        result: Any?,
        status: String = "SUCCESS",
        targetBaseUrl: String? = this.baseUrl
    ): Any? {
        val effectiveBaseUrl = targetBaseUrl?.takeIf { it.isNotBlank() } ?: return null
        val effectiveToken = jitToken?.takeIf { it.isNotBlank() } ?: return null

        return invoke(
            componentName = MANAGED_INVOCATION_COMPONENT,
            methodName = CALLBACK_RESULT_METHOD,
            args = arrayOf(effectiveToken, traceId, pluginId, methodName, status, result ?: "null"),
            targetBaseUrl = effectiveBaseUrl
        )
    }
}
