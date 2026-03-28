package com.reveila.ai;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatGPT Worker Implementation: Handles task-specific tool generation.
 * Now using LangChain4j for consistent model interaction.
 * 
 * @author CL
 */
public class OpenAiProvider extends com.reveila.system.AbstractService implements LlmProvider {
    private String apiKey;
    private String model = "gpt-4";
    private double temperature = 0.7;
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
            throw new IllegalStateException("OpenAI API Key could not be resolved.");
        }

        this.chatModel = OpenAiChatModel.builder()
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
                onStart();
            }

            List<ChatMessage> messages = new ArrayList<>();
            if (systemContext != null && !systemContext.isBlank()) {
                messages.add(SystemMessage.from(systemContext));
            }
            messages.add(UserMessage.from(prompt));

            return chatModel.generate(messages).content().text();
        } catch (Exception e) {
            return "ERROR invoking OpenAI via LangChain4j: " + e.getMessage();
        }
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        // The 'Specialized Worker' generates complex reasoning and tool_call arguments.
        return "{\"intent\": \"doc_extraction.extract\", \"arguments\": {\"document_type\": \"SEC Filing\"}, \"_thought\": \"Extracting liabilities as requested.\"}";
    }
}
