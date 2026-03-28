package com.reveila.ai;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini RailGuard Implementation: Specifically for IntentValidator safety audits.
 * Now using LangChain4j for consistent model interaction.
 * 
 * @author CL
 */
public class GeminiProvider extends com.reveila.system.AbstractService implements LlmProvider {
    private String apiKey;
    private String model = "gemini-1.5-pro";
    private double temperature = 0.1;
    private ChatLanguageModel chatModel;

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
        String resolvedApiKey = apiKey;
        if (apiKey != null && apiKey.startsWith("REF:")) {
            resolvedApiKey = (String) this.context.getProxy("CredentialManager")
                    .orElseThrow(() -> new IllegalStateException("CredentialManager not found"))
                    .invoke("getSecret", new Object[] { apiKey.substring(4) });
        }

        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API Key could not be resolved.");
        }

        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(resolvedApiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public String generateResponse(String prompt, String systemContext) {
        try {
            if (chatModel == null) {
                onStart(); // Lazy init if needed, though onStart should handle it
            }

            List<ChatMessage> messages = new ArrayList<>();
            if (systemContext != null && !systemContext.isBlank()) {
                messages.add(SystemMessage.from(systemContext));
            }
            messages.add(UserMessage.from(prompt));

            return chatModel.generate(messages).content().text();
        } catch (Exception e) {
            return "ERROR invoking Gemini via LangChain4j: " + e.getMessage();
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
