package com.reveila.system

import com.reveila.util.json.JsonUtil

internal actual fun executeContainerPlugin(args: Array<String>) {
    val pluginId = System.getenv("PLUGIN_ID")
    val pluginClass = System.getenv("PLUGIN_CLASS")
    val methodName = System.getenv("METHOD_NAME") ?: "defaultMethod"
    val traceId = System.getenv("TRACE_ID")
    val argsJson = System.getenv("PLUGIN_ARGS_JSON")
    val callbackUrl = System.getenv(Constants.ENV_CALLBACK_URL)

    println("--- Reveila Worker Agent Starting ---")
    println("Plugin ID: $pluginId")
    println("Class: $pluginClass")
    println("Method: $methodName")
    println("Trace: $traceId")

    if (pluginClass.isNullOrEmpty()) {
        System.err.println("Error: PLUGIN_CLASS environment variable is not set.")
        PluginRunner.reportResult(callbackUrl, traceId, pluginId, methodName, "PLUGIN_CLASS environment variable is not set.", "ERROR")
        System.exit(1)
        return
    }

    try {
        var methodArgs: Array<Any?> = emptyArray()
        if (!argsJson.isNullOrEmpty()) {
            try {
                val argsMap = JsonUtil.parseJsonStringToMap(argsJson)
                methodArgs = argsMap.values.toTypedArray()
            } catch (e: Exception) {
                System.err.println("Warning: Failed to parse PLUGIN_ARGS_JSON. Using empty arguments.")
            }
        }

        val clazz = Class.forName(pluginClass)
        val instance = clazz.getDeclaredConstructor().newInstance()

        val method = ReflectionMethod.findBestMethod(clazz, methodName, methodArgs)
            ?: throw NoSuchMethodException("Could not find method $methodName on class $pluginClass")

        println("Executing $methodName...")
        val result = method.invoke(instance, *ReflectionMethod.coerceArguments(method, methodArgs))

        println("--- Execution Completed Successfully ---")
        println("Result: $result")

        PluginRunner.reportResult(callbackUrl, traceId, pluginId, methodName, result, "SUCCESS")
        System.exit(0)
    } catch (e: Exception) {
        System.err.println("--- Execution Failed ---")
        e.printStackTrace()
        PluginRunner.reportResult(callbackUrl, traceId, pluginId, methodName, e.message ?: "Execution failed", "ERROR")
        System.exit(1)
    }
}
