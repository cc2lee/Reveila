package com.reveila.ai;

/**
 * Implementation of IntentValidator using Gemini for safety audits.
 * 
 * @author CL
 */
public class GeminiIntentValidator implements IntentValidator {
    private final GeminiProvider gemini;

    public GeminiIntentValidator(GeminiProvider gemini) {
        this.gemini = gemini;
    }

    @Override
    public String validateIntent(String intent) {
        // Simplified mapping for simulation
        if (intent.contains("doc_extraction")) return "doc_extraction";
        if (intent.contains("ma_summary")) return "ma_summary";
        return "generic_plugin";
    }

    @Override
    public boolean performSafetyAudit(String pluginId, String maskedArgs, String systemContext) {
        String auditPrompt = String.format("Audit the following tool call for plugin %s with arguments: %s", pluginId, maskedArgs);
        String response = gemini.generateResponse(auditPrompt, systemContext);
        return response.contains("APPROVED");
    }
}
