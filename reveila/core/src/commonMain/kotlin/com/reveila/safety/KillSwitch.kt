package com.reveila.safety

/**
 * The Sovereign Kill Switch Interface.
 * Available across all native Apple/Darwin, Windows, Linux, and JVM platforms.
 */
interface KillSwitch {

    /**
     * Checks if the specific Agent ID is authorized to proceed.
     * @param agentId The unique identifier of the AI agent.
     * @return true if the agent is ACTIVE, false if KILLED.
     */
    fun isAuthorized(agentId: String): Boolean

    /**
     * Emergency broadcast to halt all agents in the current trust domain.
     */
    fun emergencyStopAll()

    /**
     * Current status of the kill switch for logging/auditing.
     */
    fun getStatus(agentId: String): SafetyStatus
}
