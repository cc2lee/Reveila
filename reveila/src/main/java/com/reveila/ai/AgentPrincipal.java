package com.reveila.ai;

import java.util.UUID;

/**
 * Assigns non-person entity (NPE) identities to each agent session.
 * Ensures "Least Privilege" access to enterprise data sources.
 * 
 * @author CL
 */
public record AgentPrincipal(UUID sessionId, String agentId, String tenantId) {
    /**
     * Creates a new AgentPrincipal with a random session ID.
     *
     * @param agentId The unique identifier for the agent.
     * @param tenantId The tenant associated with the agent.
     * @return A new AgentPrincipal instance.
     */
    public static AgentPrincipal create(String agentId, String tenantId) {
        return new AgentPrincipal(UUID.randomUUID(), agentId, tenantId);
    }
}
