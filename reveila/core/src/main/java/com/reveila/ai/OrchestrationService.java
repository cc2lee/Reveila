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
     * Returns the maximum number of messages allowed in a single session.
     */
    public int getMaxMessages() {
        String max = context.getProperties().getProperty("ai.session.maxMessages");
        if (max != null && !max.isBlank()) {
            try {
                return Integer.parseInt(max);
            } catch (Exception e) {}
        }
        // Default based on optimization priority
        return "cost".equalsIgnoreCase(optimizationPriority) ? 10 : 50;
    }

    /**
     * Creates a new AgentSession with settings derived from system properties.
     * 
     * @param sessionId Optional preferred session ID. If null, a random UUID will be generated.
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    public AgentSession createSession(String sessionId, String parentTraceId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        
        AgentSession session = new AgentSession(sessionId, parentTraceId, getMaxMessages());
        sessions.put(sessionId, session);
        return session;
    }

    /**
     * Creates a new AgentSession with a random UUID.
     * 
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    public AgentSession createSession(String parentTraceId) {
        return createSession(null, parentTraceId);
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
    
    /**
     * Returns a list of active sessions for the dashboard.
     */
    public java.util.List<java.util.Map<String, Object>> getActiveSessions() {
        return sessions.values().stream()
            .filter(session -> session.getChatMemory().messages().size() > 0)
            .map(session -> {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", session.getSessionId());
            
            // find the first user message for title
            String title = "Session";
            for (com.reveila.ai.ReveilaMessage msg : session.getChatMemory().messages()) {
                if (com.reveila.ai.LlmRole.USER.equals(msg.role())) {
                    title = msg.content();
                    if (title.length() > 30) title = title.substring(0, 30) + "...";
                    break;
                }
            }
            map.put("title", title);
            map.put("messageCount", session.getChatMemory().messages().size());
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns the chat history for a specific session.
     */
    public java.util.List<java.util.Map<String, String>> getSessionHistory(String sessionId) {
        AgentSession session = getSession(sessionId);
        if (session == null) return java.util.Collections.emptyList();
        
        return session.getChatMemory().messages().stream().map(msg -> {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("role", msg.role().name());
            String content = msg.content();
            if (com.reveila.ai.LlmRole.ASSISTANT.equals(msg.role())) {
                try {
                    String cleaned = com.reveila.util.json.JsonUtil.clean(content);
                    if (cleaned.startsWith("{")) {
                        content = com.reveila.ai.AgenticFabric.interpretAiResponse(new org.json.JSONObject(cleaned));
                    }
                } catch (Exception e) {
                    // Not JSON or parse failed, keep original content
                }
            }
            map.put("content", content);
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }
    
    public String getOptimizationPriority() {
        return optimizationPriority;
    }
}
