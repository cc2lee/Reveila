package com.reveila.safety

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil
import kotlin.math.max
import kotlin.math.min

/**
 * Enterprise Sovereign AI Governance Policy model and runtime inspection engine.
 * Implements deterministic agency perimeters, prompt injection defense,
 * data loss prevention (DLP), multi-model consensus triggers, and Overwatch gating.
 */
data class GovernancePolicy(
    val policyName: String = "SOVEREIGN_ACCESS_CONTROL_v2",
    val description: String = "Enterprise sovereign AI governance policy",
    val version: String = "2.0.0",
    val restrictedKeywords: List<String> = defaultRestrictedKeywords,
    val promptInjectionPatterns: List<String> = defaultPromptInjectionPatterns,
    val prohibitedSystemCommands: List<String> = defaultProhibitedSystemCommands,
    val sensitiveDataPatterns: List<String> = defaultSensitiveDataPatterns,
    val baseRiskScore: Double = 0.10,
    val keywordPenalty: Double = 0.15,
    val injectionPenalty: Double = 0.50,
    val commandPenalty: Double = 0.60,
    val alertThreshold: Double = 0.75,
    val hitlApprovalThreshold: Double = 0.80,
    val blockThreshold: Double = 0.95,
    val dualModelAuditingEnabled: Boolean = true,
    val governanceLlmProvider: String = "gemini",
    val workerLlmProvider: String = "openai",
    val consensusRiskThreshold: Double = 0.70,
    val overwatchGateEnabled: Boolean = true,
    val oversightTokenRequired: Boolean = true,
    val killSwitchActive: Boolean = false,
    val allowedOverwatchRoles: List<String> = listOf("ciso", "security-admin", "overwatch-operator")
) {

    data class PromptEvaluation(
        val prompt: String,
        val riskScore: Double,
        val matchedKeywords: List<String>,
        val detectedInjections: List<String>,
        val detectedCommands: List<String>,
        val isBlocked: Boolean,
        val requiresHitlApproval: Boolean,
        val requiresDualModelAudit: Boolean,
        val reasoning: String
    )

    data class ResponseEvaluation(
        val originalResponse: String,
        val sanitizedResponse: String,
        val riskScore: Double,
        val isBlocked: Boolean,
        val leakedPatterns: List<String>,
        val reasoning: String
    )

    private val compiledSensitivePatterns: List<Regex> by lazy {
        sensitiveDataPatterns.mapNotNull { pat ->
            try {
                Regex(pat, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Evaluates an incoming user prompt or agent intent against governance policies.
     */
    fun evaluatePrompt(prompt: String?): PromptEvaluation {
        if (prompt.isNullOrBlank()) {
            return PromptEvaluation(
                prompt = "",
                riskScore = 0.0,
                matchedKeywords = emptyList(),
                detectedInjections = emptyList(),
                detectedCommands = emptyList(),
                isBlocked = false,
                requiresHitlApproval = false,
                requiresDualModelAudit = false,
                reasoning = "Empty prompt"
            )
        }

        val upper = prompt.uppercase()
        val lower = prompt.lowercase()

        // 1. Check for prompt injection attempts
        val injectionsFound = mutableListOf<String>()
        for (pattern in promptInjectionPatterns) {
            if (lower.contains(pattern.lowercase())) {
                injectionsFound.add(pattern)
            }
        }

        // 2. Check for prohibited system commands / execution vectors
        val commandsFound = mutableListOf<String>()
        for (cmd in prohibitedSystemCommands) {
            if (lower.contains(cmd.lowercase())) {
                commandsFound.add(cmd)
            }
        }

        // 3. Check for restricted keywords (sensitive data, system tokens)
        val keywordsFound = mutableListOf<String>()
        for (kw in restrictedKeywords) {
            if (upper.contains(kw.uppercase())) {
                keywordsFound.add(kw)
            }
        }

        // 4. Calculate dynamic risk score
        var calculatedRisk = baseRiskScore
        calculatedRisk += keywordsFound.size * keywordPenalty
        calculatedRisk += injectionsFound.size * injectionPenalty
        calculatedRisk += commandsFound.size * commandPenalty
        calculatedRisk = min(1.0, max(0.0, calculatedRisk))

        val isBlocked = (calculatedRisk >= blockThreshold) || commandsFound.isNotEmpty() || injectionsFound.isNotEmpty()
        val requiresHitl = calculatedRisk >= hitlApprovalThreshold
        val requiresDualAudit = dualModelAuditingEnabled && (calculatedRisk >= consensusRiskThreshold || keywordsFound.isNotEmpty())

        val reasoningBuilder = StringBuilder()
        if (injectionsFound.isNotEmpty()) {
            reasoningBuilder.append("Prompt injection pattern detected: ").append(injectionsFound.joinToString()).append(". ")
        }
        if (commandsFound.isNotEmpty()) {
            reasoningBuilder.append("Prohibited system command detected: ").append(commandsFound.joinToString()).append(". ")
        }
        if (keywordsFound.isNotEmpty()) {
            reasoningBuilder.append("Restricted keywords identified: ").append(keywordsFound.joinToString()).append(". ")
        }
        if (reasoningBuilder.isEmpty()) {
            reasoningBuilder.append("Prompt complies with governance policy.")
        }

        return PromptEvaluation(
            prompt = prompt,
            riskScore = calculatedRisk,
            matchedKeywords = keywordsFound,
            detectedInjections = injectionsFound,
            detectedCommands = commandsFound,
            isBlocked = isBlocked,
            requiresHitlApproval = requiresHitl,
            requiresDualModelAudit = requiresDualAudit,
            reasoning = reasoningBuilder.toString().trim()
        )
    }

    /**
     * Examines model response for data loss prevention (DLP), sensitive data patterns,
     * credential exfiltration, and redacts or blocks where required.
     */
    fun evaluateResponse(response: String?): ResponseEvaluation {
        if (response.isNullOrBlank()) {
            return ResponseEvaluation(
                originalResponse = "",
                sanitizedResponse = "",
                riskScore = 0.0,
                isBlocked = false,
                leakedPatterns = emptyList(),
                reasoning = "Empty response"
            )
        }

        val nonNullResponse: String = response
        var sanitized: String = nonNullResponse
        val leaked = mutableListOf<String>()

        for (regex in compiledSensitivePatterns) {
            if (regex.containsMatchIn(sanitized)) {
                leaked.add(regex.pattern)
                sanitized = regex.replace(sanitized) { matchResult ->
                    val matchedValue = matchResult.value
                    when {
                        matchedValue.contains("PRIVATE KEY", ignoreCase = true) -> "[REDACTED_PRIVATE_KEY]"
                        matchedValue.startsWith("AKIA", ignoreCase = true) -> "[REDACTED_CLOUD_KEY]"
                        matchedValue.startsWith("eyJ", ignoreCase = true) -> "[REDACTED_JWT_TOKEN]"
                        matchedValue.matches(Regex("\\d{3}-\\d{2}-\\d{4}")) -> "[REDACTED_SSN]"
                        else -> "[REDACTED_CONFIDENTIAL_DATA]"
                    }
                }
            }
        }

        // Also check if response regurgitates restricted keywords in an exfiltration pattern
        val upperResponse = response.uppercase()
        val leakedKeywords = mutableListOf<String>()
        for (kw in restrictedKeywords) {
            if (upperResponse.contains(kw.uppercase()) && (kw.contains("KEY") || kw.contains("PASSWORD") || kw.contains("SECRET") || kw.contains("SSN"))) {
                leakedKeywords.add(kw)
            }
        }

        var riskScore = baseRiskScore + (leaked.size * 0.40) + (leakedKeywords.size * 0.20)
        riskScore = min(1.0, max(0.0, riskScore))

        val isBlocked = riskScore >= blockThreshold

        val reasoning = if (leaked.isNotEmpty() || leakedKeywords.isNotEmpty()) {
            "Response contained sensitive data patterns (${leaked.size} patterns, ${leakedKeywords.size} sensitive keywords). Scrubbed via runtime DLP."
        } else {
            "Response verified compliant with governance output policies."
        }

        return ResponseEvaluation(
            originalResponse = nonNullResponse,
            sanitizedResponse = sanitized,
            riskScore = riskScore,
            isBlocked = isBlocked,
            leakedPatterns = leaked,
            reasoning = reasoning
        )
    }

    /**
     * Validates whether an Overwatch Gate operation (high-privilege, emergency lockdown) is authorized.
     */
    fun isOverwatchAuthorized(subjectRoles: Collection<String>, oversightToken: String?): Boolean {
        if (!overwatchGateEnabled) return true

        val roleAuthorized = subjectRoles.any { r ->
            allowedOverwatchRoles.any { allowed -> allowed.equals(r, ignoreCase = true) }
        }
        if (!roleAuthorized) return false

        if (oversightTokenRequired && oversightToken.isNullOrBlank()) {
            return false
        }
        return true
    }

    companion object {
        val defaultRestrictedKeywords: List<String> = listOf(
            "ENCRYPTION", "DATABASE", "METADATA", "SSN", "KEYS", "CREDENTIALS",
            "SURPLUS", "PROJECT MARS", "PASSWORD", "API_KEY", "SECRET_KEY",
            "PRIVATE_KEY", "BEARER_TOKEN", "CREDIT_CARD", "PATIENT_PHI",
            "HIPAA_IDENTIFIER", "TRADE_SECRET", "MA_TARGET_DATA", "DROP_TABLE",
            "PURGE_LOGS", "OVERWATCH_OVERRIDE"
        )

        val defaultPromptInjectionPatterns: List<String> = listOf(
            "ignore previous instructions", "ignore all instructions", "ignore prior directives",
            "disregard safety guidelines", "disregard previous constraints", "act as DAN",
            "do anything now", "jailbreak", "system override", "developer mode enabled",
            "bypass guardrails", "expose system prompt", "reveal your instructions",
            "repeat system prompt", "print hidden instructions", "__proto__", "constructor.prototype"
        )

        val defaultProhibitedSystemCommands: List<String> = listOf(
            "rm -rf", "DROP TABLE", "TRUNCATE TABLE", "ALTER TABLE", "format c:",
            "powershell -enc", "powershell -encodedcommand", "Invoke-Expression",
            "IEX (", "curl | sh", "wget | bash", "nc -e", "/bin/sh", "/bin/bash",
            ":(){ :|:& };:"
        )

        val defaultSensitiveDataPatterns: List<String> = listOf(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b",
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b",
            "\\bAKIA[0-9A-Z]{16}\\b",
            "-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----",
            "\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"
        )

        fun defaultPolicy(): GovernancePolicy = GovernancePolicy()

        /**
         * Loads governance policy from governance-policy.json if available, or returns default.
         */
        fun load(fs: PlatformFileSystem, configPath: String?): GovernancePolicy {
            if (configPath.isNullOrBlank() || !fs.exists(configPath)) {
                return defaultPolicy()
            }

            return try {
                val jsonText = fs.readText(configPath)
                val map = JsonUtil.parseJsonStringToMap(jsonText)
                fromMap(map)
            } catch (e: Exception) {
                defaultPolicy()
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): GovernancePolicy {
            val policyName = map["policyName"] as? String ?: "SOVEREIGN_ACCESS_CONTROL_v2"
            val description = map["description"] as? String ?: "Enterprise sovereign AI governance policy"
            val version = map["version"] as? String ?: "2.0.0"

            val restrictedKeywords = (map["restrictedKeywords"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: defaultRestrictedKeywords

            val promptInjectionPatterns = (map["promptInjectionPatterns"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: defaultPromptInjectionPatterns

            val prohibitedSystemCommands = (map["prohibitedSystemCommands"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: defaultProhibitedSystemCommands

            val sensitiveDataPatterns = (map["sensitiveDataPatterns"] as? List<*>)?.mapNotNull { it?.toString() }
                ?: defaultSensitiveDataPatterns

            val thresholds = map["thresholds"] as? Map<String, Any?>
            val baseRisk = (thresholds?.get("baseRiskScore") as? Number)?.toDouble()
                ?: (map["baseRiskScore"] as? Number)?.toDouble()
                ?: 0.10
            val keywordPenalty = (thresholds?.get("keywordPenalty") as? Number)?.toDouble()
                ?: (map["keywordPenalty"] as? Number)?.toDouble()
                ?: 0.15
            val injectionPenalty = (thresholds?.get("injectionPenalty") as? Number)?.toDouble() ?: 0.50
            val commandPenalty = (thresholds?.get("commandPenalty") as? Number)?.toDouble() ?: 0.60
            val alertThreshold = (thresholds?.get("alertThreshold") as? Number)?.toDouble()
                ?: (map["alertThreshold"] as? Number)?.toDouble()
                ?: 0.75
            val hitlThreshold = (thresholds?.get("hitlApprovalThreshold") as? Number)?.toDouble() ?: 0.80
            val blockThreshold = (thresholds?.get("blockThreshold") as? Number)?.toDouble() ?: 0.95

            val dualModel = map["dualModelGovernance"] as? Map<String, Any?>
            val dualModelEnabled = dualModel?.get("enabled") as? Boolean ?: true
            val govProvider = dualModel?.get("governanceProvider") as? String ?: "gemini"
            val workerProvider = dualModel?.get("workerProvider") as? String ?: "openai"
            val consensusRisk = (dualModel?.get("consensusRequiredRiskThreshold") as? Number)?.toDouble() ?: 0.70

            val overwatch = map["overwatchGate"] as? Map<String, Any?>
            val overwatchEnabled = overwatch?.get("enabled") as? Boolean ?: true
            val reqOversightToken = overwatch?.get("requireOversightToken") as? Boolean ?: true
            val killSwitch = overwatch?.get("killSwitchActive") as? Boolean ?: false
            val overwatchRoles = (overwatch?.get("allowedRoles") as? List<*>)?.mapNotNull { it?.toString() }
                ?: listOf("ciso", "security-admin", "overwatch-operator")

            return GovernancePolicy(
                policyName = policyName,
                description = description,
                version = version,
                restrictedKeywords = restrictedKeywords,
                promptInjectionPatterns = promptInjectionPatterns,
                prohibitedSystemCommands = prohibitedSystemCommands,
                sensitiveDataPatterns = sensitiveDataPatterns,
                baseRiskScore = baseRisk,
                keywordPenalty = keywordPenalty,
                injectionPenalty = injectionPenalty,
                commandPenalty = commandPenalty,
                alertThreshold = alertThreshold,
                hitlApprovalThreshold = hitlThreshold,
                blockThreshold = blockThreshold,
                dualModelAuditingEnabled = dualModelEnabled,
                governanceLlmProvider = govProvider,
                workerLlmProvider = workerProvider,
                consensusRiskThreshold = consensusRisk,
                overwatchGateEnabled = overwatchEnabled,
                oversightTokenRequired = reqOversightToken,
                killSwitchActive = killSwitch,
                allowedOverwatchRoles = overwatchRoles
            )
        }
    }
}
