package com.reveila.ai;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.reveila.crypto.Cryptographer;
import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.AbstractService;
import com.reveila.system.PluginPrincipal;
import com.reveila.system.SystemContext;

/**
 * Phase 3: JIT Token Management.
 * Generates short-lived credentials for agentic execution.
 * Handles encrypted storage of sovereign secrets.
 * 
 * @author CL
 */
public class CredentialManager extends AbstractService {
    private final Map<String, String> jitTokens = new ConcurrentHashMap<>();
    private static final String ENC_PREFIX = "ENC:";

    @Override
    protected void onStart() throws Exception {
        logger.info("CredentialManager (Sovereign Vault) starting...");
        ensureSecretStoreExists();
    }

    private void ensureSecretStoreExists() {
        try {
            this.context.getProxy("DataService")
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

            Repository<Entity, Map<String, Map<String, Object>>> repo = (Repository<Entity, Map<String, Map<String, Object>>>) this.context
                    .getProxy("DataService")
                    .orElseThrow(() -> new IllegalStateException("DataService not found"))
                    .invoke("getRepository", new Object[] { "reveila_secrets" });

            return repo.fetchById(id)
                    .map(entity -> {
                        String val = (String) entity.getAttributes().get("value");
                        return decryptIfNeeded(val);
                    })
                    .orElse(null);
        } catch (Exception e) {
            logger.warning("Failed to retrieve secret from Sovereign Store: " + secretKey + ". Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Stores a secret in the Sovereign Store, encrypting it first.
     * 
     * @param key   The secret key.
     * @param value The plain text secret value.
     */
    @SuppressWarnings("unchecked")
    public void storeSecret(String key, String value) {
        try {
            String encryptedValue = encrypt(value);

            Map<String, Map<String, Object>> idMap = Map.of("id", Map.of("value", key));
            Map<String, Object> attributes = Map.of("id", key, "value", encryptedValue);
            Entity entity = new Entity("reveila_secrets", idMap, attributes);

            Repository<Entity, Map<String, Map<String, Object>>> repo = (Repository<Entity, Map<String, Map<String, Object>>>) this.context
                    .getProxy("DataService")
                    .orElseThrow(() -> new IllegalStateException("DataService not found"))
                    .invoke("getRepository", new Object[] { "reveila_secrets" });

            repo.store(entity);
            logger.info("Successfully stored encrypted secret: " + key);
        } catch (Exception e) {
            logger.severe("Failed to store secret '" + key + "': " + e.getMessage());
        }
    }

    private String encrypt(String value) throws Exception {
        if (value == null)
            return null;
        Cryptographer crypto = ((SystemContext) context).getCryptographer();
        byte[] encrypted = crypto.encrypt(value.getBytes());
        return ENC_PREFIX + Base64.getEncoder().encodeToString(encrypted);
    }

    private String decryptIfNeeded(String value) {
        if (value == null || !value.startsWith(ENC_PREFIX)) {
            return value;
        }

        try {
            String base64Data = value.substring(ENC_PREFIX.length());
            byte[] encryptedData = Base64.getDecoder().decode(base64Data);
            Cryptographer crypto = ((SystemContext) context).getCryptographer();
            return new String(crypto.decrypt(encryptedData));
        } catch (Exception e) {
            logger.severe("Failed to decrypt secret value: " + e.getMessage());
            return "ERROR_DECRYPTION_FAILED";
        }
    }

    /**
     * Generates a short-lived JIT token for a specific agent and scope.
     *
     * @param principal The agent principal.
     * @param scope     The requested access scope.
     * @return A map containing the temporary token.
     */
    public Map<String, String> generateJitToken(PluginPrincipal principal, String scope) {
        String token = "jit_" + UUID.randomUUID().toString().substring(0, 8);
        jitTokens.put(token, principal.getTraceId() + ":" + scope);
        return Map.of("REVEILA_JIT_TOKEN", token);
    }

    public boolean validateToken(String token) {
        return jitTokens.containsKey(token);
    }
}
