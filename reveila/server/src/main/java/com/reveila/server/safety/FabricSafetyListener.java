package com.reveila.server.safety;

import com.reveila.core.safety.AgentSafetyCommand;
import com.reveila.core.safety.SafetyCommandListener;
import com.reveila.core.safety.SafetyStatus;
import com.reveila.core.safety.SafetyAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Server-side implementation of SafetyCommandListener.
 * Leverages Java 21 features for high-performance state propagation.
 */
public class FabricSafetyListener implements SafetyCommandListener {

    private static final Map<String, Boolean> KILLED_AGENTS = new ConcurrentHashMap<>();
    
    /**
     * Volatile flag for global safety state. 
     * Ensures sub-millisecond propagation across all Virtual Threads.
     */
    private static volatile boolean globalHalt = false;

    /**
     * Java 21 ScopedValue (Preview) to propagate safety state within specific task hierarchies.
     */
    public static final ScopedValue<Boolean> IS_KILLED = ScopedValue.newInstance();

    @Override
    public void onSafetyCommand(AgentSafetyCommand command) {
        if (command.action() == SafetyAction.HALT || command.action() == SafetyAction.KILL) {
            if ("GLOBAL".equals(command.agentId())) {
                globalHalt = true;
            } else {
                KILLED_AGENTS.put(command.agentId(), true);
            }
        } else if (command.action() == SafetyAction.ISOLATE) {
            KILLED_AGENTS.put(command.agentId(), true);
        }
    }

    /**
     * Helper to execute a task within a safety scope.
     */
    public static void runInSafetyScope(String agentId, Runnable task) {
        boolean killed = globalHalt || KILLED_AGENTS.getOrDefault(agentId, false);
        ScopedValue.where(IS_KILLED, killed).run(task);
    }

    /**
     * Checks if current execution is safe to proceed.
     */
    public static boolean checkSafety(String agentId) {
        return globalHalt || KILLED_AGENTS.getOrDefault(agentId, false) || (IS_KILLED.isBound() && IS_KILLED.get());
    }
}
