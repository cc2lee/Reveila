package com.reveila.ai;

import java.util.Map;

/**
 * Abstract base class for all guarded runtimes.
 * Provides a common foundation for executing plugins in isolated environments.
 * 
 * @author CL
 */
public abstract class AbstractGuardedRuntime implements GuardedRuntime {
    
    /**
     * Common validation logic for perimeters and principals.
     */
    protected void validateRequest(AgentPrincipal principal, AgencyPerimeter perimeter) {
        if (principal == null) throw new IllegalArgumentException("AgentPrincipal cannot be null");
        if (perimeter == null) throw new IllegalArgumentException("AgencyPerimeter cannot be null");
    }

    @Override
    public abstract Object execute(AgentPrincipal principal, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials);
}
