package com.reveila.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.reveila.system.SystemComponent;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Phase 5: Stateful Context Persistence.
 * Manages agent sessions and histories with automated size-based eviction.
 * 
 * Refactored to enforce ai.optimization.history limits as per ADR 0014.
 * 
 * @author CL
 */
public class AgentSessionManager extends SystemComponent {
    
    // Using synchronized LinkedHashMap for basic LRU eviction support
    private final Map<String, AgentSession> sessionStore = Collections.synchronizedMap(
        new LinkedHashMap<String, AgentSession>(16, 0.75f, true)
    );
    
    private long maxHistorySizeBytes = Long.MAX_VALUE;

    @Override
    public void onStart() throws Exception {
        String historyLimit = context.getProperties().getProperty("ai.optimization.history", "unlimited");
        if (historyLimit != null && !"unlimited".equalsIgnoreCase(historyLimit)) {
            try {
                if (historyLimit.toUpperCase().endsWith("MB")) {
                    maxHistorySizeBytes = Long.parseLong(historyLimit.substring(0, historyLimit.length() - 2)) * 1024 * 1024;
                } else if (historyLimit.toUpperCase().endsWith("KB")) {
                    maxHistorySizeBytes = Long.parseLong(historyLimit.substring(0, historyLimit.length() - 2)) * 1024;
                } else {
                    maxHistorySizeBytes = Long.parseLong(historyLimit);
                }
            } catch (NumberFormatException e) {
                if (this.logger != null) {
                    this.logger.warning("Invalid ai.optimization.history format: " + historyLimit + ". Using unlimited.");
                }
            }
        }
    }

    @Override
    protected void onStop() throws Exception {
        sessionStore.clear();
    }

    /**
     * Persists a session and enforces the total history size limit.
     *
     * @param id      The session or trace ID.
     * @param session The AgentSession instance.
     */
    public void saveSession(String id, AgentSession session) {
        long newSessionSize = estimateSessionSize(session);
        
        synchronized (sessionStore) {
            long currentTotalSize = calculateTotalSize();
            
            // Evict oldest sessions until we are within the limit
            while (currentTotalSize + newSessionSize > maxHistorySizeBytes && !sessionStore.isEmpty()) {
                String oldestId = sessionStore.keySet().iterator().next();
                AgentSession removed = sessionStore.remove(oldestId);
                if (removed != null) {
                    currentTotalSize -= estimateSessionSize(removed);
                    if (this.logger != null) {
                        this.logger.info("Evicted session " + oldestId + " to respect history size limit.");
                    }
                }
            }
            sessionStore.put(id, session);
        }
    }

    public AgentSession getSession(String id) {
        return sessionStore.get(id);
    }

    public void clear(String id) {
        sessionStore.remove(id);
    }

    private long calculateTotalSize() {
        return sessionStore.values().stream()
                .mapToLong(this::estimateSessionSize)
                .sum();
    }

    /**
     * Estimates the memory footprint of a session based on message content.
     * 1 character is estimated as 2 bytes.
     */
    private long estimateSessionSize(AgentSession session) {
        if (session == null) return 0;
        
        long size = 0;
        // 1. Messages size
        if (session.getChatMemory() != null) {
            for (ChatMessage msg : session.getChatMemory().messages()) {
                String text = getMessageText(msg);
                if (text != null) {
                    size += text.length() * 2L;
                }
            }
        }
        // 2. Context map size
        for (Map.Entry<String, Object> entry : session.getContextMap().entrySet()) {
            size += entry.getKey().length() * 2L;
            if (entry.getValue() instanceof String s) {
                size += s.length() * 2L;
            } else {
                size += 128; // Constant overhead for other objects
            }
        }
        return size;
    }

    private String getMessageText(ChatMessage msg) {
        if (msg instanceof dev.langchain4j.data.message.UserMessage user) {
            return user.contents().stream()
                    .filter(c -> c instanceof dev.langchain4j.data.message.TextContent)
                    .map(c -> ((dev.langchain4j.data.message.TextContent) c).text())
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        if (msg instanceof dev.langchain4j.data.message.AiMessage ai) return ai.text();
        if (msg instanceof dev.langchain4j.data.message.SystemMessage sys) return sys.text();
        if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage tool) return tool.text();
        return null;
    }

    /**
     * Legacy support: Persists context for a specific trace.
     */
    public void saveContext(String traceId, Map<String, Object> context) {
        AgentSession session = new AgentSession(traceId, traceId);
        if (context != null) {
            context.forEach(session::put);
        }
        saveSession(traceId, session);
    }

    /**
     * Legacy support: Retrieves context for a specific trace.
     */
    public Map<String, Object> getContext(String traceId) {
        AgentSession session = getSession(traceId);
        return session != null ? session.getContextMap() : new ConcurrentHashMap<>();
    }
}
