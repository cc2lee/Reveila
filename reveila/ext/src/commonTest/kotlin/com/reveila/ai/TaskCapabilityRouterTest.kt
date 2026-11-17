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

    private fun createConfiguredFactory(
        workerName: String,
        governanceName: String
    ): LlmProviderFactory {
        val factory = object : LlmProviderFactory() {
            override fun getConfiguredWorkerName(): String = workerName
            override fun getConfiguredGovernanceName(): String = governanceName
            override fun getSettings(tab: String): String = """
                {
                  "ai.worker.llm": "$workerName",
                  "ai.governance.llm": "$governanceName",
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
        return factory
    }

    @Test
    fun testAutoWorkerUsesLocalFirstRuleAcrossAllCategories() {
        val factory = createConfiguredFactory(workerName = "Auto", governanceName = "Disable")
        assertTrue(factory.isAutoWorker())

        // LOCAL_* chooses On-Device Model even though OpenAI also declares LOCAL_TEXT_TRANSFORM
        val localResult = factory.resolveWorkerProviderForIntent("LOCAL_TEXT_TRANSFORM")
        assertTrue(localResult.isAutoMode)
        assertTrue(localResult.isCapable)
        assertEquals("On-Device Model", localResult.providerName)

        // CLOUD_* chooses matching Cloud LLM (OpenAI for live retrieval, DeepSeek for deep reasoning)
        val cloudLive = factory.resolveWorkerProviderForIntent("CLOUD_LIVE_RETRIEVAL")
        assertTrue(cloudLive.isAutoMode)
        assertTrue(cloudLive.isCapable)
        assertEquals("OpenAI", cloudLive.providerName)

        val cloudDeep = factory.resolveWorkerProviderForIntent("CLOUD_DEEP_REASONING")
        assertTrue(cloudDeep.isAutoMode)
        assertTrue(cloudDeep.isCapable)
        assertEquals("DeepSeek", cloudDeep.providerName)
    }

    @Test
    fun testExplicitWorkerEnforcedAndAlertsWhenIncapable() {
        val factory = createConfiguredFactory(workerName = "DeepSeek", governanceName = "Disable")
        assertFalse(factory.isAutoWorker())

        // Capable category succeeds with the explicitly chosen provider
        val capable = factory.resolveWorkerProviderForIntent("CLOUD_DEEP_REASONING")
        assertFalse(capable.isAutoMode)
        assertTrue(capable.isCapable)
        assertEquals("DeepSeek", capable.providerName)

        // Incapable category does NOT silently fall back; returns isCapable=false + alert message
        val incapable = factory.resolveWorkerProviderForIntent("CLOUD_LIVE_RETRIEVAL")
        assertFalse(incapable.isAutoMode)
        assertFalse(incapable.isCapable)
        assertEquals("DeepSeek", incapable.providerName)
        assertTrue(incapable.errorMessage?.contains("cannot handle task type 'CLOUD_LIVE_RETRIEVAL'") == true)
    }

    @Test
    fun testGovernanceResolutionSupportsDisableAutoAndExplicitAlert() {
        val disabled = createConfiguredFactory(workerName = "Auto", governanceName = "Disable")
            .resolveGovernanceProviderForIntent("CLOUD_LIVE_RETRIEVAL")
        assertTrue(disabled.isCapable)
        assertEquals("Disable", disabled.providerName)
        assertEquals(null, disabled.provider)

        val autoLocal = createConfiguredFactory(workerName = "Auto", governanceName = "Auto")
            .resolveGovernanceProviderForIntent("LOCAL_STRUCTURED_EXTRACT")
        assertTrue(autoLocal.isAutoMode)
        assertTrue(autoLocal.isCapable)
        assertEquals("On-Device Model", autoLocal.providerName)

        val explicitIncapable = createConfiguredFactory(workerName = "Auto", governanceName = "On-Device Model")
            .resolveGovernanceProviderForIntent("CLOUD_DEEP_REASONING")
        assertFalse(explicitIncapable.isAutoMode)
        assertFalse(explicitIncapable.isCapable)
        assertEquals("On-Device Model", explicitIncapable.providerName)
        assertTrue(explicitIncapable.errorMessage?.contains("cannot handle task type 'CLOUD_DEEP_REASONING'") == true)
    }

    @Test
    fun testSelfHostedProviderWithoutApiKeyAndDeletedDefaultProviders() {
        val factory = object : LlmProviderFactory() {
            override fun getSettings(tab: String): String = """
                {
                  "ai.worker.llm": "Ollama Local",
                  "ai.governance.llm": "Disable",
                  "onboarded.providers": [
                    {
                      "Name": "Ollama Local",
                      "Type": "Self-Hosted",
                      "Provider": "OpenAI",
                      "Endpoint": "http://localhost:11434/v1/chat/completions",
                      "Model": "llama3.2",
                      "Temperature": 0.7,
                      "Intent.Options": ["LOCAL_TEXT_TRANSFORM", "CLOUD_DEEP_REASONING"]
                    }
                  ]
                }
            """.trimIndent()
        }
        factory.loadProviders()

        // Deleted default cloud providers (OpenAI, DeepSeek, Qwen, Google Gemini) are NOT resurrected
        assertEquals(null, factory.getProvider("OpenAI"))
        assertEquals(null, factory.getProvider("DeepSeek"))

        // Self-Hosted provider is configured even with no API key
        assertTrue(factory.isProviderConfigured("Ollama Local"))
        assertEquals("Ollama Local", factory.getProviderForIntent("CLOUD_DEEP_REASONING")?.getName())
    }

    @Test
    fun testAssistantModeDecouplingAndTestedFingerprintGate() {
        var testedFp = ""
        val factory = object : LlmProviderFactory() {
            override fun getSettings(tab: String): String = """
                {
                  "ai.worker.llm": "Ollama Local",
                  "ai.governance.llm": "On-Device Model",
                  "ai.assistant.llm": "Auto",
                  "onboarded.providers": [
                    {
                      "Name": "On-Device Model",
                      "Type": "Local",
                      "Endpoint": "local://on-device",
                      "Model": "gemma-2-2b-it-Q4_K_M.gguf",
                      "Tested": true,
                      "Tested.Fingerprint": "$testedFp",
                      "Intent.Options": ["LOCAL_TEXT_TRANSFORM", "LOCAL_STRUCTURED_EXTRACT", "LOCAL_DEVICE_ACTION"]
                    },
                    {
                      "Name": "Ollama Local",
                      "Type": "Self-Hosted",
                      "Provider": "OpenAI",
                      "Endpoint": "http://localhost:11434/v1/chat/completions",
                      "Model": "llama3.2",
                      "Tested": false,
                      "Tested.Fingerprint": "",
                      "Intent.Options": ["LOCAL_TEXT_TRANSFORM", "CLOUD_DEEP_REASONING"]
                    }
                  ]
                }
            """.trimIndent()
        }

        val testResult = factory.testProviderConfigJson(
            """
            {
              "Name": "On-Device Model",
              "Type": "Local",
              "Endpoint": "local://on-device",
              "Model": "gemma-2-2b-it-Q4_K_M.gguf",
              "localModelReady": true
            }
            """.trimIndent()
        )
        val parsedResult = com.reveila.util.json.JsonUtil.parseJsonStringToMap(testResult)
        assertEquals(true, parsedResult["ok"])
        testedFp = parsedResult["fingerprint"]?.toString().orEmpty()
        assertTrue(testedFp.isNotBlank())

        factory.loadProviders()
        assertEquals("Auto", factory.getConfiguredAssistantName())
        assertTrue(factory.isProviderTested("On-Device Model"))
        assertFalse(factory.isProviderTested("Ollama Local"))

        val explicitSel = factory.resolveExplicitProviderForIntent("Ollama Local", "LOCAL_TEXT_TRANSFORM")
        assertTrue(explicitSel.isCapable)
        val missingSel = factory.resolveExplicitProviderForIntent("OpenAI", "CLOUD_DEEP_REASONING")
        assertFalse(missingSel.isCapable)
        assertTrue(missingSel.errorMessage?.contains("Studio -> Configuration") == true)
    }
}
