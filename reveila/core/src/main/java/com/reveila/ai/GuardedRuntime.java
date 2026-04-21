package com.reveila.ai;

import java.util.Map;

import com.reveila.system.InvocationTarget;

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
     * @param plugin The agent plugin.
     * @param perimeter The security perimeter for execution.
     * @param arguments The validated arguments for the plugin.
     * @param jitCredentials Temporary credentials to inject.
     * @return The result of the execution.
     */
    Object execute(InvocationTarget plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials);
}
