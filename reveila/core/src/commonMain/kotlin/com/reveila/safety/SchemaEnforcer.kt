package com.reveila.safety

import kotlin.jvm.JvmSuppressWildcards

/**
 * Validates model-generated arguments against tool schemas before invocation.
 */
interface SchemaEnforcer {
    /**
     * Validates and cleans raw arguments against the plugin's schema.
     */
    fun enforce(
        pluginId: String,
        rawArguments: Map<String, @JvmSuppressWildcards Any>
    ): Map<String, @JvmSuppressWildcards Any>
}
