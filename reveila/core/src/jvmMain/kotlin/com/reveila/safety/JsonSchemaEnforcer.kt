package com.reveila.safety

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy

class JsonSchemaEnforcer : SystemComponent(), SchemaEnforcer {

    private var registry: MetadataRegistry? = null
    private val mapper = ObjectMapper()
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)

    @Throws(Exception::class)
    override fun onStart() {
        val proxy = context?.getProxy("MetadataRegistry")
        if (proxy is SystemProxy) {
            this.registry = proxy.getInstance() as? MetadataRegistry
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    override fun enforce(
        pluginId: String,
        rawArguments: Map<String, @JvmSuppressWildcards Any>
    ): Map<String, @JvmSuppressWildcards Any> {
        val manifest = registry?.getManifest(pluginId)
            ?: throw IllegalArgumentException("Cannot enforce schema: Plugin $pluginId not found.")

        try {
            val schemaNode: JsonNode = mapper.valueToTree(manifest.toolDefinitions())
            val dataNode: JsonNode = mapper.valueToTree(rawArguments)

            val schema = factory.getSchema(schemaNode)
            val errors: Set<ValidationMessage> = schema.validate(dataNode)

            if (errors.isNotEmpty()) {
                val errorMsg = errors.joinToString(", ") { it.message }
                throw IllegalArgumentException("Schema validation failed for plugin $pluginId: $errorMsg")
            }

            return rawArguments
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw RuntimeException("Error during schema validation for plugin $pluginId", e)
        }
    }
}
