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

    private String name; // Plugin ID, unique identifier for the plugin, e.g. "SalesforcePlugin"

    public String getName() {
        return name;
    }

    private String tenantId;

    public String getTenantId() {
        return tenantId;
    }

    private String traceId;

    public String getTraceId() {
        return traceId;
    }

    public Plugin(UUID sessionId, String name, String tenantId, String traceId) {
        super();
        this.sessionId = sessionId.toString();
        this.name = name;
        this.tenantId = tenantId;
        this.traceId = traceId;
    }

    /**
     * Creates a new AgentPrincipal with a random session ID and trace ID.
     *
     * @param name  The unique identifier for the agent.
     * @param tenantId The tenant associated with the agent.
     * @return A new AgentPrincipal instance.
     */
    public static Plugin create(String name, String tenantId) {
        return new Plugin(UUID.randomUUID(), name, tenantId, UUID.randomUUID().toString());
    }

    /**
     * Creates a child AgentPrincipal with a new session ID but inheriting the
     * parent's trace ID.
     *
     * @param name The unique identifier for the child agent.
     * @return A new AgentPrincipal instance linked by traceId.
     */
    public Plugin deriveChild(String name) {
        return new Plugin(UUID.randomUUID(), name, this.tenantId, this.traceId);
    }
}
