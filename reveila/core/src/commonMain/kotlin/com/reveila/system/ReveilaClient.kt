package com.reveila.system

import com.reveila.service.HttpClientService
import com.reveila.service.PlatformHttpEngine
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

/**
 * Pure Kotlin Multiplatform client for containerized plugins to call back into the main Reveila node.
 */
open class ReveilaClient {
    private val callbackUrl: String? = PlatformSystem.getEnv("REVEILA_CALLBACK_URL")
    private val jitToken: String? = PlatformSystem.getEnv("REVEILA_JIT_TOKEN")

    /**
     * Invokes a system tool or proxy via the host's callback bridge.
     */
    open fun invokeHost(component: String, method: String, arguments: Map<String, Any?>?): Any? {
        if (callbackUrl == null || jitToken == null) {
            throw IllegalStateException("Reveila callback environment not configured.")
        }

        val requestBody = mapOf(
            "component" to component,
            "method" to method,
            "arguments" to (arguments ?: emptyMap<String, Any?>()),
            "jit_token" to jitToken
        )

        val json = JsonUtil.toJsonString(requestBody)
        val headers = mapOf(
            "Content-Type" to HttpClientService.JSON,
            "Authorization" to "Bearer $jitToken"
        )
        val resp = PlatformHttpEngine().execute(
            url = "$callbackUrl/api/system/callback",
            method = "POST",
            payload = json,
            contentType = HttpClientService.JSON,
            headers = headers,
            timeoutSeconds = 30
        )

        if (resp.statusCode !in 200..299) {
            throw Exception("Host invocation failed: ${resp.statusCode} ${resp.body}")
        }
        val resultMap = JsonUtil.parseJsonStringToMap(resp.body)
        return resultMap["data"]
    }
}
