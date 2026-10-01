package com.reveila.safety;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Data Transfer Object for safety commands.
 * Transmitted across the bridge to enforce agent behavior.
 */
public record SafetyCommand(
        String agentId,
        SafetyAction action,
        byte[] biometricSignature,
        long timestamp) implements Serializable {

    @Override
    public boolean equals(Object obj) {
        // 1. Same memory reference short-circuit
        if (this == obj) {
            return true;
        }

        // 2. Exact type structural verification
        if (!(obj instanceof SafetyCommand other)) {
            return false;
        }

        // 3. Native field evaluation + structural array evaluation
        return this.timestamp == other.timestamp &&
                Objects.equals(this.agentId, other.agentId) &&
                this.action == other.action &&
                Arrays.equals(this.biometricSignature, other.biometricSignature);
    }

    @Override
    public int hashCode() {
        // Compute a prime-multiplier hash step using content arrays natively
        int result = Objects.hash(agentId, action, timestamp);
        result = 31 * result + Arrays.hashCode(biometricSignature);
        return result;
    }

    @Override
    public String toString() {
        // Pretty-print the array's contents instead of yielding its memory address stub
        // (e.g., [B@7a35a)
        return "AgentSafetyCommand[" +
                "agentId='" + agentId + '\'' +
                ", action=" + action +
                ", biometricSignature=" + Arrays.toString(biometricSignature) +
                ", timestamp=" + timestamp +
                ']';
    }
}