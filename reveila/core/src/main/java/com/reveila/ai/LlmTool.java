package com.reveila.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import com.reveila.util.json.JsonException;
import com.reveila.util.json.JsonUtil;

public class LlmTool {

    private String name;
    private String description;
    private Map<String, Object> parameterSchema;
    private Map<String, Object> reveilaMetadata = new HashMap<>();

    public LlmTool() {
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setParameterSchema(JSONObject schema) {
        this.parameterSchema = schema.toMap();
    }

    public void setReveilaMetadata(String tier, List<String> humanInTheLoop, List<String> accessScopes) {
        reveilaMetadata.put("tier", tier);
        reveilaMetadata.put("human_in_the_loop", humanInTheLoop != null ? humanInTheLoop : new ArrayList<>());
        reveilaMetadata.put("access_scopes", accessScopes != null ? accessScopes : new ArrayList<>());
    }

    public String toJsonString() throws JsonException {
        Map<String, Object> functionMap = new HashMap<>();
        functionMap.put("name", name);
        functionMap.put("description", description);
        if (parameterSchema != null) {
            functionMap.put("parameters", parameterSchema);
        }

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("type", "function");
        rootMap.put("function", functionMap);
        
        if (!reveilaMetadata.isEmpty()) {
            rootMap.put("reveila_metadata", reveilaMetadata);
        }

        return JsonUtil.toJsonString(rootMap);
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool Name: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n");
        if (parameterSchema != null) {
            sb.append("Parameters Schema:\n");
            sb.append(new JSONObject(parameterSchema).toString(2)).append("\n");
        }
        if (!reveilaMetadata.isEmpty()) {
            sb.append("Reveila Metadata:\n");
            sb.append(new JSONObject(reveilaMetadata).toString(2)).append("\n");
        }
        return sb.toString();
    }
}
