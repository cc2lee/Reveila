package com.reveila.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 5: Stateful Context Persistence.
 * Manages agent sessions and histories.
 * In a real-world scenario, this would use Redis or MongoDB.
 * 
 * @author CL
 */
public class AgentSessionManager {
    private final Map<String, Map<String, Object>> sessionStore = new ConcurrentHashMap<>();

    /**
     * Persists context for a specific trace.
     *
     * @param traceId The trace ID.
     * @param context The context map.
     */
    public void saveContext(String traceId, Map<String, Object> context) {
        sessionStore.put(traceId, context);
    }

    /**
     * Retrieves context for a specific trace.
     *
     * @param traceId The trace ID.
     * @return The context map, or a new empty map if none exists.
     */
    public Map<String, Object> getContext(String traceId) {
        return sessionStore.getOrDefault(traceId, new ConcurrentHashMap<>());
    }

    /**
     * Clears session context after completion.
     */
    public void clear(String traceId) {
        sessionStore.remove(traceId);
    }
}
