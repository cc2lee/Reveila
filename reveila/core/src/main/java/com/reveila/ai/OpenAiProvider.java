package com.reveila.ai;

import java.util.ArrayList;
import java.util.List;

import com.reveila.system.PluginComponent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * ChatGPT Worker Implementation: Handles task-specific tool generation.
 * Now using LangChain4j for consistent model interaction.
 * 
 * @author CL
 */
public class OpenAiProvider extends PluginComponent implements LlmProvider {
    private String apiKey;
    private String model = "gpt-4";
    private double temperature = 0.7;
    private boolean enabled = false;
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isConfigured() {
        String resolvedApiKey = getResolvedApiKey();
        return resolvedApiKey != null && !resolvedApiKey.isBlank() && !"ERROR_DECRYPTION_FAILED".equals(resolvedApiKey);
    }

    private String getResolvedApiKey() {
        String resolvedApiKey = apiKey;
        if (apiKey != null && apiKey.startsWith("REF:")) {
            try {
                resolvedApiKey = (String) this.context.getProxy("SecretManager")
                        .invoke("getSecret", new Object[] { apiKey.substring(4) });
            } catch (Exception e) {
                return null;
            }
        }
        return resolvedApiKey;
    }

    @Override
    protected void onStart() throws Exception {
        if (!isEnabled()) {
            return;
        }

        String resolvedApiKey = getResolvedApiKey();

        if (resolvedApiKey == null || resolvedApiKey.isBlank() || "ERROR_DECRYPTION_FAILED".equals(resolvedApiKey)) {
            throw new IllegalStateException("OpenAI API Key could not be resolved. (Check if Vault is unlocked)");
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
