package com.reveila.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import com.reveila.service.HttpClientService;
import java.util.HashMap;
import java.util.Map;

/**
 * Native Google Gemini Provider Implementation.
 * Supports both OpenAI-compatible and native Gemini Interaction endpoints.
 */
public class GeminiLlmProvider extends GenericLlmProvider {

    public GeminiLlmProvider() {
        super();
    }

    @Override
    public LlmResponse invoke(LlmRequest request) throws com.reveila.error.LlmException {
        try {
            HttpClientService httpService = getHttpClientService();
            if (httpService == null) {
                throw new com.reveila.error.LlmException("HttpClientService not available");
            }

            String activeApiKey = resolveApiKey();
            String activeModel = (getModel() != null && !getModel().isBlank()) ? getModel() : request.getModelId();
            
            // Auto-repair model for Gemini 3
            if (activeModel != null && (activeModel.equals("gemini-1.5-pro") || activeModel.equals("gemini-1.5-pro-latest") || activeModel.equals("gemini-2.0-flash"))) {
                activeModel = "gemini-3-flash";
            }

            String url = getEndpoint();
            if (url == null || url.isBlank()) url = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

            boolean isInteractionApi = url.contains("interactions");

            JSONObject body = new JSONObject();
            body.put("model", activeModel);
            
            if (isInteractionApi) {
                JSONArray input = new JSONArray();
                for (ReveilaMessage msg : request.getMessages()) {
                    JSONObject msgJson = new JSONObject();
                    String role = msg.role().name().toLowerCase();
                    if (role.equals("assistant")) role = "model";
                    msgJson.put("role", role);
                    msgJson.put("content", msg.content());
                    input.put(msgJson);
                }
                body.put("input", input);
            } else {
                body.put("temperature", getTemperature());
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
                        } catch (Exception e) {}
                    }
                    body.put("tools", toolsJson);
                }
            }

            Map<String, String> headers = new HashMap<>();
            if (activeApiKey != null && !activeApiKey.isBlank()) {
                headers.put("x-goog-api-key", activeApiKey);
            }
            headers.put("Content-Type", "application/json");

            String responseJson = httpService.invokeRest(url, "POST", body.toString(), HttpClientService.JSON, headers);
            return parseResponse(responseJson);

        } catch (Exception e) {
            throw new com.reveila.error.LlmException("ERROR invoking Gemini: " + e.getMessage(), e);
        }
    }
}
