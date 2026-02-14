package com.reveila.spring.service;

import com.reveila.ai.AgentPrincipal;
import com.reveila.ai.FlightRecorder;
import com.reveila.spring.model.jpa.AuditLog;
import com.reveila.spring.repository.jpa.JdbcAuditLogRepository;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Phase 4: Long-term Forensic Auditability using PostgreSQL.
 * Persists reasoning traces and tool outputs to a secure database entity for compliance.
 * 
 * @author CL
 */
@Service
public class PostgresFlightRecorder implements FlightRecorder {

    private final JdbcAuditLogRepository auditRepository;

    public PostgresFlightRecorder(JdbcAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
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
        if (metadata != null) {
            log.setMetadata(metadata.toString());
        }
        auditRepository.save(log);
    }

    private @NonNull AuditLog createBaseLog(AgentPrincipal principal, String action) {
        AuditLog log = new AuditLog();
        log.setTraceId(principal.traceId());
        log.setAction(action);
        return log;
    }
}
