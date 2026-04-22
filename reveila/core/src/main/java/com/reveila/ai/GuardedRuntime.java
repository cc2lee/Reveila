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
    Object execute(InvocationTarget plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials);
    boolean pause(Map<String, String> jitCredentials);
    boolean resume(Map<String, String> jitCredentials);
    boolean kill(Map<String, String> jitCredentials);
}
