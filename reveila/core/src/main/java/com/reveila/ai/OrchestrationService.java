package com.reveila.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OrchestrationService manages AgentSessions and coordinates multi-agent workflows.
 * 
 * @author CL
 */
public class OrchestrationService {
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a new AgentSession.
     * 
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    public AgentSession createSession(String parentTraceId) {
        String sessionId = UUID.randomUUID().toString();
        AgentSession session = new AgentSession(sessionId, parentTraceId);
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Retrieves an existing AgentSession.
     * 
     * @param sessionId The ID of the session to retrieve.
     * @return The AgentSession, or null if not found.
     */
    public AgentSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Closes and removes an AgentSession.
     * 
     * @param sessionId The ID of the session to close.
     */
    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
