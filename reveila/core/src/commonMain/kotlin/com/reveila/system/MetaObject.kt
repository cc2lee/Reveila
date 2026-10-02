package com.reveila.system

import kotlin.jvm.JvmSuppressWildcards

class MetaObject(
    val dataMap: Map<String, @JvmSuppressWildcards Any>
) {
    var isPlugin: Boolean = false

    /**
     * Checks if the component is configured to be thread-safe, which implies
     * a singleton lifecycle (one instance is created and reused).
     * Defaults to `true` if the property is not specified.
     */
    fun isThreadSafe(): Boolean {
        val value = dataMap[Constants.THREAD_SAFE]
        return !"false".equals(value?.toString(), ignoreCase = true)
    }

    fun getName(): String? = dataMap[Constants.NAME] as? String

    fun getImplementationClassName(): String? = dataMap[Constants.CLASS] as? String

    fun getDescription(): String? = dataMap[Constants.DESCRIPTION] as? String

    fun getVersion(): String? = dataMap[Constants.VERSION] as? String

    fun getAuthor(): String? = dataMap[Constants.AUTHOR] as? String

    fun getLicense(): String? = dataMap[Constants.LICENSE_TOKEN] as? String

    @Suppress("UNCHECKED_CAST")
    fun getArguments(): List<Map<String, @JvmSuppressWildcards Any>> {
        val value = dataMap[Constants.ARGUMENTS]
        return if (value is List<*>) value as List<Map<String, Any>> else emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun getAutoRunConf(): Map<String, @JvmSuppressWildcards Any> {
        val value = dataMap[Constants.RUNNABLE]
        return if (value is Map<*, *>) value as Map<String, Any> else emptyMap()
    }

    fun isHotDeployEnabled(): Boolean {
        return "true".equals(dataMap[Constants.HOT_DEPLOY]?.toString(), ignoreCase = true)
    }

    @Suppress("UNCHECKED_CAST")
    fun getDependencies(): List<String> {
        val value = dataMap[Constants.DEPENDENCIES]
        return if (value is List<*>) value as List<String> else emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    fun requiresRuntimeIsolation(): Boolean {
        val perimeter = dataMap[Constants.SECURITY_PERIMETER]
        if (perimeter is Map<*, *>) {
            val pMap = perimeter as Map<String, Any?>
            val iv = pMap[Constants.ISOLATION]
            if (iv is Boolean) {
                return iv
            }
            return "true".equals(iv?.toString(), ignoreCase = true)
        }
        return false
    }

    /**
     * ADR 0006: Retrieves the network policy from the security-perimeter.
     *
     * @return The network policy string (e.g., "restricted"), or null if not defined.
     */
    @Suppress("UNCHECKED_CAST")
    fun getNetworkPolicy(): String? {
        val perimeter = dataMap[Constants.SECURITY_PERIMETER]
        if (perimeter is Map<*, *>) {
            val pMap = perimeter as Map<String, Any?>
            return pMap[Constants.NETWORK] as? String
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun isAutoStart(): Boolean {
        val service = dataMap[Constants.SERVICE]
        if (service is Map<*, *>) {
            val sMap = service as Map<String, Any?>
            val autoStart = sMap[Constants.AUTO_START]
            if (autoStart is Boolean) {
                return autoStart
            }
            return !"false".equals(autoStart?.toString(), ignoreCase = true)
        }
        return true
    }

    fun getManifest(): Manifest? {
        return dataMap[Constants.MANEFEST] as? Manifest
    }
}
