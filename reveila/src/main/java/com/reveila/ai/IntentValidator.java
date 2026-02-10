package com.reveila.ai;

/**
 * Validates that an agent's intent maps to a registered, metadata-defined plugin.
 * 
 * @author CL
 */
public interface IntentValidator {
    /**
     * Maps an intent to a plugin ID.
     *
     * @param intent The intent to validate.
     * @return The corresponding plugin ID.
     * @throws IllegalArgumentException If the intent is unauthorized or unknown.
     */
    String validateIntent(String intent);
}
