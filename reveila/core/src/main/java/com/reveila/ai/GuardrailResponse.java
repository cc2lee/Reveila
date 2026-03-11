package com.reveila.ai;

/**
 * Structured response for the Guardrail model to prevent prompt injection.
 * 
 * @author CL
 */
public record GuardrailResponse(boolean approved, String reasoning, String status) {
    /**
     * Default rejection for malformed or suspicious responses.
     */
    public static GuardrailResponse failSafe() {
        return new GuardrailResponse(false, "Failsafe: Malformed guardrail response or suspected injection.", "REJECTED");
    }
}
