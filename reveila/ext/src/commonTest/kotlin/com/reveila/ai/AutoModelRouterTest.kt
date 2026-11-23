package com.reveila.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoModelRouterTest {

    private fun candidate(
        name: String,
        type: String,
        model: String,
        providerField: String = "",
        isTested: Boolean = true,
        stats: ProviderRuntimeStats = ProviderRuntimeStats()
    ): RoutingCandidate {
        return RoutingCandidate(
            name = name,
            type = type,
            providerField = providerField,
            model = model,
            isTested = isTested,
            profile = AutoModelRouter.resolveProfile(type = type, model = model, providerField = providerField),
            stats = stats
        )
    }

    @Test
    fun localTasksPreferZeroCostOnDeviceGguf() {
        val candidates = listOf(
            candidate("On-Device Model", "Local", "qwen2.5-3b-instruct-q4_k_m.gguf"),
            candidate("Google Gemini", "Cloud", "gemini-2.5-flash", "Gemini"),
            candidate("OpenAI", "Cloud", "gpt-4o", "OpenAI")
        )

        val ranked = AutoModelRouter.rankCandidates(candidates, TaskRoutingCategory.LOCAL_TEXT_TRANSFORM)
        assertEquals("On-Device Model", ranked.first().candidate.name)
    }

    @Test
    fun deepReasoningPrefersCapableFrontierModelOverZeroCostLocalGguf() {
        val candidates = listOf(
            candidate("Local 3B", "Local", "qwen2.5-3b-instruct-q4_k_m.gguf"),
            candidate("Local 7B", "Local", "qwen2.5-7b-instruct-q4_k_m.gguf"),
            candidate("Local 14B", "Local", "qwen2.5-14b-instruct-q4_k_m.gguf"),
            candidate("Google Gemini Pro", "Cloud", "gemini-2.5-pro", "Gemini"),
            candidate("OpenAI", "Cloud", "gpt-4o", "OpenAI"),
            candidate("Anthropic Opus", "Cloud", "claude-3-opus", "Anthropic")
        )

        val ranked = AutoModelRouter.rankCandidates(candidates, TaskRoutingCategory.CLOUD_DEEP_REASONING)
        val topThree = ranked.take(3).map { it.candidate.name }.toSet()

        assertTrue(topThree.contains("OpenAI"))
        assertTrue(topThree.contains("Google Gemini Pro"))
        assertTrue(topThree.contains("Anthropic Opus"))
        // Even a $0 local 14B must not outrank frontier deep-reasoning models
        assertTrue(ranked.indexOfFirst { it.candidate.name == "Local 14B" } >= 3)
        assertTrue(ranked.indexOfFirst { it.candidate.name == "Local 7B" } > ranked.indexOfFirst { it.candidate.name == "Local 14B" })
        assertTrue(ranked.indexOfFirst { it.candidate.name == "Local 3B" } > ranked.indexOfFirst { it.candidate.name == "Local 7B" })
    }

    @Test
    fun deepReasoningBalancesCostAmongEquallyCapableModels() {
        val candidates = listOf(
            candidate("DeepSeek Reasoner", "Cloud", "deepseek-reasoner", "DeepSeek"),
            candidate("OpenAI", "Cloud", "gpt-4o", "OpenAI"),
            candidate("Anthropic Opus", "Cloud", "claude-3-opus", "Anthropic")
        )

        val ranked = AutoModelRouter.rankCandidates(candidates, TaskRoutingCategory.CLOUD_DEEP_REASONING)
        // DeepSeek Reasoner matches 4.8 capability at $1.20/1M vs $4.00/1M for gpt-4o
        assertEquals("DeepSeek Reasoner", ranked.first().candidate.name)
    }

    @Test
    fun liveRetrievalFavorsFastLowCostCapableModel() {
        val candidates = listOf(
            candidate("Google Gemini Flash", "Cloud", "gemini-2.5-flash", "Gemini"),
            candidate("OpenAI", "Cloud", "gpt-4o", "OpenAI"),
            candidate("Anthropic Opus", "Cloud", "claude-3-opus", "Anthropic")
        )

        val ranked = AutoModelRouter.rankCandidates(candidates, TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL)
        assertEquals("Google Gemini Flash", ranked.first().candidate.name)
    }

    @Test
    fun consecutiveFailuresDemoteUnhealthyProvider() {
        val failingReasoner = candidate(
            name = "DeepSeek Reasoner",
            type = "Cloud",
            model = "deepseek-reasoner",
            providerField = "DeepSeek",
            stats = ProviderRuntimeStats(failureCount = 2, consecutiveFailures = 2)
        )
        val healthyOpenAi = candidate("OpenAI", "Cloud", "gpt-4o", "OpenAI")

        val ranked = AutoModelRouter.rankCandidates(
            listOf(failingReasoner, healthyOpenAi),
            TaskRoutingCategory.CLOUD_DEEP_REASONING
        )
        assertEquals("OpenAI", ranked.first().candidate.name)
    }

    @Test
    fun resolveProfilePrefersModelPricingMapOverProviderCostAndPriors() {
        val pricing = mapOf(
            "deepseek-chat" to 0.35,
            "deepseek-reasoner" to 1.15
        )
        val reasonerProfile = AutoModelRouter.resolveProfile(
            type = "Cloud",
            model = "DeepSeek-Reasoner",
            providerField = "DeepSeek",
            userCostPer1MTokens = 0.40,
            modelPricing = pricing
        )
        assertEquals(1.15, reasonerProfile.costPer1MTokens)
        assertEquals("USER_CONFIG", reasonerProfile.source)

        val fallbackProfile = AutoModelRouter.resolveProfile(
            type = "Cloud",
            model = "deepseek-v4",
            providerField = "DeepSeek",
            userCostPer1MTokens = 0.40,
            modelPricing = pricing
        )
        assertEquals(0.40, fallbackProfile.costPer1MTokens)
        assertEquals("USER_CONFIG", fallbackProfile.source)
    }
}
