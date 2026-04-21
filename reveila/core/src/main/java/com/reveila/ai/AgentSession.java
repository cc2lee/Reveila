package com.reveila.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The AgentSession represents a stateful container for shared context
 * and conversation history within a single agentic workflow.
 * 
 * @author CL
 */
public class AgentSession {

    public static final String ID = "_session_id";
    public static final String THOUGHT = "_thought";
    
    private final String sessionId;
    private final String parentTraceId;
    private final ReveilaChatMemory chatMemory;
    private final Map<String, Object> context;

    /**
     * Initializes a new AgentSession with a default message window of 20.
     * 
     * @param sessionId      The unique session ID.
     * @param parentTraceId The trace ID of the parent process.
     */
    public AgentSession(String sessionId, String parentTraceId) {
        this(sessionId, parentTraceId, 20);
    }

    /**
     * Initializes a new AgentSession with a configurable message window.
     * 
     * @param sessionId      The unique session ID.
     * @param parentTraceId The trace ID of the parent process.
     * @param windowSize     The maximum number of messages to keep in memory.
     */
    public AgentSession(String sessionId, String parentTraceId, int windowSize) {
        this.sessionId = sessionId;
        this.parentTraceId = parentTraceId;
        this.chatMemory = new ReveilaChatMemory(windowSize);
        this.context = new ConcurrentHashMap<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getParentTraceId() {
        return parentTraceId;
    }

    /**
     * Provides access to the ChatMemory for this session.
     * Used to maintain the "State" across the AI Loop.
     * 
     * @return The ChatMemory instance.
     */
    public ReveilaChatMemory getChatMemory() {
        return chatMemory;
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

    /**
     * Returns the full context map for persistence or debugging.
     * 
     * @return The context map.
     */
    public Map<String, Object> getContextMap() {
        return context;
    }
}
