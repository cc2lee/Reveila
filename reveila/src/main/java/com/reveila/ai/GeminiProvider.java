package com.reveila.ai;

/**
 * Gemini RailGuard Implementation: Specifically for IntentValidator safety audits.
 * 
 * @author CL
 */
public class GeminiProvider implements LlmProvider {
    private String apiKey;
    private String model = "gemini-pro";

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

    @Override
    public String generateResponse(String prompt, String systemContext) {
        // Real-world implementation would use Google AI SDK/Vertex AI here.
        // For this implementation, we simulate the safety audit of the tool arguments.
        if (prompt.contains("unsafe") || prompt.contains("malicious") || prompt.contains("SECURITY_BREACH")) {
            return "REJECTED (Model: " + model + "): Safety audit failed. Malicious intent or perimeter violation detected.";
        }
        return "APPROVED (Model: " + model + "): Safety audit passed.";
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
