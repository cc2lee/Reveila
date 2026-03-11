package com.reveila.core.safety;

import java.io.Serializable;

/**
 * Data Transfer Object for safety commands.
 * Transmitted across the bridge to enforce agent behavior.
 */
public record AgentSafetyCommand(
    String agentId,
    SafetyAction action, 
    byte[] biometricSignature, 
    long timestamp
) implements Serializable {
}
