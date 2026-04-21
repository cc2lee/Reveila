package com.reveila.ai;

import java.util.Map;

import com.reveila.system.InvocationTarget;

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
     * @param plugin The agent plugin attempting the action.
     * @param perimeter The active security perimeter.
     * @param toolName The name of the tool/plugin.
     * @param arguments The validated arguments.
     * @return The authorization status.
     */
    AuthorizationStatus authorize(InvocationTarget plugin, SecurityPerimeter perimeter, String toolName, Map<String, Object> arguments);

    /**
     * Injects short-lived credentials into the execution context (JIT).
     *
     * @param plugin The agent plugin.
     * @param scope The requested access scope.
     * @return A map of temporary credentials.
     */
    Map<String, String> getJitCredentials(InvocationTarget plugin, String scope);

    enum AuthorizationStatus {
        AUTHORIZED,
        DENIED,
        HUMAN_APPROVAL_REQUIRED
    }
}
