package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

public class GeminiLlmProvider extends OpenAiLlmProvider {

    public GeminiLlmProvider() {
        super();
        this.endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    }

    @Override
    protected String buildRequestBody(LlmRequest request) throws Exception {
        // We can leverage most of OpenAI's logic, but we need to intercept the model ID
        String originalModel = model;
        
        // Auto-repair model for Gemini 3 as per your legacy code
        String activeModel = (model != null && !model.isBlank()) ? model : request.getModelId();
        if (activeModel != null && (activeModel.equals("gemini-1.5-pro") || 
            activeModel.equals("gemini-1.5-pro-latest") || 
            activeModel.equals("gemini-2.0-flash"))) {
            this.model = "gemini-3-flash";
        }

        String body = super.buildRequestBody(request);
        
        // Restore original model state if necessary to avoid side effects
        this.model = originalModel; 
        
        return body;
    }

    @Override
    protected Map<String, String> getHeaders() throws Exception {
        Map<String, String> headers = new HashMap<>();
        String key = resolveApiKey();
        
        // Google supports both Bearer and a custom header, 
        // but your legacy code used Bearer for the OpenAI-compatible endpoint.
        headers.put("Authorization", "Bearer " + key);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}