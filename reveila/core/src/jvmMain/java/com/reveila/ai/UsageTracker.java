package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

import java.time.Instant;

import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.system.SystemComponent;

public class UsageTracker extends SystemComponent {

    private Repository<Entity, Map<String, Map<String, Object>>> usageRepository;

    @Override
    @SuppressWarnings("unchecked")
    protected void onStart() throws Exception {
        Object repo = context.getProxy("DataService").invoke("getRepository", new Object[] { "LlmUsageLog" });
        if (repo instanceof Repository) {
            this.usageRepository = (Repository<Entity, Map<String, Map<String, Object>>>) repo;
        } else {
            logger.warning("LlmUsageLog repository not found via DataService. UsageTracker will not persist data.");
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    public void logUsage(String tenantId, String requestId, String modelId,
            Usage usage, long latencyMs, JSONObject securityResult) {

        if (this.usageRepository == null) {
            logger.warning("Usage data not persisted because usageRepository is null.");
            return;
        }

        boolean isSafe = securityResult.optBoolean("safe", true);
        String risk = securityResult.optString("risk_category", "UNKNOWN");
        boolean mismatch = (!isSafe && risk.equals("NONE"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tenant_id", tenantId);
        attributes.put("request_id", requestId);
        attributes.put("model_id", modelId);
        attributes.put("prompt_tokens", usage.getPromptTokens());
        attributes.put("completion_tokens", usage.getCompletionTokens());
        attributes.put("cached_tokens", usage.getCachedPromptTokens());
        attributes.put("reasoning_tokens", usage.getReasoningTokens());
        attributes.put("estimated_cost", usage.getEstimatedCost());
        attributes.put("validation_latency_ms", latencyMs);
        attributes.put("logic_mismatch", mismatch);
        attributes.put("risk_category", risk);
        attributes.put("timestamp", Instant.now().toString());

        Map<String, Map<String, Object>> key = new HashMap<>();
        Map<String, Object> idValue = new HashMap<>();
        idValue.put("value", UUID.randomUUID().toString());
        key.put("id", idValue);

        Entity entity = new Entity("LlmUsageLog", key, attributes);
        try {
            usageRepository.store(entity);
        } catch (Exception e) {
            logger.severe("Failed to store usage log: " + e.getMessage());
        }
    }
}