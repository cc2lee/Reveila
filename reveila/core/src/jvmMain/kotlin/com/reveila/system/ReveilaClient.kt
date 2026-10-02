package com.reveila.system

import java.io.IOException
import com.reveila.util.json.JsonUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Secure client for containerized plugins to call back into the main Reveila node.
 * Used for tool execution and proxy invocation from isolated environments.
 * 
 * @author CL
 */
open class ReveilaClient {
    private val callbackUrl: String? = System.getenv("REVEILA_CALLBACK_URL")
    private val jitToken: String? = System.getenv("REVEILA_JIT_TOKEN")
    private val httpClient: OkHttpClient = OkHttpClient()

    /**
     * Invokes a system tool or proxy via the host's callback bridge.
     */
    @Throws(IOException::class)
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

        try {
            val json = JsonUtil.toJsonString(requestBody)
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$callbackUrl/api/system/callback")
                .post(body)
                .header("Authorization", "Bearer $jitToken")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw IOException("Host invocation failed: ${response.code} $errorBody")
                }
                val resultJson = response.body?.string() ?: "{}"
                val resultMap = JsonUtil.parseJsonStringToMap(resultJson)
                return resultMap["data"]
            }
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException("Callback execution error: " + e.message, e)
        }
    }
}
