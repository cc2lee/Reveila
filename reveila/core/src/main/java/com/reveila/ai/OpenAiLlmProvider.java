package com.reveila.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class OpenAiLlmProvider extends BaseLlmProvider {

    public OpenAiLlmProvider() {
        super();
        this.endpoint = "https://api.openai.com/v1/chat/completions";
    }

    @Override
    protected String buildRequestBody(LlmRequest request) throws Exception {
        JSONObject body = new JSONObject();
        String activeModel = (model != null && !model.isBlank()) ? model : request.getModelId();
        
        body.put("model", activeModel);
        body.put("temperature", temperature);
        body.put("stream", false);

        JSONArray messages = new JSONArray();
        for (ReveilaMessage msg : request.getMessages()) {
            JSONObject msgJson = new JSONObject();
            msgJson.put("role", msg.role().name().toLowerCase());
            msgJson.put("content", msg.content());
            messages.put(msgJson);
        }
        body.put("messages", messages);

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            JSONArray toolsJson = new JSONArray();
            for (LlmTool tool : request.getTools()) {
                toolsJson.put(new JSONObject(tool.toJsonString()));
            }
            body.put("tools", toolsJson);
        }
        return body.toString();
    }

    @Override
    protected Map<String, String> getHeaders() throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + resolveApiKey());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    protected LlmResponse parseResponse(String json) throws Exception {
        JSONObject resp = new JSONObject(json);
        LlmResponse response = new LlmResponse();
        
        JSONArray choices = resp.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            response.setContent(message.optString("content", ""));
        }
        return response;
    }

    @Override
    public boolean isConfigured() {
        return name != null && endpoint != null && apiKey != null;
    }
}