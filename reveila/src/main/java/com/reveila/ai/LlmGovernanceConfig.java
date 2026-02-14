package com.reveila.ai;

/**
 * Configuration for LLM multi-model governance roles.
 * 
 * @author CL
 */
public record LlmGovernanceConfig(String workerProvider, String guardrailProvider) {
    /**
     * Default configuration with OpenAI as worker and Gemini as guardrail.
     */
    public static LlmGovernanceConfig defaultGov() {
        return new LlmGovernanceConfig("openai", "gemini");
    }
}
