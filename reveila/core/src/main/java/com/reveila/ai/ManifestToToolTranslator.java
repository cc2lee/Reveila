package com.reveila.ai;

import org.json.JSONArray;
import org.json.JSONObject;

public class ManifestToToolTranslator {

    public static LlmTool translate(JSONObject manifest) {
        JSONObject plugin = manifest.getJSONObject("plugin");
        JSONArray methods = plugin.getJSONArray("methods");

        // For this example, we take the primary method (echo)
        JSONObject targetMethod = methods.getJSONObject(0);

        LlmTool tool = new LlmTool();
        tool.setName(plugin.getString("name") + "_" + targetMethod.getString("name"));
        tool.setDescription(targetMethod.getString("description"));

        // Build the JSON Schema for parameters
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        
        JSONObject properties = new JSONObject();
        JSONArray params = targetMethod.getJSONArray("parameters");
        
        for (int i = 0; i < params.length(); i++) {
            JSONObject p = params.getJSONObject(i);
            JSONObject pSchema = new JSONObject();
            pSchema.put("type", mapJavaToJsonType(p.getString("type")));
            pSchema.put("description", p.getString("description"));
            properties.put(p.getString("name"), pSchema);
        }
        
        schema.put("properties", properties);
        tool.setParameterSchema(schema);

        return tool;
    }

    private static String mapJavaToJsonType(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "string";
            case "int", "java.lang.Integer" -> "integer";
            case "boolean", "java.lang.Boolean" -> "boolean";
            default -> "string";
        };
    }
}