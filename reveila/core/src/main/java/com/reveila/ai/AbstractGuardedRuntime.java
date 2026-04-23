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

    protected boolean isSuspended = false;

    public boolean isSuspended() {
        return isSuspended;
    }

    protected void validateRequest(Plugin target, SecurityPerimeter perimeter) {
        if (target == null)
            throw new IllegalArgumentException("Plugin cannot be null");
        if (perimeter == null)
            throw new IllegalArgumentException("SecurityPerimeter cannot be null");

        // TODO Check if target is allowed by perimeter
    }

    @Override
    public final InvocationResult execute(Plugin plugin, SecurityPerimeter perimeter, Map<String, Object> arguments,
            Map<String, String> jitCredentials) {
        if (isSuspended) {
            return InvocationResult.error("Runtime is currently suspended.");
        }
        validateRequest(plugin, perimeter);

        // TODO Implement execution logic
        
        return onExecute(plugin, perimeter, arguments, jitCredentials);
    }

    @Override
    public boolean suspend(Map<String, String> jitCredentials) {
        isSuspended = true;
        return true;
    }

    @Override
    public boolean resume(Map<String, String> jitCredentials) {
        isSuspended = false;
        return true;
    }

    protected abstract InvocationResult onExecute(Plugin plugin, SecurityPerimeter perimeter, Map<String, Object> arguments,
            Map<String, String> jitCredentials);
}
