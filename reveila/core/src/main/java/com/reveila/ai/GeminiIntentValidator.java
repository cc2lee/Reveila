package com.reveila.ai;

import com.reveila.util.json.JsonUtil;
import java.util.Map;

/**
 * Implementation of IntentValidator using Gemini for safety audits.
 * 
 * @author CL
 */
public class GeminiIntentValidator extends com.reveila.system.AbstractService implements IntentValidator {
    private GeminiProvider gemini;

    public GeminiIntentValidator() {
    }

    @Override
    public void onStart() throws Exception {
        this.gemini = (GeminiProvider) systemContext.getProxy("GeminiProvider").orElseThrow().invoke("getInstance", null);
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public String validateIntent(String intent) {
        // Simplified mapping for simulation
        if (intent.contains("doc_extraction")) return "doc_extraction";
        if (intent.contains("ma_summary")) return "ma_summary";
        if (intent.contains("healthcare")) return "healthcare";
        return "generic_plugin";
    }

    @Override
    public boolean performSafetyAudit(String pluginId, String maskedArgs, String systemContext) {
        GuardrailResponse response = getGuardrailResponse(pluginId, maskedArgs, systemContext);
        return response.approved();
    }

    /**
     * Helper to get structured guardrail response for auditing.
     */
    public GuardrailResponse getGuardrailResponse(String pluginId, String maskedArgs, String systemContext) {
        String auditPrompt = String.format("Audit the following tool call for plugin %s with arguments: %s", pluginId, maskedArgs);
        String jsonResponse = gemini.generateJson(systemContext, auditPrompt);
        
        try {
            Map<String, Object> map = JsonUtil.parseJsonStringToMap(jsonResponse);
            boolean approved = (Boolean) map.getOrDefault("approved", false);
            String reasoning = (String) map.getOrDefault("reasoning", "No reasoning provided");
            String status = (String) map.getOrDefault("status", "REJECTED");
            return new GuardrailResponse(approved, reasoning, status);
        } catch (Exception e) {
            // Fail-safe if JSON parsing fails or schema is unexpected
            return GuardrailResponse.failSafe();
        }
    }
}
