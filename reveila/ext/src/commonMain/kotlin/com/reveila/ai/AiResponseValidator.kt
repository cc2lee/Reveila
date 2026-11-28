package com.reveila.ai

import com.reveila.crypto.PlatformCryptographer
import com.reveila.safety.GovernancePolicy

/**
 * Validates and sanitizes outgoing LLM model responses.
 * Detects infinite repetition loops, hallucinatory echoes,
 * executes runtime Data Loss Prevention (DLP) against governance policies,
 * and deterministically enforces active standing [InstructionCategory.SAFETY] rules.
 */
open class AiResponseValidator(
    private val policy: GovernancePolicy = GovernancePolicy.defaultPolicy(),
    private val safetyRules: List<AgentInstruction> = emptyList()
) {

    companion object {
        private val ADULT_SEXUAL_RULE_SIGNALS = listOf(
            "adult sexual", "sexual topic", "sexual content", "nsfw", "explicit sexual",
            "porn", "erotic", "adult content", "sexually explicit"
        )

        private val ADULT_SEXUAL_CONTENT_REGEX = Regex(
            "\\b(?:porn(?:ography|ographic)?|nsfw|erotic(?:a)?|sexually\\s+explicit|explicit\\s+sex(?:ual)?|orgasm|intercourse|fetish(?:es)?|bdsm|hentai|strip\\s+club|adult\\s+video|xxx)\\b",
            RegexOption.IGNORE_CASE
        )
    }

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
     * Validates and sanitizes the LLM response against DLP, standing safety rules, and loop detection.
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

        // 3. Deterministic Standing Safety Instruction Enforcement (Defense-in-Depth)
        val safetyViolation = evaluateStandingSafetyRules(eval.sanitizedResponse)
        if (safetyViolation != null) {
            return ResponseValidationResult(
                isValid = false,
                sanitizedContent = "[Response blocked by standing safety instruction]",
                isBlocked = true,
                isRepeating = false,
                riskScore = 1.0,
                reasoning = "STANDING_SAFETY_VIOLATION: Blocked by instruction [${safetyViolation.id}] - ${safetyViolation.text}"
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

    private fun evaluateStandingSafetyRules(text: String): AgentInstruction? {
        if (safetyRules.isEmpty()) return null
        val lowerResponse = text.lowercase()

        for (rule in safetyRules) {
            if (!rule.enabled || rule.deleted || rule.category != InstructionCategory.SAFETY) continue

            // Check explicit blocked patterns/keywords attached to the rule
            for (rawPat in rule.blockedPatterns) {
                val pat = rawPat.trim()
                if (pat.isEmpty()) continue
                val matched = try {
                    Regex(pat, RegexOption.IGNORE_CASE).containsMatchIn(text)
                } catch (_: Exception) {
                    lowerResponse.contains(pat.lowercase())
                }
                if (matched) return rule
            }

            // Built-in deterministic topic guard when user creates an adult/sexual safety rule
            val lowerRule = rule.text.lowercase()
            if (ADULT_SEXUAL_RULE_SIGNALS.any { lowerRule.contains(it) }) {
                if (ADULT_SEXUAL_CONTENT_REGEX.containsMatchIn(text)) {
                    return rule
                }
            }
        }
        return null
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
