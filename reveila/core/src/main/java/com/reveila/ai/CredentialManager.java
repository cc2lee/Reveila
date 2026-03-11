package com.reveila.ai;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.reveila.service.DataService;
import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.AbstractService;

/**
 * Phase 3: JIT Token Management.
 * Generates short-lived credentials for agentic execution.
 * 
 * @author CL
 */
public class CredentialManager extends AbstractService {
    private final Map<String, String> jitTokens = new ConcurrentHashMap<>();

    @Override
    protected void onStart() throws Exception {
        logger.info("CredentialManager (Sovereign Vault) starting...");
        ensureSecretStoreExists();
    }

    private void ensureSecretStoreExists() {
        try {
            this.systemContext.getProxy("DataService")
                    .orElseThrow(() -> new IllegalStateException("DataService not found"))
                    .invoke("getRepository", new Object[] { "reveila_secrets" });

            logger.info("Verified Sovereign Secret Store: reveila_secrets");
        } catch (Exception e) {
            logger.severe("Failed to verify Sovereign Secret Store: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Tiered secret retrieval logic.
     * 1. Memory (jitTokens)
     * 2. Environment (System.getenv)
     * 3. Sovereign Store (DataService)
     *
     * @param secretKey The key or handle for the secret.
     * @return The resolved secret value, or null if not found.
     */
    @SuppressWarnings("unchecked")
    public String getSecret(String secretKey) {
        // Level 1: Memory
        String token = jitTokens.get(secretKey);
        if (token != null)
            return token;

        // Level 2: Environment
        String envValue = System.getenv(secretKey);
        if (envValue != null)
            return envValue;

        // Level 3: Sovereign Store
        try {
            // Expected ID format for the repository
            Map<String, Map<String, Object>> id = Map.of("id", Map.of("value", secretKey));

            Repository<Entity, Map<String, Map<String, Object>>> repo = (Repository<Entity, Map<String, Map<String, Object>>>) this.systemContext
                    .getProxy("DataService")
                    .orElseThrow(() -> new IllegalStateException("DataService not found"))
                    .invoke("getRepository", new Object[] { "reveila_secrets" });

            return repo.fetchById(id)
                    .map(entity -> (String) entity.getAttributes().get("value"))
                    .orElse(null);
        } catch (Exception e) {
            logger.warning("Failed to retrieve secret from Sovereign Store: " + secretKey + ". Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates a short-lived JIT token for a specific agent and scope.
     *
     * @param principal The agent principal.
     * @param scope     The requested access scope.
     * @return A map containing the temporary token.
     */
    public Map<String, String> generateJitToken(AgentPrincipal principal, String scope) {
        String token = "jit_" + UUID.randomUUID().toString().substring(0, 8);
        jitTokens.put(token, principal.traceId() + ":" + scope);
        return Map.of("REVEILA_JIT_TOKEN", token);
    }

    public boolean validateToken(String token) {
        return jitTokens.containsKey(token);
    }
}
