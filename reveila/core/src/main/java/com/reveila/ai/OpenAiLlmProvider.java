package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.reveila.error.LlmException;

public class OpenAiLlmProvider extends BaseLlmProvider {

    public OpenAiLlmProvider() {
        super();
        this.endpoint = "https://api.openai.com/v1/chat/completions";
    }

    @Override
    protected String buildRequestBody(LlmRequest request) throws LlmException {
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
                try {
                    toolsJson.put(new JSONObject(tool.toJsonString()));
                } catch (Exception e) {
                    throw new LlmException("Failed to serialize tool: " + e.getMessage(), e);
                }
            }
            body.put("tools", toolsJson);
        }
        return body.toString();
    }

    @Override
    protected Map<String, String> getHeaders() throws LlmException {
        Map<String, String> headers = new HashMap<>();
        try {
            headers.put("Authorization", "Bearer " + resolveApiKey());
        } catch (Exception e) {
            throw new LlmException("Failed to resolve API key for OpenAI provider: " + e.getMessage(), e);
        }
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    protected LlmResponse parseResponse(String json) throws LlmException {
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