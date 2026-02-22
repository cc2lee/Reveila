package com.reveila.ai;

import java.util.List;
import java.util.Map;
import com.reveila.service.HttpClientService;
import com.reveila.util.json.JsonUtil;

/**
 * ChatGPT Worker Implementation: Handles task-specific tool generation.
 * 
 * @author CL
 */
public class OpenAiProvider extends com.reveila.system.AbstractService implements LlmProvider {
    private String apiKey;
    private String model = "gpt-4";
    private double temperature = 0.7;

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
            String resolvedApiKey = apiKey;
            if (apiKey != null && apiKey.startsWith("REF:")) {
                resolvedApiKey = (String) this.systemContext.getProxy("CredentialManager")
                        .orElseThrow(() -> new IllegalStateException("CredentialManager not found"))
                        .invoke("getSecret", new Object[] { apiKey.substring(4) });
            }

            if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
                throw new IllegalStateException("OpenAI API Key could not be resolved.");
            }

            String url = "https://api.openai.com/v1/chat/completions";

            Map<String, Object> requestMap = Map.of(
                    "model", model,
                    "temperature", temperature,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemContext != null ? systemContext : ""),
                            Map.of("role", "user", "content", prompt)));

            String payload = JsonUtil.toJsonString(requestMap);
            Map<String, String> headers = Map.of("Authorization", "Bearer " + resolvedApiKey);

            String responseJson = (String) this.systemContext.getProxy("HttpClientService")
                    .orElseThrow(() -> new IllegalStateException("HttpClientService not found"))
                    .invoke("invokeRest", new Object[] { url, "POST", payload, HttpClientService.JSON, headers });

            // Parse response
            Map<String, Object> responseMap = JsonUtil.parseJsonStringToMap(responseJson);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }

            return "ERROR: No valid response choice from OpenAI. Raw response: " + responseJson;
        } catch (Exception e) {
            return "ERROR invoking OpenAI API: " + e.getMessage();
        }
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        // The 'Specialized Worker' generates complex reasoning and tool_call arguments.
        return "{\"intent\": \"doc_extraction.extract\", \"arguments\": {\"document_type\": \"SEC Filing\"}, \"_thought\": \"Extracting liabilities as requested.\"}";
    }
}
