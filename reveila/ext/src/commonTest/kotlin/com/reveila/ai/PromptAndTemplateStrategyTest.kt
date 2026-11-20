package com.reveila.ai

import com.reveila.ai.util.GemmaPromptFormatter
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.service.DataService
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.system.platform.PlatformSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptAndTemplateStrategyTest {

    private class CapturingDelegate(
        private val modelPath: String,
        private val cannedReply: String
    ) : OnDeviceInferenceDelegate {
        var lastFormattedPrompt: String = ""
        var lastStopToken: String = ""

        override fun isModelLoaded(): Boolean = true
        override fun getLoadedModelPath(): String = modelPath

        override fun generateBlocking(
            formattedPrompt: String,
            stopToken: String,
            maxTokens: Int,
            grammar: String
        ): String {
            lastFormattedPrompt = formattedPrompt
            lastStopToken = stopToken
            return cannedReply
        }

        override fun generateBlocking(formattedPrompt: String, stopToken: String, maxTokens: Int): String =
            generateBlocking(formattedPrompt, stopToken, maxTokens, "")

        override fun generateStream(formattedPrompt: String, stopToken: String, maxTokens: Int): Flow<String> {
            lastFormattedPrompt = formattedPrompt
            lastStopToken = stopToken
            return flowOf(cannedReply)
        }

        override fun scheduleRelease() {}
    }

    private class SimpleProxy(
        private val name: String,
        private val instance: Any
    ) : Proxy {
        override fun getName(): String = name
        override fun getRequiredRoles(): List<String> = emptyList()
        override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
            if (instance is KnowledgeVault && methodName == "search") {
                val query = args?.getOrNull(0)?.toString().orEmpty()
                val limit = (args?.getOrNull(1) as? Number)?.toInt() ?: 3
                return instance.search(query, limit)
            }
            return null
        }

        override fun getInstance(): Any = instance
    }

    private fun resolveStandardSystemHome(): String {
        val fs = PlatformFileSystem()
        val candidates = listOf(
            "system-home/standard",
            "../system-home/standard",
            "../../system-home/standard"
        )
        return candidates.firstOrNull { fs.exists(fs.resolve(it, "configs/templates/prompts/default.md")) }
            ?: "system-home/standard"
    }

    @Test
    fun testKnowledgeVaultRelevanceGateSkipsUnrelatedPriorityDocsOnGeneralQuestions() {
        val vault = KnowledgeVault()
        vault.indexDocument(
            "Reveila_Sovereign_Overview.md",
            """
            # Reveila Sovereign AI Reference Document
            Focus areas: strategy, confidential architecture, multi-platform roadmap, zero cloud leaks.
            """.trimIndent()
        )

        val generalSearch = vault.search("How many moons does earth have?", 3)
        assertEquals("No relevant internal documents found.", generalSearch)

        val relevantSearch = vault.search("What is our multi-platform roadmap strategy?", 3)
        assertTrue(relevantSearch.contains("Reveila_Sovereign_Overview.md"))
        assertTrue(relevantSearch.contains("Priority Keyword Match"))

        // Multi-document overview query must cover distinct files beyond a single-doc chunk cap
        vault.indexDocument("Notes_Alpha.txt", "Alpha architecture notes: zero-copy inference pipeline and benchmark latency.")
        vault.indexDocument("Budget_Plan.md", "Q4 hardware budget approved for on-device test lab expansion.")
        val overviewSearch = vault.search("Summarize the files in my Knowledge Folder.", 2)
        assertTrue(overviewSearch.contains("Notes_Alpha.txt"))
        assertTrue(overviewSearch.contains("Budget_Plan.md"))

        // Scoped subfolder & root-only searches must filter to selected nested paths
        vault.indexDocument(
            "Architecture/design.md",
            "Modular capability slicer keeps JAR minimal.",
            mapOf("title" to "design.md", "relativePath" to "Architecture/design.md")
        )
        vault.indexDocument(
            "Finance/q4_costs.csv",
            "Compute cost allocation for GPU nodes.",
            mapOf("title" to "q4_costs.csv", "relativePath" to "Finance/q4_costs.csv")
        )
        vault.setSearchScope("Architecture/")
        val scopedArch = vault.search("Summarize the selected scope", 3)
        assertTrue(scopedArch.contains("Architecture/design.md"))
        assertFalse(scopedArch.contains("Finance/q4_costs.csv"))

        vault.setSearchScope("__ROOT__")
        val scopedRoot = vault.search("Summarize root files", 5)
        assertTrue(scopedRoot.contains("Notes_Alpha.txt"))
        assertFalse(scopedRoot.contains("Architecture/design.md"))

        vault.setSearchScope("")
        assertTrue(vault.getDocumentsJson().contains("Architecture/design.md"))
    }

    @Test
    fun testPromptAllowsGeneralKnowledgeAndAvoidsVaultPrison() {
        val legacyPrompt = Prompt.getSystemPrompt(
            role = "Reveila Sovereign AI Agent",
            context = "Available Tools: \n\nRelated Documents: [Doc 1]: [notes.md] Project Apollo uses KMP.",
            constraints = "Preserve confidentiality."
        )
        assertFalse(legacyPrompt.contains("without attempting to answer the task"))
        assertTrue(legacyPrompt.contains("ignore any unrelated excerpts") || legacyPrompt.contains("general knowledge"))

        val noVaultPrompt = Prompt.buildSystemPrompt(
            role = "Aegin",
            spaceName = "Main Space",
            tools = emptyList(),
            vaultContext = "No relevant internal documents found.",
            modelName = "gpt-4o",
            systemHome = resolveStandardSystemHome(),
            mode = PromptMode.AGENTIC_JSON
        )
        assertFalse(noVaultPrompt.systemPrompt.contains("<vault_context>"))
        assertFalse(noVaultPrompt.systemPrompt.contains("\"tool-call\""))
    }

    @Test
    fun testModelSpecificAndFamilyAndGenericTemplateResolution() {
        val stdHome = resolveStandardSystemHome()

        // 1. Exact/stem match for qwen2.5-0.5b-instruct-q4_k_m.gguf
        val qwenTiny = PromptTemplateRegistry.resolvePromptTemplate(
            modelName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            systemHome = stdHome,
            mode = PromptMode.CONVERSATIONAL
        )
        assertEquals("models/qwen2.5-0.5b-instruct", qwenTiny.templateId)
        assertEquals(ChatFormat.CHATML, qwenTiny.chatFormat)
        assertEquals("<|im_end|>", qwenTiny.stopToken)
        assertEquals(4, qwenTiny.maxHistoryMessages)

        // 2. Case-insensitive stem match for gemma-2-2b-it-Q4_K_M.gguf
        val gemma2b = PromptTemplateRegistry.resolvePromptTemplate(
            modelName = "gemma-2-2b-it-Q4_K_M.gguf",
            systemHome = stdHome,
            mode = PromptMode.CONVERSATIONAL
        )
        assertEquals("models/gemma-2-2b-it", gemma2b.templateId)
        assertEquals(ChatFormat.GEMMA, gemma2b.chatFormat)
        assertEquals("<end_of_turn>", gemma2b.stopToken)

        // 3. Family match for qwen2.5-3b-instruct-q4_k_m.gguf
        val qwen3b = PromptTemplateRegistry.resolvePromptTemplate(
            modelName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            systemHome = stdHome,
            mode = PromptMode.CONVERSATIONAL
        )
        assertEquals("families/qwen2.5", qwen3b.templateId)

        // 4. Generic fallback for unknown model
        val genericConv = PromptTemplateRegistry.resolvePromptTemplate(
            modelName = "totally-unknown-model-v1.gguf",
            systemHome = stdHome,
            mode = PromptMode.CONVERSATIONAL
        )
        assertEquals("conversational", genericConv.templateId)

        val genericAgentic = PromptTemplateRegistry.resolvePromptTemplate(
            modelName = "gpt-4o",
            systemHome = stdHome,
            mode = PromptMode.AGENTIC_JSON
        )
        assertEquals("default", genericAgentic.templateId)
    }

    @Test
    fun testCustomEditablePromptTemplateWithCustomMarkersAndFallback() {
        val fs = PlatformFileSystem()
        val tempHome = "build/test-data/custom-prompt-${PlatformSystem.currentTimeMillis()}"
        try {
            val modelsDir = fs.resolve(tempHome, "configs/templates/prompts/models")
            fs.createDirectories(modelsDir)
            fs.writeText(
                fs.resolve(modelsDir, "nano-custom-1b.md"),
                """
                ---
                chat_format: custom
                stop_token: <|stop_nano|>
                system_prefix: "### SYS\n"
                system_suffix: "\n### END\n"
                user_prefix: "### USR\n"
                user_suffix: "\n### END\n"
                assistant_generation_prefix: "### AST\n"
                response_mode: conversational
                parameter_size_b: 1.0
                ---
                You are Nano in {{space_name}}.
                """.trimIndent()
            )

            val rendered = Prompt.buildSystemPrompt(
                spaceName = "Physics",
                modelName = "nano-custom-1b-q4_k_m.gguf",
                systemHome = tempHome,
                mode = PromptMode.CONVERSATIONAL
            )
            assertEquals("models/nano-custom-1b", rendered.resolvedTemplate.templateId)
            assertTrue(rendered.systemPrompt.contains("You are Nano in Physics."))

            val (formatted, stopTok) = LocalLlamaProvider.formatMessages(
                messages = listOf(
                    ReveilaMessage.system(rendered.systemPrompt),
                    ReveilaMessage.user("How many moons does earth have?")
                ),
                modelName = "nano-custom-1b-q4_k_m.gguf",
                systemHome = tempHome
            )
            assertEquals("<|stop_nano|>", stopTok)
            assertTrue(formatted.startsWith("### SYS\nYou are Nano in Physics.\n### END\n### USR\nHow many moons does earth have?\n### END\n### AST\n"))
        } finally {
            if (fs.exists(tempHome)) fs.delete(tempHome, recursive = true)
        }
    }

    @Test
    fun testGemmaPromptFormatterMergesSystemIntoSingleFirstUserTurn() {
        val formatted = GemmaPromptFormatter.format(
            listOf(
                ReveilaMessage.system("Be concise."),
                ReveilaMessage.user("How many moons does earth have?"),
                ReveilaMessage.assistant("Earth has 1 moon."),
                ReveilaMessage.user("And Mars?")
            )
        )
        assertEquals(
            "<start_of_turn>user\nBe concise.\n\nHow many moons does earth have?<end_of_turn>\n" +
                "<start_of_turn>model\nEarth has 1 moon.<end_of_turn>\n" +
                "<start_of_turn>user\nAnd Mars?<end_of_turn>\n" +
                "<start_of_turn>model\n",
            formatted
        )
    }

    @Test
    fun testSmallLocalModelInAskAgentUsesConversationalTemplateAndUnwrapsTrackedProvider() {
        val fs = PlatformFileSystem()
        val tempHome = "build/test-data/fabric-prompt-${PlatformSystem.currentTimeMillis()}"
        fs.createDirectories(tempHome)
        val stdPrompts = fs.resolve(resolveStandardSystemHome(), "configs/templates/prompts")
        // Copy editable prompts into tempHome to verify real disk resolution
        val targetPrompts = fs.resolve(tempHome, "configs/templates/prompts")
        for (rel in fs.listRelativePaths(stdPrompts, "md")) {
            val dst = fs.resolve(targetPrompts, rel)
            fs.createDirectories(dst.substringBeforeLast('/').substringBeforeLast('\\'))
            fs.writeText(dst, fs.readText(fs.resolve(stdPrompts, rel)))
        }

        val delegate = CapturingDelegate(
            modelPath = "/models/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            cannedReply = "Earth has 1 moon. In Kotlin: fun moons() { return 1 }"
        )
        LocalLlamaProvider.registerNativeDelegate(delegate)
        try {
            val props = Properties().apply {
                setProperty("system.home", tempHome)
                setProperty("data.dir", tempHome)
                setProperty("ai.worker.llm", "On-Device Model")
                setProperty("ai.assistant.llm", "On-Device Model")
                setProperty("ai.governance.llm", "Disable")
                setProperty("ai.llm.model.name", "qwen2.5-0.5b-instruct-q4_k_m.gguf")
            }

            val dataService = DataService()
            val sessionManager = AgentSessionManager()
            val orchestrationService = OrchestrationService()
            val metadataRegistry = MetadataRegistry()
            val usageTracker = UsageTracker()
            val vault = KnowledgeVault().apply {
                indexDocument(
                    "Reveila_Sovereign_Overview.md",
                    "# Reveila Overview\nFocus: strategy, roadmap, confidential architecture."
                )
            }

            val localProvider = LocalLlamaProvider().apply {
                setName("On-Device Model")
                model = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
                intentOptions = LlmProviderFactory.DEFAULT_LOCAL_INTENTS
            }
            val trackedProvider = TrackedLlmProvider(localProvider, usageTracker)

            val stubFactory = object : LlmProviderFactory() {
                override fun onStart() {}
                override fun getActiveProvider(): LlmProvider = trackedProvider
                override fun getProvider(name: String?): LlmProvider = trackedProvider
                override fun resolveWorkerProviderForIntent(
                    intentCategory: String?,
                    forceAuto: Boolean
                ): ProviderSelectionResult = ProviderSelectionResult(
                    provider = trackedProvider,
                    providerName = "On-Device Model",
                    isAutoMode = forceAuto,
                    isCapable = true,
                    intentCategory = TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name
                )
                override fun resolveExplicitProviderForIntent(
                    providerName: String,
                    intentCategory: String?
                ): ProviderSelectionResult = ProviderSelectionResult(
                    provider = trackedProvider,
                    providerName = "On-Device Model",
                    isAutoMode = false,
                    isCapable = true,
                    intentCategory = TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name
                )
                override fun resolveGovernanceProviderForIntent(intentCategory: String?): ProviderSelectionResult =
                    ProviderSelectionResult(null, "Disable", false, true, TaskRoutingCategory.LOCAL_TEXT_TRANSFORM.name)
            }

            val managedInvocation = object : ManagedInvocation() {
                override fun onStart() {}
            }

            val proxies = mapOf<String, Proxy>(
                "DataService" to SimpleProxy("DataService", dataService),
                "AgentSessionManager" to SimpleProxy("AgentSessionManager", sessionManager),
                "OrchestrationService" to SimpleProxy("OrchestrationService", orchestrationService),
                "MetadataRegistry" to SimpleProxy("MetadataRegistry", metadataRegistry),
                "KnowledgeVault" to SimpleProxy("KnowledgeVault", vault),
                "LlmProviderFactory" to SimpleProxy("LlmProviderFactory", stubFactory),
                "ManagedInvocation" to SimpleProxy("ManagedInvocation", managedInvocation)
            )
            val testContext = object : Context {
                override fun getLogger(): PlatformLogger? = null
                override fun getProxy(name: String): Proxy =
                    proxies[name] ?: throw IllegalArgumentException("Missing proxy $name")
                override val properties: Properties = props
                override val platformAdapter: PlatformAdapter? = null
                override val cryptographer: com.reveila.crypto.Cryptographer? = null
            }

            listOf(dataService, sessionManager, orchestrationService, metadataRegistry, vault, usageTracker, localProvider, stubFactory, managedInvocation).forEach {
                it.context = testContext
                it.start()
            }

            val fabric = AgenticFabric().apply {
                context = testContext
                start()
            }

            val res = fabric.askAgent("How many moons does earth have?", null, null)
            assertEquals("Earth has 1 moon. In Kotlin: fun moons() { return 1 }", res["answer"])
            assertTrue(delegate.lastFormattedPrompt.contains("<|im_start|>system"))
            assertFalse(delegate.lastFormattedPrompt.contains("Return your response as a valid JSON document"))
            assertFalse(delegate.lastFormattedPrompt.contains("Reveila_Sovereign_Overview.md"))
            assertEquals("<|im_end|>", delegate.lastStopToken)

            // Verify switching model to Gemma 2 2B unwraps TrackedLlmProvider during streaming prep
            localProvider.model = "gemma-2-2b-it-Q4_K_M.gguf"
            val prep = fabric.prepareStreamingTurnWithMode(
                userIntent = "How many moons does earth have?",
                sessionId = null,
                systemPrompt = null,
                assistantMode = "On-Device Model"
            )
            val streamPrompt = prep["formattedPrompt"].orEmpty()
            assertTrue(streamPrompt.startsWith("<start_of_turn>user\n"))
            assertFalse(streamPrompt.contains("<|im_start|>"))
            assertEquals("<end_of_turn>", prep["stopToken"])
            assertEquals("models/gemma-2-2b-it", prep["templateId"])
        } finally {
            LocalLlamaProvider.registerNativeDelegate(null)
            if (fs.exists(tempHome)) fs.delete(tempHome, recursive = true)
        }
    }

    @Test
    fun testPromptModeSelectionRespectsSmallLocalVsToolsAndCloud() {
        val stdHome = resolveStandardSystemHome()
        assertEquals(
            PromptMode.CONVERSATIONAL,
            PromptTemplateRegistry.resolvePromptMode(
                isLocalProvider = true,
                modelName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                hasTools = false,
                hasGrammar = false,
                systemHome = stdHome
            )
        )
        assertEquals(
            PromptMode.CONVERSATIONAL,
            PromptTemplateRegistry.resolvePromptMode(
                isLocalProvider = true,
                modelName = "gemma-2-2b-it-Q4_K_M.gguf",
                hasTools = false,
                hasGrammar = false,
                systemHome = stdHome
            )
        )
        assertEquals(
            PromptMode.AGENTIC_JSON,
            PromptTemplateRegistry.resolvePromptMode(
                isLocalProvider = true,
                modelName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                hasTools = false,
                hasGrammar = false,
                systemHome = stdHome
            )
        )
        assertEquals(
            PromptMode.AGENTIC_JSON,
            PromptTemplateRegistry.resolvePromptMode(
                isLocalProvider = true,
                modelName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                hasTools = true,
                hasGrammar = false,
                systemHome = stdHome
            )
        )
        assertEquals(
            PromptMode.AGENTIC_JSON,
            PromptTemplateRegistry.resolvePromptMode(
                isLocalProvider = false,
                modelName = "gpt-4o",
                hasTools = false,
                hasGrammar = false,
                systemHome = stdHome
            )
        )

        // Verify fallback to embedded generic templates when systemHome points to an empty directory
        val emptyHome = "build/test-data/empty-home-${PlatformSystem.currentTimeMillis()}"
        val fs = PlatformFileSystem()
        fs.createDirectories(emptyHome)
        try {
            val (phiFormatted, phiStop) = LocalLlamaProvider.formatMessages(
                listOf(ReveilaMessage.system("Hi"), ReveilaMessage.user("2+2?")),
                "phi-3-mini-4k-instruct.gguf",
                emptyHome
            )
            assertEquals("<|end|>", phiStop)
            assertTrue(phiFormatted.contains("<|system|>\nHi<|end|>"))

            val (mistralFormatted, mistralStop) = LocalLlamaProvider.formatMessages(
                listOf(ReveilaMessage.system("Hi"), ReveilaMessage.user("2+2?")),
                "mistral-7b-instruct-v0.3.gguf",
                emptyHome
            )
            assertEquals("</s>", mistralStop)
            assertTrue(mistralFormatted.startsWith("<s>[INST] Hi\n\n2+2? [/INST]"))
            val renderedPrompt = Prompt.buildSystemPrompt(
                role = "Agent",
                spaceName = "Assistant",
                tools = listOf(LlmTool().apply {
                    name = "virtual_browser"
                    description = "Search and browse web"
                }),
                modelName = "gpt-4o",
                systemHome = stdHome,
                mode = PromptMode.AGENTIC_JSON
            )
            assertTrue(renderedPrompt.systemPrompt.contains("""{"name": "<tool_name>", "arguments": {<tool_parameters>}}"""))
        } finally {
            if (fs.exists(emptyHome)) fs.delete(emptyHome, recursive = true)
        }
    }
}
