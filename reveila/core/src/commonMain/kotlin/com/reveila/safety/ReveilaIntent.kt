package com.reveila.safety

/**
 * Represents a normalized intent within the Reveila ecosystem,
 * converted from external tool signals.
 */
data class ReveilaIntent(
    val intent: String,
    val arguments: Map<String, Any?> = emptyMap(),
    val sourceTool: String? = null
) {
    fun intent(): String = intent
    fun arguments(): Map<String, Any?> = arguments
    fun sourceTool(): String? = sourceTool
}
