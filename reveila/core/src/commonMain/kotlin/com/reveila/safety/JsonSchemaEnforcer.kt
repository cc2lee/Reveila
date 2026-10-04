package com.reveila.safety

import com.reveila.system.SystemComponent

/**
 * Pure Kotlin Multiplatform JSON Schema Enforcer.
 * Validates tool call parameters and inputs against plugin capability schemas.
 */
class JsonSchemaEnforcer : SystemComponent(), SchemaEnforcer {

    private var registry: MetadataRegistry? = null

    @Throws(Exception::class)
    override fun onStart() {
        val proxy = context?.getProxy("MetadataRegistry")
        this.registry = proxy?.getInstance() as? MetadataRegistry
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    override fun enforce(
        pluginId: String,
        rawArguments: Map<String, Any>
    ): Map<String, Any> {
        val manifest = registry?.getManifest(pluginId)
            ?: throw IllegalArgumentException("Cannot enforce schema: Plugin $pluginId not found.")

        val toolDefs = manifest.toolDefinitions()
        validateSchema(pluginId, toolDefs, rawArguments)
        return rawArguments
    }

    private fun validateSchema(pluginId: String, schema: Map<String, Any>?, data: Map<String, Any>) {
        if (schema == null || schema.isEmpty()) return

        @Suppress("UNCHECKED_CAST")
        val required = schema["required"] as? List<String>
        if (required != null) {
            for (field in required) {
                if (!data.containsKey(field) || data[field] == null) {
                    throw IllegalArgumentException("Schema validation failed for plugin $pluginId: missing required property '$field'")
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Map<String, Any>>
        if (properties != null) {
            for ((key, propSpec) in properties) {
                val value = data[key] ?: continue
                val expectedType = propSpec["type"] as? String ?: continue
                when (expectedType.lowercase()) {
                    "string" -> if (value !is String) throw IllegalArgumentException("Field '$key' must be a string")
                    "number", "integer" -> if (value !is Number) throw IllegalArgumentException("Field '$key' must be a number")
                    "boolean" -> if (value !is Boolean) throw IllegalArgumentException("Field '$key' must be a boolean")
                    "array" -> if (value !is List<*>) throw IllegalArgumentException("Field '$key' must be an array")
                    "object" -> if (value !is Map<*, *>) throw IllegalArgumentException("Field '$key' must be an object")
                }
            }
        }
    }
}
