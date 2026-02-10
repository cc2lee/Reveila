package com.reveila.ai;

import java.util.Map;

/**
 * Model-generated arguments are strictly validated against JSON schemas 
 * before being passed to the plugin.
 * 
 * @author CL
 */
public interface SchemaEnforcer {
    /**
     * Validates and cleans raw arguments against the plugin's schema.
     *
     * @param pluginId The target plugin ID.
     * @param rawArguments The arguments to validate.
     * @return A map of validated and casted arguments.
     * @throws IllegalArgumentException If schema validation fails.
     */
    Map<String, Object> enforce(String pluginId, Map<String, Object> rawArguments);
}
