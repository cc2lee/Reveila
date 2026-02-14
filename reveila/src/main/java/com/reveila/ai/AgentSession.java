package com.reveila.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The AgentSession represents a stateful container for shared context
 * between multiple plugin calls within a single agentic workflow.
 * 
 * @author CL
 */
public record AgentSession(
        String sessionId,
        String parentTraceId,
        Map<String, Object> context) {
    
    public AgentSession(String sessionId, String parentTraceId) {
        this(sessionId, parentTraceId, new ConcurrentHashMap<>());
    }

    /**
     * Updates the session context with a new key-value pair.
     * 
     * @param key   The context key.
     * @param value The context value.
     */
    public void put(String key, Object value) {
        context.put(key, value);
    }

    /**
     * Retrieves a value from the session context.
     * 
     * @param key The context key.
     * @return The context value, or null if not found.
     */
    public Object get(String key) {
        return context.get(key);
    }
}
