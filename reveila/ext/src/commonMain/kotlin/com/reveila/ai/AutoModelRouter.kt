package com.reveila.ai

import kotlin.math.ln

/**
 * Cost and performance profile used by [AutoModelRouter] when choosing the best capable model
 * under `"Auto"` mode.
 *
 * Values come from a 3-layer hierarchy:
 * 1. Optional user-defined overrides in `llm.json` (`Cost.Tier`, `Cost.Per1MTokens`, `Performance.Tier`).
 * 2. Built-in model/type priors inferred from `(type, model, provider)`.
 * 3. Combined with live runtime telemetry ([ProviderRuntimeStats]) observed by [TrackedLlmProvider].
 */
data class ProviderRoutingProfile(
    val costPer1MTokens: Double,
    val performanceTier: Double,
    val source: String = "MODEL_PRIOR"
)

/**
 * Lightweight rolling runtime telemetry per provider (EWMA latency + recent failure tracking).
 */
data class ProviderRuntimeStats(
    val ewmaLatencyMs: Double? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastLatencyMs: Long? = null
) {
    val totalCalls: Int
        get() = successCount + failureCount

    val failureRate: Double
        get() = if (totalCalls == 0) 0.0 else failureCount.toDouble() / totalCalls.toDouble()

    fun recordSuccess(latencyMs: Long): ProviderRuntimeStats {
        val clampedLatency = latencyMs.coerceAtLeast(1L).toDouble()
        val updatedEwma = if (ewmaLatencyMs == null) {
            clampedLatency
        } else {
            (0.3 * clampedLatency) + (0.7 * ewmaLatencyMs)
        }
        return copy(
            ewmaLatencyMs = updatedEwma,
            successCount = successCount + 1,
            consecutiveFailures = 0,
            lastLatencyMs = latencyMs
        )
    }

    fun recordFailure(latencyMs: Long? = null): ProviderRuntimeStats {
        return copy(
            failureCount = failureCount + 1,
            consecutiveFailures = consecutiveFailures + 1,
            lastLatencyMs = latencyMs ?: lastLatencyMs
        )
    }
}

data class RoutingCandidate(
    val name: String,
    val type: String,
    val providerField: String,
    val model: String,
    val isTested: Boolean,
    val profile: ProviderRoutingProfile,
    val stats: ProviderRuntimeStats = ProviderRuntimeStats()
)

data class ScoredRoutingCandidate(
    val candidate: RoutingCandidate,
    val totalScore: Double,
    val reason: String
)

/**
 * Capability-aware cost + performance scorer for `"Auto"` model selection.
 *
 * Note: This router governs Worker / Assistant model selection only.
 * Safety Review AI (`ai.governance.llm`) remains strictly decoupled and privacy-first.
 */
object AutoModelRouter {

    private val paramBillionsRegex = Regex("(?<![0-9.])(\\d+(?:\\.\\d+)?)b(?![a-z])")
    private val miniModelRegex = Regex("(?<![a-z])mini(?![a-z])")

    /**
     * Resolves a provider's [ProviderRoutingProfile] by starting with built-in model priors
     * and applying any optional user-configured overrides from `llm.json`.
     */
    fun resolveProfile(
        type: String,
        model: String,
        providerField: String = "",
        userCostTier: String? = null,
        userCostPer1MTokens: Double? = null,
        userPerformanceTier: String? = null,
        modelPricing: Map<String, Double> = emptyMap()
    ): ProviderRoutingProfile {
        val prior = inferDefaultProfile(type = type, model = model, providerField = providerField)

        var cost = prior.costPer1MTokens
        var perf = prior.performanceTier
        var hasUserOverride = false

        val matchedModelPrice = if (model.isNotBlank() && modelPricing.isNotEmpty()) {
            modelPricing.entries.firstOrNull { it.key.equals(model.trim(), ignoreCase = true) }?.value
        } else {
            null
        }

        if (matchedModelPrice != null && matchedModelPrice >= 0.0) {
            cost = matchedModelPrice
            hasUserOverride = true
        } else if (userCostPer1MTokens != null && userCostPer1MTokens >= 0.0) {
            cost = userCostPer1MTokens
            hasUserOverride = true
        } else if (!userCostTier.isNullOrBlank()) {
            val parsedCost = parseCostTier(userCostTier)
            if (parsedCost != null) {
                cost = parsedCost
                hasUserOverride = true
            }
        }

        if (!userPerformanceTier.isNullOrBlank()) {
            val parsedPerf = parsePerformanceTier(userPerformanceTier)
            if (parsedPerf != null) {
                perf = parsedPerf
                hasUserOverride = true
            }
        }

        return ProviderRoutingProfile(
            costPer1MTokens = cost.coerceAtLeast(0.0),
            performanceTier = perf.coerceIn(1.0, 5.0),
            source = if (hasUserOverride) "USER_CONFIG" else "MODEL_PRIOR"
        )
    }

    /**
     * Infers sensible zero-config cost (blended USD / 1M tokens) and capability tier (1.0..5.0)
     * from the provider type and model identifier.
     */
    fun inferDefaultProfile(
        type: String,
        model: String,
        providerField: String = ""
    ): ProviderRoutingProfile {
        val normType = type.trim().lowercase()
        val lowerModel = model.trim().lowercase()
        val lowerProvider = providerField.trim().lowercase()
        val paramBillions = paramBillionsRegex.find(lowerModel)
            ?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        if (normType == "local") {
            val perf = when {
                paramBillions == null -> 2.3
                paramBillions >= 14.0 -> 3.9
                paramBillions >= 7.0 -> 3.2
                paramBillions >= 2.0 -> 2.3
                else -> 1.6
            }
            return ProviderRoutingProfile(costPer1MTokens = 0.0, performanceTier = perf)
        }

        if (normType == "self-hosted" || normType == "selfhosted") {
            val perf = when {
                paramBillions == null -> 3.5
                paramBillions >= 70.0 -> 4.6
                paramBillions >= 30.0 -> 4.3
                paramBillions >= 14.0 -> 3.9
                paramBillions >= 7.0 -> 3.4
                else -> 2.5
            }
            return ProviderRoutingProfile(costPer1MTokens = 0.0, performanceTier = perf)
        }

        // Cloud model priors (blended input/output USD per 1M tokens + capability tier 1..5)
        return when {
            lowerModel.contains("flash-lite") || lowerModel.contains("nano") || lowerModel.contains("qwen-turbo") ->
                ProviderRoutingProfile(costPer1MTokens = 0.15, performanceTier = 3.4)

            lowerModel.contains("deepseek-reasoner") || lowerModel.contains("deepseek-r1") || lowerModel.endsWith("-r1") ->
                ProviderRoutingProfile(costPer1MTokens = 1.20, performanceTier = 4.8)

            lowerModel.contains("deepseek-chat") || lowerModel.contains("deepseek-v3") || lowerProvider.contains("deepseek") ->
                ProviderRoutingProfile(costPer1MTokens = 0.40, performanceTier = 4.1)

            lowerModel.contains("flash") ->
                ProviderRoutingProfile(costPer1MTokens = 0.30, performanceTier = 3.9)

            lowerModel.contains("gemini") && lowerModel.contains("pro") ->
                ProviderRoutingProfile(costPer1MTokens = 2.50, performanceTier = 4.7)

            miniModelRegex.containsMatchIn(lowerModel) || lowerModel.contains("haiku") ->
                ProviderRoutingProfile(costPer1MTokens = 0.40, performanceTier = 3.8)

            lowerModel.contains("qwen-plus") ->
                ProviderRoutingProfile(costPer1MTokens = 0.50, performanceTier = 3.9)

            lowerModel.contains("qwen-max") ->
                ProviderRoutingProfile(costPer1MTokens = 2.00, performanceTier = 4.5)

            lowerModel.contains("opus") ->
                ProviderRoutingProfile(costPer1MTokens = 15.00, performanceTier = 5.0)

            lowerModel.contains("o1") || lowerModel.contains("o3") || lowerModel.contains("o4") ||
                lowerModel.contains("gpt-5") || lowerModel.contains("gpt-4.1") ||
                lowerModel.contains("gpt-4o") || lowerModel.contains("sonnet") ||
                lowerModel.contains("thinking") ->
                ProviderRoutingProfile(costPer1MTokens = 4.00, performanceTier = 4.8)

            else ->
                ProviderRoutingProfile(costPer1MTokens = 1.50, performanceTier = 4.0)
        }
    }

    /**
     * Scores a single capable candidate for [category].
     */
    fun scoreCandidate(
        candidate: RoutingCandidate,
        category: TaskRoutingCategory,
        hasAnyTestedCandidate: Boolean
    ): ScoredRoutingCandidate {
        val normType = candidate.type.trim().lowercase()
        val isLocal = normType == "local"
        val isSelfHosted = normType == "self-hosted" || normType == "selfhosted"

        // Smooth log-scaled cost efficiency in (0, 1]: $0 -> 1.0, $0.30 -> 0.79, $1.20 -> 0.56, $4 -> 0.38
        val costEfficiency = 1.0 / (1.0 + ln(1.0 + candidate.profile.costPer1MTokens.coerceAtLeast(0.0)))
        val perfScore = ((candidate.profile.performanceTier - 1.0) / 4.0).coerceIn(0.0, 1.0)
        val latencyScore = candidate.stats.ewmaLatencyMs?.let { ewma ->
            1.0 / (1.0 + (ewma / 4000.0))
        } ?: when {
            isLocal -> 0.85
            isSelfHosted -> 0.75
            else -> 0.65
        }

        val (wPerf, wCost, wLatency) = when (category) {
            TaskRoutingCategory.LOCAL_TEXT_TRANSFORM,
            TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT,
            TaskRoutingCategory.LOCAL_DEVICE_ACTION -> Triple(0.30, 0.50, 0.20)

            TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL -> Triple(0.30, 0.50, 0.20)

            TaskRoutingCategory.CLOUD_DEEP_REASONING,
            TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT -> Triple(0.65, 0.25, 0.10)
        }

        val localityBonus = when {
            !category.isCloudRoute && isLocal -> 0.50
            !category.isCloudRoute && isSelfHosted -> 0.25
            category == TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL && isSelfHosted -> 0.08
            else -> 0.0
        }

        // Capability floor ensures $0 local/economy models cannot outscore frontier reasoning
        // models on deep-reasoning or multi-step agent tasks when they fall short on capability.
        val capabilityFloor = requiredCapabilityFloor(category)
        val capabilityDeficit = (capabilityFloor - candidate.profile.performanceTier).coerceAtLeast(0.0)
        val capabilityFloorPenalty = capabilityDeficit * 0.22

        val testedBonus = if (hasAnyTestedCandidate && candidate.isTested) 0.18 else 0.0

        val consecutiveFailurePenalty = (0.25 * candidate.stats.consecutiveFailures).coerceAtMost(0.75)
        val failureRatePenalty = if (candidate.stats.totalCalls >= 3) {
            (0.20 * candidate.stats.failureRate).coerceAtMost(0.20)
        } else {
            0.0
        }

        val total = (wPerf * perfScore) +
            (wCost * costEfficiency) +
            (wLatency * latencyScore) +
            localityBonus +
            testedBonus -
            capabilityFloorPenalty -
            consecutiveFailurePenalty -
            failureRatePenalty

        val reason = buildString {
            append(candidate.name)
            append(" [score=")
            append(((total * 1000).toInt() / 1000.0))
            append(", perf=")
            append(candidate.profile.performanceTier)
            append(", cost=$")
            append(candidate.profile.costPer1MTokens)
            append("/1M")
            if (candidate.isTested) append(", tested")
            if (candidate.stats.consecutiveFailures > 0) {
                append(", failures=")
                append(candidate.stats.consecutiveFailures)
            }
            append("]")
        }

        return ScoredRoutingCandidate(
            candidate = candidate,
            totalScore = total,
            reason = reason
        )
    }

    /**
     * Ranks all capable candidates for [category] in descending score order.
     */
    fun rankCandidates(
        candidates: List<RoutingCandidate>,
        category: TaskRoutingCategory
    ): List<ScoredRoutingCandidate> {
        if (candidates.isEmpty()) return emptyList()
        val hasAnyTested = candidates.any { it.isTested }
        return candidates
            .map { scoreCandidate(it, category, hasAnyTested) }
            .sortedWith(
                compareByDescending<ScoredRoutingCandidate> { it.totalScore }
                    .thenBy { it.candidate.profile.costPer1MTokens }
                    .thenBy { it.candidate.name.lowercase() }
            )
    }

    fun requiredCapabilityFloor(category: TaskRoutingCategory): Double {
        return when (category) {
            TaskRoutingCategory.LOCAL_TEXT_TRANSFORM,
            TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT,
            TaskRoutingCategory.LOCAL_DEVICE_ACTION -> 2.0

            TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL -> 3.2

            TaskRoutingCategory.CLOUD_DEEP_REASONING,
            TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT -> 4.2
        }
    }

    private fun parseCostTier(raw: String): Double? {
        val norm = raw.trim().uppercase()
        norm.toDoubleOrNull()?.let { if (it >= 0.0) return it }
        return when (norm) {
            "FREE", "LOCAL", "ZERO" -> 0.0
            "LOW", "ECONOMY", "CHEAP", "BUDGET" -> 0.30
            "MEDIUM", "BALANCED", "STANDARD" -> 1.50
            "HIGH", "PREMIUM", "FRONTIER" -> 5.00
            else -> null
        }
    }

    private fun parsePerformanceTier(raw: String): Double? {
        val norm = raw.trim().uppercase()
        norm.toDoubleOrNull()?.let { return it.coerceIn(1.0, 5.0) }
        return when (norm) {
            "LIGHT", "BASIC", "TINY" -> 2.2
            "FAST", "ECONOMY", "COMPACT" -> 3.5
            "BALANCED", "STANDARD" -> 4.0
            "STRONG", "HIGH" -> 4.5
            "FRONTIER", "REASONING", "MAX" -> 4.9
            else -> null
        }
    }
}
