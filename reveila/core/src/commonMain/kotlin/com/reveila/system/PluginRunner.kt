package com.reveila.system

import com.reveila.service.HttpClientService
import com.reveila.service.PlatformHttpEngine
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

object PluginRunner {

    fun reportResult(callbackUrl: String?, traceId: String?, pluginId: String?, methodName: String?, result: Any?) {
        if (callbackUrl.isNullOrEmpty()) {
            return
        }

        try {
            val jitToken = PlatformSystem.getEnv("REVEILA_JIT_TOKEN")
            val payload = mapOf(
                "trace_id" to traceId,
                "plugin_id" to pluginId,
                "method" to methodName,
                "status" to "SUCCESS",
                "data" to (result ?: "null")
            )

            val json = JsonUtil.toJsonString(payload)
            val headers = mapOf(
                "Content-Type" to HttpClientService.JSON,
                "Authorization" to "Bearer $jitToken"
            )

            PlatformHttpEngine().execute(
                url = "$callbackUrl/api/system/callback/result",
                method = "POST",
                payload = json,
                contentType = HttpClientService.JSON,
                headers = headers,
                timeoutSeconds = 30
            )
        } catch (e: Exception) {
            // Ignore error reporting failure
        }
    }
}
