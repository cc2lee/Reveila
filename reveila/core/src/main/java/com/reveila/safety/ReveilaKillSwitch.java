package com.reveila.safety;

/**
 * The Sovereign Kill Switch Interface. 
 * Defined in :core (Java 17) for cross-platform compatibility.
 */
public interface ReveilaKillSwitch {
    
    /**
     * Checks if the specific Agent ID is authorized to proceed.
     * @param agentId The unique identifier of the AI agent.
     * @return true if the agent is ACTIVE, false if KILLED.
     */
    boolean isAuthorized(String agentId);

    /**
     * Emergency broadcast to halt all agents in the current trust domain.
     */
    void emergencyStopAll();
    
    /**
     * Current status of the kill switch for logging/auditing.
     */
    SafetyStatus getStatus(String agentId);
}
