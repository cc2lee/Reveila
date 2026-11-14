package com.reveila.system

import com.reveila.system.platform.PlatformSystem

/**
 * The Mini-Reveila Runtime (Reveila Worker Agent).
 * ADR 0006: Executes plugin code in isolation within a container and reports
 * results back to the host via the unified `ReveilaClient` callback architecture.
 */
object PluginRunner {

    @kotlin.jvm.JvmStatic
    fun main(args: Array<String>) {
        executeContainerPlugin(args)
    }

    @kotlin.jvm.JvmOverloads
    @kotlin.jvm.JvmStatic
    fun reportResult(
        callbackUrl: String?,
        traceId: String?,
        pluginId: String?,
        methodName: String?,
        result: Any?,
        status: String = "SUCCESS"
    ) {
        if (callbackUrl.isNullOrEmpty()) {
            return
        }

        try {
            val jitToken = PlatformSystem.getEnv(Constants.ENV_JIT_TOKEN)
            val version = PlatformSystem.getEnv(Constants.ENV_REVEILA_VERSION)
                ?.takeIf { it.isNotBlank() }
                ?: Constants.DEFAULT_SYSTEM_VERSION

            val client = ReveilaClient(
                baseUrl = callbackUrl,
                jitToken = jitToken,
                version = version
            )
            client.reportResult(
                traceId = traceId,
                pluginId = pluginId,
                methodName = methodName,
                result = result,
                status = status,
                targetBaseUrl = callbackUrl
            )
        } catch (e: Exception) {
            // Ignore error reporting failure inside ephemeral container exit path
        }
    }
}

internal expect fun executeContainerPlugin(args: Array<String>)
