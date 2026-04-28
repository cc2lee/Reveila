package com.reveila.ai;

import com.reveila.error.LlmException;

/**
 * AI Provider Interface for multi-model governance.
 * 
 * @author CL
 */
public interface LlmProvider {

    /**
     * The primary entry point for all LLM interactions within the Reveila Suite.
     * Designed to be provider-agnostic and reflection-friendly.
     */
    LlmResponse invoke(LlmRequest request) throws LlmException;
    
    /**
     * Checks if the provider is enabled by the user.
     * 
     * @return true if enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Checks if the provider is fully configured (e.g., API keys present).
     * 
     * @return true if configured, false otherwise.
     */
    boolean isConfigured();

    String getName();

}
