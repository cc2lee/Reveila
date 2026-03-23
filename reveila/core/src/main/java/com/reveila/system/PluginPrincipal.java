package com.reveila.system;

import java.util.UUID;

/**
 * Assigns non-person entity (NPE) identities to each agent session.
 * Ensures "Least Privilege" access to enterprise data sources.
 * 
 * @author CL
 */
public class PluginPrincipal extends RolePrincipal {

    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    private String agentId;
    public String getAgentId() {
        return agentId;
    }

    private String tenantId;
    public String getTenantId() {
        return tenantId;
    }

    private String traceId;
    public String getTraceId() {
        return traceId;
    }

    public PluginPrincipal(UUID sessionId, String agentId, String tenantId, String traceId) {
        super(Constants.PLUGIN);
        this.sessionId = sessionId.toString();
        this.agentId = agentId;
        this.tenantId = tenantId;
        this.traceId = traceId;
    }

    /**
     * Creates a new AgentPrincipal with a random session ID and trace ID.
     *
     * @param agentId  The unique identifier for the agent.
     * @param tenantId The tenant associated with the agent.
     * @return A new AgentPrincipal instance.
     */
    public static PluginPrincipal create(String agentId, String tenantId) {
        return new PluginPrincipal(UUID.randomUUID(), agentId, tenantId, UUID.randomUUID().toString());
    }

    /**
     * Creates a child AgentPrincipal with a new session ID but inheriting the
     * parent's trace ID.
     *
     * @param agentId The unique identifier for the child agent.
     * @return A new AgentPrincipal instance linked by traceId.
     */
    public PluginPrincipal deriveChild(String agentId) {
        return new PluginPrincipal(UUID.randomUUID(), agentId, this.tenantId, this.traceId);
    }
}
