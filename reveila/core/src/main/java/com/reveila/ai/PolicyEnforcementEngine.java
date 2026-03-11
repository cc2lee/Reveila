package com.reveila.ai;

import java.util.Map;

/**
 * Phase 3: Governance & Security.
 * Responsible for intercepting tool calls, validating against Agency Perimeters,
 * and triggering Human-in-the-Loop (HITL) workflows.
 * 
 * @author CL
 */
public interface PolicyEnforcementEngine {
    
    /**
     * Determines if a tool call is authorized and if it requires human approval.
     *
     * @param principal The agent principal attempting the action.
     * @param perimeter The active security perimeter.
     * @param toolName The name of the tool/plugin.
     * @param arguments The validated arguments.
     * @return The authorization status.
     */
    AuthorizationStatus authorize(AgentPrincipal principal, AgencyPerimeter perimeter, String toolName, Map<String, Object> arguments);

    /**
     * Injects short-lived credentials into the execution context (JIT).
     *
     * @param principal The agent principal.
     * @param scope The requested access scope.
     * @return A map of temporary credentials.
     */
    Map<String, String> getJitCredentials(AgentPrincipal principal, String scope);

    enum AuthorizationStatus {
        AUTHORIZED,
        DENIED,
        HUMAN_APPROVAL_REQUIRED
    }
}
