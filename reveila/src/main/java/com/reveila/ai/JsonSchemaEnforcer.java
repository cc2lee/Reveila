package com.reveila.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates model-generated arguments against JSON schemas defined in the MetadataRegistry.
 * Uses networknt json-schema-validator for production-grade enforcement.
 *
 * @author CL
 */
public class JsonSchemaEnforcer implements SchemaEnforcer {
    private final MetadataRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public JsonSchemaEnforcer(MetadataRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Map<String, Object> enforce(String pluginId, Map<String, Object> rawArguments) {
        MetadataRegistry.PluginManifest manifest = registry.getManifest(pluginId);
        if (manifest == null) {
            throw new IllegalArgumentException("Cannot enforce schema: Plugin " + pluginId + " not found.");
        }

        try {
            JsonNode schemaNode = mapper.valueToTree(manifest.toolDefinitions());
            JsonNode dataNode = mapper.valueToTree(rawArguments);
            
            JsonSchema schema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(dataNode);

            if (!errors.isEmpty()) {
                String errorMsg = errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Schema validation failed for plugin " + pluginId + ": " + errorMsg);
            }

            return rawArguments;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new RuntimeException("Error during schema validation for plugin " + pluginId, e);
        }
    }
}
