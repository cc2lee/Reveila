package com.reveila.ai;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class LlmRequest {
    private final String modelId;           // e.g., "gemini-3-flash", "gpt-5-preview", "local-gemma-3"
    private final List<ReveilaMessage> messages; // System, User, Assistant, and Tool roles
    private final Double temperature;
    private final Map<String, Object> metadata; // For provider-specific extras (top_k, max_tokens)
    private final List<LlmTool> tools;      // Manifests for Agentic Tool Calling

    private LlmRequest(Builder builder) {
        this.modelId = builder.modelId;
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.metadata = builder.metadata;
        this.tools = builder.tools;
    }

    public String getModelId() {
        return modelId;
    }
    public List<ReveilaMessage> getMessages() {
        return messages;
    }
    public Double getTemperature() {
        return temperature;
    }
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    public List<LlmTool> getTools() {
        return tools;
    }
    
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String modelId;
        private List<ReveilaMessage> messages = new ArrayList<>();
        private Double temperature;
        private Map<String, Object> metadata = new HashMap<>();
        private List<LlmTool> tools = new ArrayList<>();

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder messages(List<ReveilaMessage> messages) {
            this.messages = messages != null ? messages : new ArrayList<>();
            return this;
        }

        public Builder addMessage(ReveilaMessage message) {
            if (message != null) {
                this.messages.add(message);
            }
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata != null ? metadata : new HashMap<>();
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder tools(List<LlmTool> tools) {
            this.tools = tools != null ? tools : new ArrayList<>();
            return this;
        }

        public Builder addTool(LlmTool tool) {
            if (tool != null) {
                this.tools.add(tool);
            }
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(this);
        }
    }
}