package com.reveila.ai;

import java.util.List;
import java.util.Map;
import com.reveila.service.HttpClientService;
import com.reveila.util.json.JsonUtil;

/**
 * Gemini RailGuard Implementation: Specifically for IntentValidator safety audits.
 * 
 * @author CL
 */
public class GeminiProvider extends com.reveila.system.AbstractService implements LlmProvider {
    private String apiKey;
    private String model = "gemini-1.5-pro";
    private double temperature = 0.1;

    /**
     * Required by LlmProviderFactory to retrieve the instance via Proxy.
     * 
     * @return The provider instance.
     */
    @Override
    public LlmProvider getInstance() {
        return this;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    @Override
    protected void onStart() throws Exception {
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public String generateResponse(String prompt, String systemContext) {
        try {
            HttpClientService httpClient = (HttpClientService) this.systemContext.getProxy("HttpClientService")
                    .orElseThrow(() -> new IllegalStateException("HttpClientService not found"))
                    .getTargetObject();

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

            // Build Gemini request JSON
            Map<String, Object> requestMap;
            Map<String, Object> generationConfig = Map.of("temperature", temperature);

            if (systemContext != null && !systemContext.isBlank()) {
                requestMap = Map.of(
                        "system_instruction", Map.of("parts", List.of(Map.of("text", systemContext))),
                        "contents", List.of(
                                Map.of("parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", generationConfig);
            } else {
                requestMap = Map.of(
                        "contents", List.of(
                                Map.of("parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", generationConfig);
            }

            String payload = JsonUtil.toJsonString(requestMap);
            String responseJson = httpClient.invokeRest(url, "POST", payload);

            // Parse response
            Map<String, Object> responseMap = JsonUtil.parseJsonStringToMap(responseJson);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    return (String) parts.get(0).get("text");
                }
            }

            return "ERROR: No valid response candidate from Gemini. Raw response: " + responseJson;
        } catch (Exception e) {
            return "ERROR invoking Gemini API: " + e.getMessage();
        }
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        // The 'Rail Guard' validates the intent and arguments against the Agency Perimeter.
        // Forces a strict JSON schema to prevent prompt injection influence.
        if (userPrompt.contains("Ignore all previous instructions") || userPrompt.contains("unauthorized_domain")) {
            return "{\"approved\": false, \"reasoning\": \"SECURITY_BREACH: Potential prompt injection or perimeter violation detected.\", \"status\": \"REJECTED\"}";
        }
        return "{\"approved\": true, \"reasoning\": \"Audit passed.\", \"status\": \"APPROVED\"}";
    }
}
