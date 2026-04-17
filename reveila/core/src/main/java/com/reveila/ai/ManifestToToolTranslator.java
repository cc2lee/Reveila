package com.reveila.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class ManifestToToolTranslator {

    public ToolSpecification translateToSpec(JSONObject manifest) {
        JSONObject plugin = manifest.getJSONObject("plugin");
        JSONObject targetMethod = plugin.getJSONArray("methods").getJSONObject(0);

        JsonObjectSchema.Builder parametersBuilder = JsonObjectSchema.builder();
        JSONArray parameters = targetMethod.optJSONArray("parameters");

        if (parameters != null) {
            for (int i = 0; i < parameters.length(); i++) {
                JSONObject param = parameters.getJSONObject(i);
                String name = param.getString("name");
                String type = param.getString("type");
                String desc = param.optString("description", "");

                JsonSchemaElement element = switch (type) {
                    case "int", "java.lang.Integer" -> JsonIntegerSchema.builder().description(desc).build();
                    case "boolean", "java.lang.Boolean" -> JsonBooleanSchema.builder().description(desc).build();
                    default -> JsonStringSchema.builder().description(desc).build();
                };

                parametersBuilder.addProperty(name, element);
                if (param.optBoolean("isRequired", false)) {
                    parametersBuilder.required(java.util.Collections.singletonList(name));
                }
            }
        }

        return ToolSpecification.builder()
                .name(plugin.getString("name") + "_" + targetMethod.getString("name"))
                .description(targetMethod.getString("description"))
                .parameters(parametersBuilder.build())
                .build();
    }
}