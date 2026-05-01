package com.reveila.ai;

import com.reveila.error.LlmException;

public interface LlmProvider {

    LlmResponse invoke(LlmRequest request) throws LlmException;

    boolean isEnabled();

    boolean isConfigured();

    String getName();

}