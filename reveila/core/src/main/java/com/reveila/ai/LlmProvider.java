package com.reveila.ai;

public interface LlmProvider {

    LlmResponse invoke(LlmRequest request) throws LlmException;

    boolean isEnabled();

    boolean isConfigured();

    String getName();

}