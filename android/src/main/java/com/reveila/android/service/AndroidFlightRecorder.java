package com.reveila.android.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.SystemComponent;
import com.reveila.system.InvocationTarget;
import com.reveila.ai.FlightRecorder;

/**
 * Platform-agnostic implementation of FlightRecorder.
 * Uses the platform's Repository to persist reasoning traces and tool outputs.
 * This ensures that on Android it uses SQLite/Room (or JSON fallback) and on
 * the Backend it uses PostgreSQL, without platform-specific code in the logic
 * layer.
 * 
 * @author CL
 */
public class AndroidFlightRecorder extends SystemComponent implements FlightRecorder {

    private Repository<Entity, Map<String, Map<String, Object>>> auditRepository;

    @Override
    protected void onStart() throws Exception {
        // ADR 0006: Platform-agnostic repository retrieval via DataService.
        Object repo = context.getProxy("DataService").invoke("getRepository", new Object[] { "AuditLog" });
        if (repo instanceof Repository) {
            this.auditRepository = (Repository<Entity, Map<String, Map<String, Object>>>) repo;
        } else {
            logger.warning("AuditLog repository not found or invalid type via DataService. FlightRecorder will not persist data.");
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    public void recordStep(InvocationTarget plugin, String stepName, Map<String, Object> data) {
        Entity log = createBaseLog(plugin, stepName);
        if (data != null) {
            log.getAttributes().put("metadata", data.toString());
        }
        store(log);
    }

    public void recordReasoning(InvocationTarget plugin, String reasoning) {
        Entity log = createBaseLog(plugin, "REASONING_TRACE");
        log.getAttributes().put("inner_monologue", reasoning);
        store(log);
    }

    public void recordToolOutput(InvocationTarget plugin, String toolName, Object output) {
        Entity log = createBaseLog(plugin, "TOOL_OUTPUT: " + toolName);
        if (output != null) {
            log.getAttributes().put("metadata", output.toString());
        }
        store(log);
    }

    public void recordForensicMetadata(InvocationTarget plugin, Map<String, Object> metadata) {
        Entity log = createBaseLog(plugin, "FORENSIC_METRICS");
        if (metadata != null) {
            log.getAttributes().put("metadata", metadata.toString());
        }
        store(log);
    }

    private Entity createBaseLog(InvocationTarget plugin, String action) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("target_id", plugin.getTraceId());
        attributes.put("proposed_action", action);
        attributes.put("timestamp", Instant.now().toString());

        // Setup the key for the entity
        Map<String, Map<String, Object>> key = new HashMap<>();
        Map<String, Object> idValue = new HashMap<>();
        idValue.put("value", UUID.randomUUID().toString());
        key.put("id", idValue);

        return new Entity("AuditLog", key, attributes);
    }

    private void store(Entity entity) {
        if (auditRepository != null) {
            try {
                auditRepository.store(entity);
            } catch (Exception e) {
                logger.severe("Failed to store audit log: " + e.getMessage());
            }
        } else {
            // Fallback to S3FlightRecorder style logging if repo is missing
            logger.info("[AUDIT-FALLBACK] " + entity.getAttributes());
        }
    }
}
