package com.reveila.system;

import java.util.UUID;

/**
 * Assigns non-person entity (NPE) identities to each agent session.
 * Ensures "Least Privilege" access to enterprise data sources.
 * 
 * @author CL
 */
public class Plugin {

    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    private String targetName;

    public String getTargetName() {
        return targetName;
    }

    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    private String traceId;

    public String getTraceId() {
        return traceId;
    }

    public Plugin(UUID sessionId, String targetName, String tenantId, String traceId) {
        super();
        this.sessionId = sessionId.toString();
        this.targetName = targetName;
        this.tenantId = tenantId;
        this.traceId = traceId;
    }

    /**
     * Creates a new AgentPrincipal with a random session ID and trace ID.
     *
     * @param targetName  The unique identifier for the agent.
     * @param tenantId The tenant associated with the agent.
     * @return A new AgentPrincipal instance.
     */
    public static Plugin create(String targetName, String tenantId) {
        return new Plugin(UUID.randomUUID(), targetName, tenantId, UUID.randomUUID().toString());
    }

    /**
     * Creates a child AgentPrincipal with a new session ID but inheriting the
     * parent's trace ID.
     *
     * @param targetName The unique identifier for the child agent.
     * @return A new AgentPrincipal instance linked by traceId.
     */
    public Plugin deriveChild(String targetName) {
        return new Plugin(UUID.randomUUID(), targetName, this.tenantId, this.traceId);
    }
}
