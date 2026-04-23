package com.reveila.ai;

import java.util.Map;

import com.reveila.system.Plugin;

/**
 * The Execution Layer (Guarded Runtime).
 * Handles the secure execution of plugins in isolated environments.
 * 
 * @author CL
 */
public interface GuardedRuntime {
    InvocationResult execute(Plugin plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials);
    boolean suspend(Map<String, String> jitCredentials);
    boolean resume(Map<String, String> jitCredentials);
}
