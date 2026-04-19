package com.reveila.ai;

import java.util.Map;

import com.reveila.system.Plugin;
import com.reveila.system.SystemComponent;

/**
 * Abstract base class for all guarded runtimes.
 * Provides a common foundation for executing plugins in isolated environments.
 * 
 * @author CL
 */
public abstract class AbstractGuardedRuntime extends SystemComponent implements GuardedRuntime {
    
    /**
     * Common validation logic for perimeters and principals.
     */
    protected void validateRequest(Plugin plugin, AgencyPerimeter perimeter) {
        if (plugin == null) throw new IllegalArgumentException("AgentPrincipal cannot be null");
        if (perimeter == null) throw new IllegalArgumentException("AgencyPerimeter cannot be null");
    }

    @Override
    public abstract Object execute(Plugin plugin, AgencyPerimeter perimeter, String pluginId, Map<String, Object> arguments, Map<String, String> jitCredentials);
}
