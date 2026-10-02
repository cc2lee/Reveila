package com.reveila.safety

/**
 * Listener for safety commands issued from authoritative clients.
 */
fun interface SafetyCommandListener {
    /**
     * Processes a safety command.
     * @param command The signed command DTO.
     */
    fun onSafetyCommand(command: SafetyCommand)
}
