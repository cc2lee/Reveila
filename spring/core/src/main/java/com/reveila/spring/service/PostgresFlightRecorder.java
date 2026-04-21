package com.reveila.spring.service;

import java.util.Map;

import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;

import com.reveila.ai.FlightRecorder;
import com.reveila.data.Repository;
import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.system.InvocationTarget;
import com.reveila.system.SystemComponent;

/**
 * Implementation of Forensic Auditability using PostgreSQL.
 * Persists reasoning traces and tool outputs to a secure database entity for compliance.
 * 
 * @author CL
 */
public class PostgresFlightRecorder extends SystemComponent implements FlightRecorder {

    private Repository<AuditLog, java.util.UUID> auditRepository;

    public PostgresFlightRecorder() {
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onStart() throws Exception {
        // ADR 0006: Platform-agnostic repository retrieval via DataService.
        Object repo = context.getProxy("DataService").invoke("getRepository", new Object[] { "AuditLog" });
        if (repo instanceof Repository) {
            this.auditRepository = (Repository<AuditLog, java.util.UUID>) repo;
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    @Async
    public void recordStep(InvocationTarget plugin, String stepName, Map<String, Object> data) {
        AuditLog log = createBaseLog(plugin, stepName);
        if (data != null) {
            log.setMetadata(data.toString());
        }
        if (auditRepository != null) auditRepository.store(log);
    }

    @Override
    @Async
    public void recordReasoning(InvocationTarget plugin, String reasoning) {
        AuditLog log = createBaseLog(plugin, "REASONING_TRACE");
        log.setReasoningTrace(reasoning);
        if (auditRepository != null) auditRepository.store(log);
    }

    @Override
    @Async
    public void recordToolOutput(InvocationTarget plugin, String toolName, Object output) {
        AuditLog log = createBaseLog(plugin, "TOOL_OUTPUT: " + toolName);
        if (output != null) {
            log.setMetadata(output.toString());
        }
        if (auditRepository != null) auditRepository.store(log);
    }

    @Override
    @Async
    public void recordForensicMetadata(InvocationTarget plugin, Map<String, Object> metadata) {
        AuditLog log = createBaseLog(plugin, "FORENSIC_METRICS");
        
        // ADR: Track "Who watches the watchers"
        java.util.Map<String, Object> forensicData = new java.util.HashMap<>();
        if (metadata != null) forensicData.putAll(metadata);
        
        try {
            // Check for oversight token in current request context if available
            org.springframework.web.context.request.RequestAttributes requestAttributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                jakarta.servlet.http.HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                if (request != null) {
                    Object tokenId = request.getAttribute("OVERSIGHT_TOKEN_ID");
                    if (tokenId != null) {
                        forensicData.put("oversight_token_id", tokenId);
                    }
                }
            }
        } catch (Exception e) {
            // Not in a request context, skip token enrichment
        }

        log.setMetadata(forensicData.toString());
        if (auditRepository != null) auditRepository.store(log);
    }

    private @NonNull AuditLog createBaseLog(InvocationTarget plugin, String action) {
        AuditLog log = new AuditLog();
        log.setTraceId(plugin.getTraceId());
        log.setAction(action);
        return log;
    }
}
