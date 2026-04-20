package com.reveila.safety;

/**
 * Listener for safety commands issued from authoritative clients.
 */
public interface SafetyCommandListener {
    
    /**
     * Processes a safety command.
     * @param command The signed command DTO.
     */
    void onSafetyCommand(AgentSafetyCommand command);
}
