package com.reveila.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.reveila.system.SystemComponent;

/**
 * OrchestrationService manages AgentSessions and coordinates multi-agent workflows.
 * Optimized to handle context windows based on system configuration.
 * 
 * @author CL
 */
public class OrchestrationService extends SystemComponent {
    
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private String optimizationPriority = "cost";

    public OrchestrationService() {
    }

    @Override
    public void onStart() throws Exception {
        this.optimizationPriority = context.getProperties().getProperty("ai.optimization.priority", "cost");
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Creates a new AgentSession with settings derived from system properties.
     * 
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    public AgentSession createSession(String parentTraceId) {
        String sessionId = UUID.randomUUID().toString();
        // Window size depends on optimization priority: cost (10) vs quality (50)
        int windowSize = "cost".equalsIgnoreCase(optimizationPriority) ? 10 : 50;
        
        AgentSession session = new AgentSession(sessionId, parentTraceId, windowSize);
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
    
    public String getOptimizationPriority() {
        return optimizationPriority;
    }
}
