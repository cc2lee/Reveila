package com.reveila.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskCapabilityRouterTest {

    @Test
    fun testStep1ExplicitOverrideLocalSucceedsForOfflineTask() {
        val assessment = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Summarize my local meeting notes into 3 bullet points.",
            executionTarget = "local"
        )
        assertEquals("local", assessment.executionTarget)
        assertEquals(TaskRoutingCategory.LOCAL_TEXT_TRANSFORM, assessment.category)
        assertFalse(assessment.routeToCloud)
        assertFalse(assessment.hasCapabilityConflict)
        assertEquals("EXPLICIT_OVERRIDE_LOCAL", assessment.routingStage)
    }

    @Test
    fun testStep1ExplicitOverrideLocalFailsFastWhenLiveInternetRequired() {
        val assessment = TaskCapabilityRouter.assessTask(
            intent = "news.search",
            prompt = "Find the latest news about AI security and provide a summary.",
            executionTarget = "local"
        )
        assertTrue(assessment.hasCapabilityConflict)
        assertEquals("EXPLICIT_OVERRIDE_CONFLICT", assessment.routingStage)
        assertEquals(TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL, assessment.category)
    }

    @Test
    fun testStep1ExplicitOverrideCloudForcesCloudRoute() {
        val assessment = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Summarize my local meeting notes.",
            executionTarget = "cloud"
        )
        assertEquals("cloud", assessment.executionTarget)
        assertTrue(assessment.routeToCloud)
        assertEquals("EXPLICIT_OVERRIDE_CLOUD", assessment.routingStage)
    }

    @Test
    fun testStep2StaticIntentRegistryShortCircuitsInO1() {
        val newsAssessment = TaskCapabilityRouter.assessTask(
            intent = "news.search",
            prompt = "Find the latest news about AI security and provide a summary.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL, newsAssessment.category)
        assertTrue(newsAssessment.routeToCloud)
        assertEquals("STATIC_INTENT_REGISTRY", newsAssessment.routingStage)

        val vaultAssessment = TaskCapabilityRouter.assessTask(
            intent = "vault.summarize",
            prompt = "Summarize the key architectural decisions from my indexed Reference Documents.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.LOCAL_TEXT_TRANSFORM, vaultAssessment.category)
        assertFalse(vaultAssessment.routeToCloud)
        assertEquals("STATIC_INTENT_REGISTRY", vaultAssessment.routingStage)
    }

    @Test
    fun testStep3PreprocessorClassifiesIntoSixCategoryTaxonomy() {
        val liveWeb = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "What is the latest bitcoin price today and breaking market news?",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL, liveWeb.category)
        assertTrue(liveWeb.routeToCloud)

        val localExtract = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Extract dates and amounts from my local notification log.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT, localExtract.category)
        assertFalse(localExtract.routeToCloud)

        val localDevice = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Toggle a setting and query local notes.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.LOCAL_DEVICE_ACTION, localDevice.category)
        assertFalse(localDevice.routeToCloud)

        val deepReason = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Analyze a multi-page contract and debug a stack trace.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.CLOUD_DEEP_REASONING, deepReason.category)
        assertTrue(deepReason.routeToCloud)

        val multiStep = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = "Research three vendors across apps and compile a comparison report.",
            executionTarget = "auto"
        )
        assertEquals(TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT, multiStep.category)
        assertTrue(multiStep.routeToCloud)
    }

    @Test
    fun testSafetyRuleDefaultsUnknownCategoryToCloudDeepReasoning() {
        val fallback = TaskRoutingCategory.fromRawOrSafeDefault("UNKNOWN_HALLUCINATED_CATEGORY")
        assertEquals(TaskRoutingCategory.CLOUD_DEEP_REASONING, fallback)
        assertTrue(fallback.isCloudRoute)
    }

    @Test
    fun testGbnfGrammarEnforcesClosedSixCategories() {
        val gbnf = TaskCapabilityRouter.TASK_ROUTING_GBNF_GRAMMAR
        for (cat in TaskRoutingCategory.entries) {
            assertTrue(gbnf.contains(cat.name), "GBNF grammar missing category ${cat.name}")
        }
    }

    @Test
    fun testLlmProviderFactorySelectsProviderByIntentWithLocalPreference() {
        val factory = object : LlmProviderFactory() {
            override fun getSettings(tab: String): String = """
                {
                  "onboarded.providers": [
                    {
                      "Name": "OpenAI",
                      "Type": "Cloud",
                      "Provider": "OpenAI",
                      "Endpoint": "https://api.openai.com/v1/chat/completions",
                      "Model": "gpt-4o",
                      "Temperature": 0.7,
                      "api.key": "sk-test-openai-key",
                      "Intent.Options": ["CLOUD_LIVE_RETRIEVAL", "LOCAL_TEXT_TRANSFORM"]
                    },
                    {
                      "Name": "DeepSeek",
                      "Type": "Cloud",
                      "Provider": "DeepSeek",
                      "Endpoint": "https://api.deepseek.com/chat/completions",
                      "Model": "deepseek-reasoner",
                      "Temperature": 0.7,
                      "api.key": "sk-test-deepseek-key",
                      "Intent.Options": ["CLOUD_DEEP_REASONING", "CLOUD_MULTI_STEP_AGENT"]
                    },
                    {
                      "Name": "On-Device Model",
                      "Type": "Local",
                      "Endpoint": "local://on-device",
                      "Model": "gemma-2-2b-it-Q4_K_M.gguf",
                      "Temperature": 0.7,
                      "Intent.Options": ["LOCAL_TEXT_TRANSFORM", "LOCAL_STRUCTURED_EXTRACT", "LOCAL_DEVICE_ACTION"]
                    }
                  ]
                }
            """.trimIndent()
        }
        factory.loadProviders()

        // 1. Both OpenAI and On-Device Model support LOCAL_TEXT_TRANSFORM -> On-Device Model wins (higher preference)
        val localSelected = factory.getProviderForIntent("LOCAL_TEXT_TRANSFORM")
        assertEquals("On-Device Model", localSelected?.getName())

        // 2. Only OpenAI supports CLOUD_LIVE_RETRIEVAL -> OpenAI is selected
        val liveSelected = factory.getProviderForIntent("CLOUD_LIVE_RETRIEVAL")
        assertEquals("OpenAI", liveSelected?.getName())

        // 3. Only DeepSeek supports CLOUD_DEEP_REASONING -> DeepSeek is selected
        val deepSelected = factory.getProviderForIntent("CLOUD_DEEP_REASONING")
        assertEquals("DeepSeek", deepSelected?.getName())
    }
}
