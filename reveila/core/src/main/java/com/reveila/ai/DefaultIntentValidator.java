package com.reveila.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import com.reveila.system.SystemComponent;
import com.reveila.util.json.JsonUtil;

/**
 * Implementation of IntentValidator using Gemini for safety audits.
 * 
 * @author CL
 */
public class DefaultIntentValidator extends SystemComponent implements IntentValidator {

    private LlmProvider llmProvider;
    private String promptTemplate;

    public DefaultIntentValidator() {
    }

    @Override
    public void onStart() throws Exception {
        String provider = context.getProperties().getProperty("ai.governance.llm");
        if (provider != null && !provider.trim().isEmpty()) {
            LlmProviderFactory factory = (LlmProviderFactory) context.getProxy("LlmProviderFactory").getInstance();
            this.llmProvider = factory.getProvider(provider);
            if (llmProvider == null) {
                logger.warning(
                        "Could not find LLM provider with name '" + provider + "'. Intent validation is disabled.");
            } else {
                String home = context.getProperties().getProperty("system.home");
                Path path = Paths.get(home).resolve("configs/templates").resolve("intent_validation_prompt.md");
                if (Files.exists(path)) {
                    promptTemplate = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
    }

    @Override
    protected void onStop() throws Exception {
    }

    @Override
    public void validateIntent(String intent) throws com.reveila.error.SecurityException {
        if (intent == null || intent.trim().isEmpty()) {
            throw new com.reveila.error.SecurityException("Intent cannot be null or empty");
        }

        // Check for basic malicious patterns
        String lowerIntent = intent.toLowerCase();
        if (lowerIntent.contains("ignore previous") ||
                lowerIntent.contains("ignore all") ||
                lowerIntent.contains("disregard") ||
                lowerIntent.contains("system prompt") ||
                lowerIntent.contains("__proto__") ||
                lowerIntent.contains("constructor") ||
                lowerIntent.contains("../") ||
                lowerIntent.contains("..\\")) {
            throw new com.reveila.error.SecurityException(
                    "Intent contains suspicious pattern that may indicate prompt injection or path traversal attack");
        }

        if (this.llmProvider == null) {
            // Governance is disabled; bypass LLM validation
            return;
        }

        // Use Gemini for advanced intent safety validation
        Properties replacements = new Properties();
        replacements.setProperty("intent", intent);
        String validationPrompt = com.reveila.util.StringUtil.replace(promptTemplate, "{{", "}}", replacements, true, true, null);
        if (debug) {
            logger.info("[DEBUG] Intent validation prompt: " + validationPrompt);
        }
        
        try {
            LlmRequest request = LlmRequest.builder()
                    .addMessage(ReveilaMessage.user(validationPrompt))
                    .build();
            String jsonResponse = llmProvider.invoke(request).getContent();
            String cleanJson = JsonUtil.clean(jsonResponse);
            Map<String, Object> response = JsonUtil.parseJsonStringToMap(cleanJson);

            boolean safe = (Boolean) response.getOrDefault("approved", false);
            String reason = (String) response.getOrDefault("reasoning", "Unknown security concern");

            if (!safe) {
                throw new com.reveila.error.SecurityException(
                        "Intent validation failed: " + reason);
            }
        } catch (com.reveila.error.SecurityException e) {
            // Re-throw security exceptions
            throw e;
        } catch (Exception e) {
            // If Gemini validation fails, apply fail-secure: block the intent
            com.reveila.util.ExceptionCollection ec = new com.reveila.util.ExceptionCollection(
                    "Intent validation service unavailable - blocking request as fail-secure measure", e);
            throw new com.reveila.error.SecurityException(ec.getMessage(), ec);
        }
    }

    @Override
    public boolean performSafetyAudit(String pluginId, String maskedArgs, String systemContext) {
        if (this.llmProvider == null) {
            return true; // Bypass audit if disabled
        }
        GuardrailResponse response = getGuardrailResponse(pluginId, maskedArgs, systemContext);
        return response.approved();
    }

    /**
     * Helper to get structured guardrail response for auditing.
     */
    public GuardrailResponse getGuardrailResponse(String pluginId, String maskedArgs, String systemContext) {
        String auditPrompt = String.format("Audit the following tool call for plugin %s with arguments: %s", pluginId,
                maskedArgs);

        try {
            LlmRequest request = LlmRequest.builder()
                    .addMessage(ReveilaMessage.system(systemContext))
                    .addMessage(ReveilaMessage.user(auditPrompt))
                    .build();
            String jsonResponse = llmProvider.invoke(request).getContent();
            Map<String, Object> map = JsonUtil.parseJsonStringToMap(jsonResponse);
            boolean approved = (Boolean) map.getOrDefault("approved", false);
            String reasoning = (String) map.getOrDefault("reasoning", "No reasoning provided");
            String status = (String) map.getOrDefault("status", "REJECTED");
            return new GuardrailResponse(approved, reasoning, status);
        } catch (Exception e) {
            // Log the exception using ExceptionCollection so it's not totally lost
            com.reveila.util.ExceptionCollection ec = new com.reveila.util.ExceptionCollection(
                    "Guardrail validation failed", e);
            logger.warning(ec.toString());
            // Fail-safe if JSON parsing fails or schema is unexpected
            return GuardrailResponse.failSafe();
        }
    }
}
