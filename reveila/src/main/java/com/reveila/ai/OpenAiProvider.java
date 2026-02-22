package com.reveila.ai;

/**
 * ChatGPT Worker Implementation: Handles task-specific tool generation.
 * 
 * @author CL
 */
public class OpenAiProvider implements LlmProvider {
    private String apiKey;
    private String model = "gpt-4";

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
        // Real-world implementation would use OpenAi SDK/Retrofit here.
        // For this implementation, we simulate the LLM's response generation for tools.
        return "SIMULATED_OPENAI_RESPONSE (Model: " + model + "): Tool call generated for " + prompt;
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        // The 'Specialized Worker' generates complex reasoning and tool_call arguments.
        return "{\"intent\": \"doc_extraction.extract\", \"arguments\": {\"document_type\": \"SEC Filing\"}, \"_thought\": \"Extracting liabilities as requested.\"}";
    }
}
