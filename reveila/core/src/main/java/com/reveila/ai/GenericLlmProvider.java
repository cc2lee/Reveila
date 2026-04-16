package com.reveila.ai;

import java.time.Duration;

import com.reveila.system.PluginComponent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Generic LLM Provider Implementation.
 * Encapsulates the specific LangChain4j ChatLanguageModels based on the active
 * provider name.
 * 
 * @author CL
 */
public class GenericLlmProvider extends PluginComponent implements LlmProvider {
    private String name = null;
    private String apiKey = null;
    private String resolvedApiKey = null;
    private String endpoint = null;
    private String model = null;
    private double temperature = 0.7;
    private String quantization = null;

    public String getQuantization() {
        return quantization;
    }

    public void setQuantization(String quantization) {
        this.quantization = quantization;
    }

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

    public boolean isLocal() {
        return name != null && name.toLowerCase().contains("(local)");
    }

    @Override
    public boolean isConfigured() {
        if (isLocal()) {
            return name != null && !name.isBlank()
                    && endpoint != null && !endpoint.isBlank();
        } else {
            return name != null && !name.isBlank()
                    && endpoint != null && !endpoint.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }

    private String resolveApiKey() throws Exception {
        if (resolvedApiKey != null && !resolvedApiKey.isBlank()) {
            return resolvedApiKey;
        }

        if (apiKey != null && apiKey.startsWith("REF:")) {
            resolvedApiKey = (String) this.context.getProxy("SecretManager")
                    .invoke("getSecret", new Object[] { resolvedApiKey.substring(4) });
        } else {
            resolvedApiKey = apiKey;
        }

        return resolvedApiKey;
    }

    @Override
    protected void onStart() throws Exception {
        if (!isEnabled() || !isConfigured()) {
            return;
        }

        if (isLocal()) {
            if (endpoint == null || endpoint.isBlank()) {
                logger.info("LLM Provider endpoint is not set. Checking OS environment variable OLLAMA_API_URL...");
                endpoint = System.getenv("OLLAMA_API_URL");
            }

            if (endpoint == null || endpoint.isBlank()) {
                logger.info(
                        "LLM Provider endpoint is not set. Neither is OS environment variable OLLAMA_API_URL. Using default endpoint: http://ollama-service:11434");
                endpoint = "http://ollama-service:11434";
            }

            // If model is blank, default to 'llama3' as a common standard for Ollama
            String activeModel = (model == null || model.isBlank()) ? "llama3" : model;

            this.chatModel = OllamaChatModel.builder()
                    .baseUrl(endpoint)
                    .modelName(activeModel)
                    .format("json") // Request JSON format for response
                    //.responseFormat(ResponseFormat.JSON)
                    .temperature(0.0) // Higher = more creative, Lower = more deterministic
                    .topP(0.9) // Nucleus sampling
                    .numPredict(100) // Roughly equivalent to maxTokens
                    .timeout(Duration.ofSeconds(60))
                    .logRequests(true) // Useful for native debugging in VS Code
                    .logResponses(true)
                    .build();

            if (this.logger != null) {
                this.logger.info("Local LLM connection established: " + endpoint + " using model: " + activeModel);
            }

            return;
        }

        resolvedApiKey = resolveApiKey();

        if (resolvedApiKey == null || resolvedApiKey.isBlank() || "ERROR_DECRYPTION_FAILED".equals(resolvedApiKey)) {
            throw new IllegalStateException("API Key could not be resolved for " + name);
        }

        if (name.toLowerCase().contains("gemini")) {
            this.chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(resolvedApiKey)
                    .modelName(model)
                    .temperature(temperature)
                    .build();

        } else if (name.toLowerCase().contains("openai")) {
            this.chatModel = OpenAiChatModel.builder()
                    .apiKey(resolvedApiKey)
                    .modelName(model)
                    .temperature(temperature)
                    .build();

        } else {
            // Treat as Custom Provider (OpenAI Compatible)
            this.chatModel = OpenAiChatModel.builder()
                    .baseUrl(endpoint)
                    .apiKey(resolvedApiKey)
                    .modelName(model)
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

            dev.langchain4j.model.output.Response<dev.langchain4j.data.message.AiMessage> response = chatModel
                    .generate(request.getMessages());

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
            throw new com.reveila.error.LlmException("ERROR invoking " + name + " via LangChain4j: " + e.getMessage(),
                    e);
        }
    }
}