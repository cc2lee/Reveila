package com.reveila.spring.service;

import java.util.Map;

import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;

import com.reveila.ai.AgentPrincipal;
import com.reveila.ai.FlightRecorder;
import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;

/**
 * Phase 4: Long-term Forensic Auditability using PostgreSQL.
 * Persists reasoning traces and tool outputs to a secure database entity for compliance.
 * 
 * @author CL
 */
public class PostgresFlightRecorder extends com.reveila.system.AbstractService implements FlightRecorder {

    private JdbcAuditLogRepository auditRepository;

    public PostgresFlightRecorder() {
    }

    @Override
    protected void onStart() throws Exception {
        // ADR 0006: Platform-agnostic repository retrieval.
        // We cast to our shared functional interface or a platform-specific helper.
        Object repo = systemContext.getPlatformAdapter().getRepository("AuditLog");
        if (repo instanceof JdbcAuditLogRepository) {
            this.auditRepository = (JdbcAuditLogRepository) repo;
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    @Async
    public void recordStep(AgentPrincipal principal, String stepName, Map<String, Object> data) {
        AuditLog log = createBaseLog(principal, stepName);
        if (data != null) {
            log.setMetadata(data.toString());
        }
        auditRepository.save(log);
    }

    @Override
    @Async
    public void recordReasoning(AgentPrincipal principal, String reasoning) {
        AuditLog log = createBaseLog(principal, "REASONING_TRACE");
        log.setReasoningTrace(reasoning);
        auditRepository.save(log);
    }

    @Override
    @Async
    public void recordToolOutput(AgentPrincipal principal, String toolName, Object output) {
        AuditLog log = createBaseLog(principal, "TOOL_OUTPUT: " + toolName);
        if (output != null) {
            log.setMetadata(output.toString());
        }
        auditRepository.save(log);
    }

    @Override
    @Async
    public void recordForensicMetadata(AgentPrincipal principal, Map<String, Object> metadata) {
        AuditLog log = createBaseLog(principal, "FORENSIC_METRICS");
        
        // ADR: Track "Who watches the watchers"
        java.util.Map<String, Object> forensicData = new java.util.HashMap<>();
        if (metadata != null) forensicData.putAll(metadata);
        
        try {
            // Check for oversight token in current request context if available
            jakarta.servlet.http.HttpServletRequest request = ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest();
            Object tokenId = request.getAttribute("OVERSIGHT_TOKEN_ID");
            if (tokenId != null) {
                forensicData.put("oversight_token_id", tokenId);
            }
        } catch (Exception e) {
            // Not in a request context, skip token enrichment
        }

        log.setMetadata(forensicData.toString());
        auditRepository.save(log);
    }

    private @NonNull AuditLog createBaseLog(AgentPrincipal principal, String action) {
        AuditLog log = new AuditLog();
        log.setTraceId(principal.traceId());
        log.setAction(action);
        return log;
    }
}
