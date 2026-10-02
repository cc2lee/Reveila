package com.reveila.safety

/**
 * Data Transfer Object for safety commands.
 * Transmitted across bridges and runtimes to enforce agent behavior.
 */
data class SafetyCommand(
    val agentId: String,
    val action: SafetyAction,
    val biometricSignature: ByteArray? = null,
    val timestamp: Long = 0L
) {
    fun agentId(): String = agentId
    fun action(): SafetyAction = action
    fun biometricSignature(): ByteArray? = biometricSignature
    fun timestamp(): Long = timestamp

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SafetyCommand
        if (timestamp != other.timestamp) return false
        if (agentId != other.agentId) return false
        if (action != other.action) return false
        if (biometricSignature != null) {
            if (other.biometricSignature == null) return false
            if (!biometricSignature.contentEquals(other.biometricSignature)) return false
        } else if (other.biometricSignature != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = agentId.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + (biometricSignature?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }

    override fun toString(): String {
        return "AgentSafetyCommand[agentId='$agentId', action=$action, timestamp=$timestamp]"
    }
}
