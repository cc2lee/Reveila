package com.reveila.ai;

import java.util.Map;

import com.reveila.system.InvocationTarget;
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
    protected void validateRequest(InvocationTarget plugin, SecurityPerimeter perimeter) {
        if (plugin == null) throw new IllegalArgumentException("InvocationTarget cannot be null");
        if (perimeter == null) throw new IllegalArgumentException("SecurityPerimeter cannot be null");
    }

    @Override
    public abstract Object execute(InvocationTarget plugin, SecurityPerimeter perimeter, Map<String, Object> arguments, Map<String, String> jitCredentials);
}
