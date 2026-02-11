package com.reveila.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3: JIT Token Management.
 * Generates short-lived credentials for agentic execution.
 * 
 * @author CL
 */
public class CredentialManager {
    private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

    /**
     * Generates a short-lived JIT token for a specific agent and scope.
     *
     * @param principal The agent principal.
     * @param scope The requested access scope.
     * @return A map containing the temporary token.
     */
    public Map<String, String> generateJitToken(AgentPrincipal principal, String scope) {
        String token = "jit_" + UUID.randomUUID().toString().substring(0, 8);
        tokenStore.put(token, principal.traceId() + ":" + scope);
        return Map.of("REVEILA_JIT_TOKEN", token);
    }

    public boolean validateToken(String token) {
        return tokenStore.containsKey(token);
    }
}
