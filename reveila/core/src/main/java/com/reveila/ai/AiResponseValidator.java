package com.reveila.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Orchestrates Agentic Loops with deterministic termination and state
 * detection.
 * Designed for Reveila-Suite Agentic Fabric (March 2026).
 */
public class AiResponseValidator {

    private final Set<String> stateHistory;
    private final MessageDigest digest;

    public AiResponseValidator() {
        this.stateHistory = new HashSet<>();
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Critical Error: SHA-256 not found for state hashing.");
        }
    }

    public String getMessage(String agentResponse) {
        if (agentResponse == null)
            return null;

        String stateHash = calculateHash(agentResponse);
        if (stateHistory.contains(stateHash)) {
            return null; // AI is repeating itself
        }

        stateHistory.add(stateHash);
        return agentResponse;
    }

    private String calculateHash(String input) {
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}