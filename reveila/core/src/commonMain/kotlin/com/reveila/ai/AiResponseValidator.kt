package com.reveila.ai

import com.reveila.crypto.PlatformCryptographer
import com.reveila.safety.GovernancePolicy

/**
 * Validates and sanitizes outgoing LLM model responses.
 * Detects infinite repetition loops, hallucinatory echoes,
 * and executes runtime Data Loss Prevention (DLP) against governance policies.
 */
open class AiResponseValidator(
    private val policy: GovernancePolicy = GovernancePolicy.defaultPolicy()
) {

    data class ResponseValidationResult(
        val isValid: Boolean,
        val sanitizedContent: String,
        val isBlocked: Boolean,
        val isRepeating: Boolean,
        val riskScore: Double,
        val reasoning: String
    )

    private val stateHistory: MutableSet<String> = mutableSetOf()
    private val platformCrypto = PlatformCryptographer()

    /**
     * Validates and sanitizes the LLM response against DLP and loop detection rules.
     */
    open fun validateAndSanitize(agentResponse: String?): ResponseValidationResult {
        if (agentResponse.isNullOrBlank()) {
            return ResponseValidationResult(
                isValid = false,
                sanitizedContent = "",
                isBlocked = false,
                isRepeating = false,
                riskScore = 0.0,
                reasoning = "Response is empty or null."
            )
        }

        // 1. Loop and repetition detection
        val stateHash = calculateHash(agentResponse)
        if (stateHistory.contains(stateHash)) {
            return ResponseValidationResult(
                isValid = false,
                sanitizedContent = agentResponse,
                isBlocked = false,
                isRepeating = true,
                riskScore = 0.50,
                reasoning = "Repetitive response detected: AI repeated identical state."
            )
        }
        stateHistory.add(stateHash)

        // 2. DLP and Sensitive Data Pattern inspection via Governance Policy
        val eval = policy.evaluateResponse(agentResponse)
        if (eval.isBlocked) {
            return ResponseValidationResult(
                isValid = false,
                sanitizedContent = eval.sanitizedResponse,
                isBlocked = true,
                isRepeating = false,
                riskScore = eval.riskScore,
                reasoning = "SECURITY_BREACH: Response blocked by governance DLP policy - ${eval.reasoning}"
            )
        }

        return ResponseValidationResult(
            isValid = true,
            sanitizedContent = eval.sanitizedResponse,
            isBlocked = false,
            isRepeating = false,
            riskScore = eval.riskScore,
            reasoning = eval.reasoning
        )
    }

    /**
     * Legacy / convenience entry point returning the sanitized response or null if invalid.
     */
    open fun getMessage(agentResponse: String?): String? {
        val result = validateAndSanitize(agentResponse)
        return if (result.isValid) result.sanitizedContent else null
    }

    private fun calculateHash(input: String): String {
        val hash = platformCrypto.sha256(input.encodeToByteArray())
        return platformCrypto.base64Encode(hash)
    }
}
