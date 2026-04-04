package com.reveila.ai;

import java.util.Map;

import com.reveila.system.SystemComponent;
import com.reveila.util.json.JsonUtil;

/**
 * Implementation of IntentValidator using Gemini for safety audits.
 * 
 * @author CL
 */
public class DefaultIntentValidator extends SystemComponent implements IntentValidator {
    private GeminiProvider gemini;

    public DefaultIntentValidator() {
    }

    @Override
    public void onStart() throws Exception {
        this.gemini = (GeminiProvider) context.getProxy("GeminiProvider").invoke("getInstance", null);
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public void validateIntent(String intent) throws com.reveila.error.SecurityException {
        // TODO implement validation logic
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
