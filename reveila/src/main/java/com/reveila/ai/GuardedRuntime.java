package com.reveila.ai;

import java.util.Map;

/**
 * The Execution Layer (Guarded Runtime).
 * Handles the secure execution of plugins in isolated environments.
 * 
 * @author CL
 */
public interface GuardedRuntime {
    /**
     * Executes a plugin within the guarded environment.
     *
     * @param principal The agent principal.
     * @param perimeter The security perimeter for execution.
     * @param pluginId The ID of the plugin to execute.
     * @param arguments The validated arguments for the plugin.
     * @param jitCredentials Temporary credentials to inject.
     * @return The result of the execution.
     */
    Object execute(AgentPrincipal principal, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials);
}
