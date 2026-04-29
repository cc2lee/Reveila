package com.reveila.ai;

import com.reveila.error.LlmException;
import com.reveila.system.Startable;
import com.reveila.system.Stoppable;

public interface LlmProvider extends Startable, Stoppable{

    LlmResponse invoke(LlmRequest request) throws LlmException;

    boolean isEnabled();

    boolean isConfigured();

    String getName();

}