package com.reveila.system

import com.reveila.util.json.JsonUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Mini-Reveila Runtime (Reveila Worker Agent).
 * ADR 0006: Executes plugin code in total isolation within a container.
 * 
 * @author CL
 */
object PluginRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val pluginId = System.getenv("PLUGIN_ID")
        val pluginClass = System.getenv("PLUGIN_CLASS")
        val methodName = System.getenv("METHOD_NAME")
        val traceId = System.getenv("TRACE_ID")
        val argsJson = System.getenv("PLUGIN_ARGS_JSON")
        val callbackUrl = System.getenv("REVEILA_CALLBACK_URL")

        println("--- Reveila Worker Agent Starting ---")
        println("Plugin ID: $pluginId")
        println("Class: $pluginClass")
        println("Method: $methodName")
        println("Trace: $traceId")

        if (pluginClass.isNullOrEmpty()) {
            System.err.println("Error: PLUGIN_CLASS environment variable is not set.")
            System.exit(1)
        }

        try {
            // 1. Parse Arguments
            var methodArgs: Array<Any?> = emptyArray()
            if (!argsJson.isNullOrEmpty()) {
                try {
                    val argsMap = JsonUtil.parseJsonStringToMap(argsJson)
                    methodArgs = argsMap.values.toTypedArray()
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to parse PLUGIN_ARGS_JSON. Using empty arguments.")
                }
            }

            // 2. Load Plugin Class
            val clazz = Class.forName(pluginClass)
            val instance = clazz.getDeclaredConstructor().newInstance()

            // 3. Find and Invoke Method
            val method = ReflectionMethod.findBestMethod(clazz, methodName, methodArgs)
                ?: throw NoSuchMethodException("Could not find method $methodName on class $pluginClass")

            println("Executing $methodName...")
            val result = method.invoke(instance, *ReflectionMethod.coerceArguments(method, methodArgs))

            println("--- Execution Completed Successfully ---")
            println("Result: $result")

            // 4. Report Result via Callback
            reportResult(callbackUrl, traceId, pluginId, methodName, result)

            System.exit(0)
        } catch (e: Exception) {
            System.err.println("--- Execution Failed ---")
            e.printStackTrace()
            System.exit(1)
        }
    }

    @JvmStatic
    private fun reportResult(callbackUrl: String?, traceId: String?, pluginId: String?, methodName: String?, result: Any?) {
        if (callbackUrl.isNullOrEmpty()) {
            println("No callback URL provided. Result not reported.")
            return
        }

        try {
            val jitToken = System.getenv("REVEILA_JIT_TOKEN")
            val payload = mapOf(
                "trace_id" to traceId,
                "plugin_id" to pluginId,
                "method" to methodName,
                "status" to "SUCCESS",
                "data" to (result ?: "null")
            )

            val json = JsonUtil.toJsonString(payload)

            val client = OkHttpClient()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$callbackUrl/api/system/callback/result")
                .post(body)
                .header("Authorization", "Bearer $jitToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    println("Result reported successfully to $callbackUrl")
                } else {
                    System.err.println("Failed to report result. Status: ${response.code}")
                }
            }
        } catch (e: Exception) {
            System.err.println("Error reporting result: ${e.message}")
        }
    }
}
