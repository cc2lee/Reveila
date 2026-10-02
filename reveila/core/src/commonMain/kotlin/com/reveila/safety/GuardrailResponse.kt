package com.reveila.safety

import kotlin.jvm.JvmStatic

/**
 * Structured response for the Guardrail model to prevent prompt injection.
 */
data class GuardrailResponse(
    val approved: Boolean,
    val reasoning: String,
    val status: String
) {
    fun approved(): Boolean = approved
    fun reasoning(): String = reasoning
    fun status(): String = status

    companion object {
        @JvmStatic
        fun failSafe(): GuardrailResponse {
            return GuardrailResponse(
                approved = false,
                reasoning = "Failsafe: Malformed guardrail response or suspected injection.",
                status = "REJECTED"
            )
        }
    }
}
