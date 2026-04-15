package com.reveila.ai;

import com.reveila.system.PluginComponent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import com.reveila.system.Constants;

/**
 * Generic LLM Provider Implementation.
 * Encapsulates the specific LangChain4j ChatLanguageModels based on the active provider name.
 * 
 * @author CL
 */
public class GenericLlmProvider extends PluginComponent implements LlmProvider {
    private String name = Constants.LLM_PROVIDER_OPENAI; // Default
    private String apiKey;
    private String endpoint;
    private String model;
    private double temperature = 0.7;
    private boolean enabled = true;
    private ChatLanguageModel chatModel;

    public GenericLlmProvider() {
        super();
    }

    @Override
    public LlmProvider getInstance() {
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
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
        if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_OLLAMA) || name.startsWith("Gemma")) {
            return endpoint != null && !endpoint.isBlank();
        }

        String resolvedApiKey = getResolvedApiKey();
        if (resolvedApiKey == null || resolvedApiKey.isBlank() || "ERROR_DECRYPTION_FAILED".equals(resolvedApiKey)) {
            return false;
        }

        // For custom OpenAI endpoints, an endpoint must also be configured
        if (!name.equalsIgnoreCase(Constants.LLM_PROVIDER_OPENAI) && 
            !name.equalsIgnoreCase(Constants.LLM_PROVIDER_GEMINI) && 
            !name.equalsIgnoreCase(Constants.LLM_PROVIDER_ANTHROPIC) &&
            (endpoint == null || endpoint.isBlank())) {
            return false;
        }

        return true;
    }

    private String getResolvedApiKey() {
        String resolvedApiKey = apiKey;

        // Try to fetch global if not set
        if (resolvedApiKey == null || resolvedApiKey.isBlank()) {
            if (this.context != null && this.context.getProperties() != null) {
                resolvedApiKey = this.context.getProperties().getProperty("apiKey");
            }
        }

        if (resolvedApiKey != null && resolvedApiKey.startsWith("REF:")) {
            try {
                resolvedApiKey = (String) this.context.getProxy("SecretManager")
                        .invoke("getSecret", new Object[] { resolvedApiKey.substring(4) });
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

        if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_OLLAMA) || name.startsWith("Gemma")) {
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = System.getenv("OLLAMA_API_URL");
            }
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = "http://ollama-service:11434"; // Default Sandbox
            }

            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(endpoint)
                    .modelName(model != null ? model : "llama3")
                    .build();

            String msg = "Ollama connection established via LangChain4j: " + endpoint;
            System.err.println("[CRITICAL_LOG] " + msg);
            if (this.logger != null) {
                this.logger.info(msg);
            }
            return;
        }

        String resolvedApiKey = getResolvedApiKey();

        if (resolvedApiKey == null || resolvedApiKey.isBlank() || "ERROR_DECRYPTION_FAILED".equals(resolvedApiKey)) {
            throw new IllegalStateException("API Key could not be resolved for " + name);
        }

        if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_GEMINI)) {
            this.chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(resolvedApiKey)
                    .modelName(model != null ? model : "gemini-1.5-pro")
                    .temperature(0.1) // Gemini prefers lower default temperature
                    .build();

        } else if (name.equalsIgnoreCase(Constants.LLM_PROVIDER_OPENAI)) {
            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(resolvedApiKey)
                    .modelName(model != null ? model : "gpt-4")
                    .temperature(temperature)
                    .build();

        } else {
            // Treat as Custom Provider (OpenAI Compatible)
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("Endpoint is missing for custom provider: " + name);
            }
            this.chatModel = OpenAiChatModel.builder()
                    .baseUrl(endpoint)
                    .apiKey(resolvedApiKey)
                    .modelName(model != null ? model : "gpt-4")
                    .temperature(temperature)
                    .build();
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public LlmResponse invoke(LlmRequest request) throws com.reveila.error.LlmException {
        try {
            if (chatModel == null) {
                onStart();
                if (chatModel == null) {
                    throw new com.reveila.error.LlmException(name + " is not properly configured.");
                }
            }

            dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> response = chatModel.generate(request.getMessages());
            
            LlmResponse llmResponse = new LlmResponse();
            llmResponse.setContent(response.content().text());
            
            if (response.finishReason() != null) {
                llmResponse.setFinishReason(response.finishReason().name());
            }

            if (response.tokenUsage() != null) {
                Usage usage = new Usage();
                usage.setPromptTokens(response.tokenUsage().inputTokenCount());
                usage.setCompletionTokens(response.tokenUsage().outputTokenCount());
                usage.setTotalTokens(response.tokenUsage().totalTokenCount());
                llmResponse.setUsage(usage);
            }
            
            return llmResponse;
        } catch (Exception e) {
            if (this.logger != null) {
                this.logger.severe("ERROR invoking " + name + " via LangChain4j: " + e.getMessage());
                e.printStackTrace();
            }
            throw new com.reveila.error.LlmException("ERROR invoking " + name + " via LangChain4j: " + e.getMessage(), e);
        }
    }
}