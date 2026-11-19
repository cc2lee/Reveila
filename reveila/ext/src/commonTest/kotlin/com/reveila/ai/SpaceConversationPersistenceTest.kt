package com.reveila.ai

import com.reveila.crypto.Cryptographer
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.service.DataService
import com.reveila.system.Constants
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.system.platform.PlatformSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpaceConversationPersistenceTest {

    private class RecordingLlmProvider(
        private val providerName: String = "TestCloudLLM",
        private val replyText: String = "Space-aware answer."
    ) : LlmProvider {
        val capturedRequests = mutableListOf<LlmRequest>()

        override fun invoke(request: LlmRequest): LlmResponse {
            capturedRequests.add(request)
            return LlmResponse(replyText, "stop", null, null, null)
        }

        override fun isEnabled(): Boolean = true
        override fun isConfigured(): Boolean = true
        override fun getName(): String = providerName
        override fun getSupportedIntents(): List<String> = TaskRoutingCategory.entries.map { it.name }
    }

    private class StubLlmProviderFactory(
        private val recordingProvider: LlmProvider
    ) : LlmProviderFactory() {
        override fun onStart() {
            // Avoid reading external llm.json on disk during unit test
        }

        override fun getActiveProvider(): LlmProvider = recordingProvider

        override fun getProvider(name: String?): LlmProvider = recordingProvider

        override fun isProviderConfigured(name: String?): Boolean = true

        override fun getProviderForIntent(intentCategory: String?): LlmProvider = recordingProvider

        override fun resolveWorkerProviderForIntent(
            intentCategory: String?,
            forceAuto: Boolean
        ): ProviderSelectionResult {
            val cat = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
            return ProviderSelectionResult(
                provider = recordingProvider,
                providerName = recordingProvider.getName(),
                isAutoMode = forceAuto,
                isCapable = true,
                intentCategory = cat
            )
        }

        override fun resolveExplicitProviderForIntent(
            providerName: String,
            intentCategory: String?
        ): ProviderSelectionResult {
            val cat = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
            return ProviderSelectionResult(
                provider = recordingProvider,
                providerName = recordingProvider.getName(),
                isAutoMode = false,
                isCapable = true,
                intentCategory = cat
            )
        }

        override fun resolveGovernanceProviderForIntent(intentCategory: String?): ProviderSelectionResult {
            val cat = TaskRoutingCategory.fromRawOrSafeDefault(intentCategory).name
            return ProviderSelectionResult(
                provider = null,
                providerName = "Disable",
                isAutoMode = false,
                isCapable = true,
                intentCategory = cat
            )
        }
    }

    private class SimpleProxy(
        private val componentName: String,
        private val target: Any
    ) : Proxy {
        override fun getName(): String = componentName
        override fun getRequiredRoles(): List<String> = emptyList()
        override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
        override fun getInstance(): Any = target
    }

    private fun cleanTestDir(dirPath: String) {
        val fs = PlatformFileSystem()
        if (fs.exists(dirPath)) {
            fs.delete(dirPath, recursive = true)
        }
        fs.createDirectories(dirPath)
    }

    private fun buildTestContext(
        dataDir: String,
        recordingProvider: LlmProvider
    ): Triple<Context, AgentSessionManager, OrchestrationService> {
        val props = Properties().apply {
            setProperty("data.dir", dataDir)
            setProperty("system.home", dataDir)
            setProperty("ai.worker.llm", recordingProvider.getName())
            setProperty("ai.assistant.llm", recordingProvider.getName())
            setProperty("ai.governance.llm", "Disable")
        }

        val dataService = DataService()
        val sessionManager = AgentSessionManager()
        val orchestrationService = OrchestrationService()
        val metadataRegistry = MetadataRegistry()
        val llmFactory = StubLlmProviderFactory(recordingProvider)
        val managedInvocation = object : ManagedInvocation() {
            override fun onStart() {}
        }

        val proxyMap = mutableMapOf<String, Proxy>(
            "DataService" to SimpleProxy("DataService", dataService),
            "AgentSessionManager" to SimpleProxy("AgentSessionManager", sessionManager),
            "OrchestrationService" to SimpleProxy("OrchestrationService", orchestrationService),
            "MetadataRegistry" to SimpleProxy("MetadataRegistry", metadataRegistry),
            "LlmProviderFactory" to SimpleProxy("LlmProviderFactory", llmFactory),
            "ManagedInvocation" to SimpleProxy("ManagedInvocation", managedInvocation)
        )

        val testContext = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy {
                return proxyMap[name] ?: throw IllegalArgumentException("Missing test proxy: $name")
            }

            override fun equals(other: Any?): Boolean = super.equals(other)
            override val properties: Properties = props
            override val platformAdapter: PlatformAdapter? = null
            override val cryptographer: Cryptographer? = null
        }

        listOf(dataService, sessionManager, orchestrationService, metadataRegistry, llmFactory, managedInvocation).forEach {
            it.context = testContext
            it.start()
        }

        return Triple(testContext, sessionManager, orchestrationService)
    }

    @Test
    fun testPersistedConversationsOrganizedAndFilteredBySpace() {
        val testDir = "build/test-data/spaces-db-${PlatformSystem.currentTimeMillis()}"
        cleanTestDir(testDir)
        try {
            val provider = RecordingLlmProvider()
            val (ctx, sessionManager, orchestration) = buildTestContext(testDir, provider)

            // Persist two distinct Spaces: "Research AI" and "Tax Planning"
            val sResearch1 = orchestration.getOrCreateSpaceSession("research-ai", "Research AI", "sess-research-1")
            sResearch1.addMessage(LlmRole.USER, "We decided to benchmark Qwen 2.5 3B for local inference.")
            sResearch1.addMessage(LlmRole.ASSISTANT, "Logged Qwen 2.5 3B benchmark decision for Research AI.")
            sResearch1.updateSummary("Benchmarking Qwen 2.5 3B local model performance.")
            sessionManager.saveSession(sResearch1.sessionId, sResearch1)

            val sTax = orchestration.getOrCreateSpaceSession("tax-planning", "Tax Planning", "sess-tax-1")
            sTax.addMessage(LlmRole.USER, "Remember my CPA deadline is April 15 with Form 1040.")
            sTax.addMessage(LlmRole.ASSISTANT, "Saved CPA deadline reminder for April 15.")
            sTax.updateSummary("2026 CPA tax filing deadline and Form 1040 checklist.")
            sessionManager.saveSession(sTax.sessionId, sTax)

            // Add a second session inside the SAME "research-ai" Space
            val sResearch2 = orchestration.createSession("sess-research-2", null, "research-ai", "Research AI")
            sResearch2.addMessage(LlmRole.USER, "Compare quantization Q4_K_M vs Q5_K_M latency.")
            sResearch2.addMessage(LlmRole.ASSISTANT, "Q4_K_M yields 25% faster token generation.")
            sessionManager.saveSession(sResearch2.sessionId, sResearch2)

            // Simulate complete app restart by building fresh DataService + AgentSessionManager + OrchestrationService on same DB dir
            val (_, restartedSm, restartedOrch) = buildTestContext(testDir, provider)

            val researchSessions = restartedSm.findSessionsBySpace("Research AI")
            assertEquals(2, researchSessions.size, "Expected 2 persisted sessions under research-ai space")
            assertTrue(researchSessions.all { it.spaceId == "research-ai" })

            val researchMessages = restartedSm.getFilteredMessagesForSpace("research-ai")
            assertEquals(4, researchMessages.size, "Should load all 4 research-ai messages across both sessions")
            assertTrue(researchMessages.any { it.content.contains("Qwen 2.5 3B") })
            assertTrue(researchMessages.any { it.content.contains("Q4_K_M yields 25%") })
            assertFalse(researchMessages.any { it.content.contains("Form 1040") || it.content.contains("April 15") })

            val taxMessages = restartedOrch.getSpaceHistory("tax-planning")
            assertEquals(2, taxMessages.size, "Expected strictly 2 messages in tax-planning space")
            assertTrue(taxMessages.all { it["spaceId"] == "tax-planning" })
            assertFalse(taxMessages.any { it["content"].toString().contains("Qwen 2.5") })

            val taxJsonViaStringLimit = restartedOrch.getSpaceHistoryJson("tax-planning", "100")
            assertTrue(taxJsonViaStringLimit.contains("Form 1040"))
            assertFalse(taxJsonViaStringLimit.contains("Qwen 2.5"))

            val spacesCatalog = restartedSm.listPersistedSpaces()
            assertTrue(spacesCatalog.any { it["spaceId"] == "research-ai" && (it["messageCount"] as Int) >= 2 })
            assertTrue(spacesCatalog.any { it["spaceId"] == "tax-planning" })
            assertEquals("Benchmarking Qwen 2.5 3B local model performance.", restartedSm.getSpaceSummary("research-ai"))

            // Verify clearing one space does not affect the other
            restartedOrch.clearSpace("tax-planning")
            assertEquals(0, restartedOrch.getSpaceHistory("tax-planning").size)
            assertEquals(4, restartedSm.getFilteredMessagesForSpace("research-ai").size)
            assertNotNull(ctx)
        } finally {
            val fs = PlatformFileSystem()
            if (fs.exists(testDir)) fs.delete(testDir, recursive = true)
        }
    }

    @Test
    fun testLlmPromptContextReceivesOnlySelectedSpaceHistoryAndSummary() {
        val testDir = "build/test-data/fabric-spaces-${PlatformSystem.currentTimeMillis()}"
        cleanTestDir(testDir)
        try {
            val recordingLlm = RecordingLlmProvider(replyText = "Continuing Apollo with Kotlin Multiplatform.")
            val (_, sessionManager, orchestration) = buildTestContext(testDir, recordingLlm)

            // Persist Space A: "Project Apollo" (with summary + historical messages)
            val apolloSession = orchestration.createSession("space-project-apollo", null, "project-apollo", "Project Apollo")
            apolloSession.updateSummary("Project Apollo uses Kotlin Multiplatform and Postgres.")
            apolloSession.addMessage(LlmRole.USER, "We finalized the KMP shared module architecture.")
            apolloSession.addMessage(LlmRole.ASSISTANT, "Confirmed KMP shared module architecture for Project Apollo.")
            sessionManager.saveSession(apolloSession.sessionId, apolloSession)

            // Persist Space B: "Weekend Cooking" (unrelated topic that must NEVER leak into Apollo context)
            val cookingSession = orchestration.createSession("space-weekend-cooking", null, "weekend-cooking", "Weekend Cooking")
            cookingSession.updateSummary("Spicy miso ramen recipe and chili oil notes.")
            cookingSession.addMessage(LlmRole.USER, "Add 2 tablespoons of gochujang and roasted garlic.")
            cookingSession.addMessage(LlmRole.ASSISTANT, "Recipe updated with gochujang and roasted garlic.")
            sessionManager.saveSession(cookingSession.sessionId, cookingSession)

            // Also test strict message-level filtering inside ReveilaChatMemory
            apolloSession.chatMemory.add(ReveilaMessage.user("Stray foreign cooking turn", spaceId = "weekend-cooking"))

            // Simulate fresh engine after app restart reading persisted DB only
            val (freshCtx, _, freshOrch) = buildTestContext(testDir, recordingLlm)
            val fabric = AgenticFabric().apply {
                context = freshCtx
                start()
            }

            val result = fabric.askAgentInSpace(
                userIntent = "What architecture did we finalize for this project?",
                spaceId = "project-apollo",
                spaceName = "Project Apollo",
                sessionId = null,
                systemPrompt = null,
                assistantMode = recordingLlm.getName()
            )

            assertEquals("Continuing Apollo with Kotlin Multiplatform.", result["answer"])
            assertEquals("project-apollo", result["spaceId"])
            assertEquals(1, recordingLlm.capturedRequests.size)

            val sentMessages = recordingLlm.capturedRequests.single().messages
            val joinedContext = sentMessages.joinToString("\n") { "${it.role.name}: ${it.content}" }

            assertTrue(
                joinedContext.contains("Project Apollo uses Kotlin Multiplatform and Postgres."),
                "Expected Apollo Space summary in LLM context: $joinedContext"
            )
            assertTrue(
                joinedContext.contains("Confirmed KMP shared module architecture"),
                "Expected Apollo conversation history in LLM context: $joinedContext"
            )
            assertFalse(
                joinedContext.contains("gochujang") || joinedContext.contains("miso ramen") || joinedContext.contains("Stray foreign cooking turn"),
                "Foreign Space history leaked into LLM context: $joinedContext"
            )

            // Verify new turn was persisted back under project-apollo in fresh engine DB
            val updatedApollo = freshOrch.getSpaceHistory("project-apollo")
            assertTrue(updatedApollo.any { it["content"] == "What architecture did we finalize for this project?" })
            assertTrue(updatedApollo.any { it["content"] == "Continuing Apollo with Kotlin Multiplatform." })
            assertFalse(updatedApollo.any { it["content"].toString().contains("gochujang") })
        } finally {
            val fs = PlatformFileSystem()
            if (fs.exists(testDir)) fs.delete(testDir, recursive = true)
        }
    }

    @Test
    fun testStreamingTurnPreparationFiltersBySpaceAndCommitsToSpaceDb() {
        val testDir = "build/test-data/stream-spaces-${PlatformSystem.currentTimeMillis()}"
        cleanTestDir(testDir)
        try {
            val localProvider = RecordingLlmProvider(providerName = "On-Device Model")
            val (_, sessionManager, orchestration) = buildTestContext(testDir, localProvider)

            val alpha = orchestration.createSession("space-alpha-audit", null, "alpha-audit", "Alpha Audit")
            alpha.updateSummary("HIPAA compliance review for Q4.")
            alpha.addMessage(LlmRole.USER, "Check vault encryption status.")
            alpha.addMessage(LlmRole.ASSISTANT, "Vault AES-GCM key rotation verified.")
            sessionManager.saveSession(alpha.sessionId, alpha)

            val beta = orchestration.createSession("space-beta-travel", null, "beta-travel", "Beta Travel")
            beta.addMessage(LlmRole.USER, "Book flights to Tokyo in November.")
            beta.addMessage(LlmRole.ASSISTANT, "Prepared Tokyo flight options.")
            sessionManager.saveSession(beta.sessionId, beta)

            val (freshCtx, _, freshOrch) = buildTestContext(testDir, localProvider)
            val fabric = AgenticFabric().apply {
                context = freshCtx
                start()
            }

            val prep = fabric.prepareStreamingTurnInSpace(
                userIntent = "Generate final audit summary.",
                spaceId = "alpha-audit",
                spaceName = "Alpha Audit",
                sessionId = null,
                systemPrompt = null,
                assistantMode = "On-Device Model"
            )

            val formattedPrompt = prep["formattedPrompt"]?.toString().orEmpty()
            val resolvedSessionId = prep["sessionId"]?.toString().orEmpty()
            assertEquals("alpha-audit", prep["spaceId"])
            assertTrue(resolvedSessionId.isNotBlank())
            assertTrue(formattedPrompt.contains("HIPAA compliance review for Q4."))
            assertTrue(formattedPrompt.contains("Vault AES-GCM key rotation verified."))
            assertFalse(formattedPrompt.contains("Tokyo") || formattedPrompt.contains("flights"))

            val committed = fabric.commitStreamingTurn(
                sessionId = resolvedSessionId,
                userIntent = "Generate final audit summary.",
                assistantAnswer = "Audit completed cleanly with AES-GCM verified.",
                spaceId = "alpha-audit",
                spaceName = "Alpha Audit"
            )
            assertTrue(committed)

            val (_, verifiedSm, verifiedOrch) = buildTestContext(testDir, localProvider)
            val alphaHistory = verifiedOrch.getSpaceHistory("alpha-audit")
            assertEquals(4, alphaHistory.size)
            assertTrue(alphaHistory.any { it["content"] == "Audit completed cleanly with AES-GCM verified." })
            assertEquals(2, verifiedSm.getFilteredMessagesForSpace("beta-travel").size)
        } finally {
            val fs = PlatformFileSystem()
            if (fs.exists(testDir)) fs.delete(testDir, recursive = true)
        }
    }

    @Test
    fun testSlidingWindowCompactsDbAndSpaceHistoryLimitsToLastSummary() {
        val testDir = "build/test-data/compact-spaces-${PlatformSystem.currentTimeMillis()}"
        cleanTestDir(testDir)
        try {
            val summaryReplyProvider = object : LlmProvider {
                override fun invoke(request: LlmRequest): LlmResponse =
                    LlmResponse("Summary: Discussed database size and Q4 mobile roadmap.", "stop", null, null, null)

                override fun isEnabled(): Boolean = true
                override fun isConfigured(): Boolean = true
                override fun getName(): String = "On-Device Model"
                override fun getSupportedIntents(): List<String> = TaskRoutingCategory.entries.map { it.name }
            }

            val (ctx, sessionManager, orchestration) = buildTestContext(testDir, summaryReplyProvider)
            val fabric = AgenticFabric().apply {
                context = ctx
                start()
            }

            // Seed 6 turns (3 user + 3 assistant) with ascending timestamps
            val session = orchestration.getOrCreateSpaceSession("mobile-opt", "Mobile Opt", "space-mobile-opt")
            for (i in 1..3) {
                session.addMessage(LlmRole.USER, "Old question $i", timestamp = 1000L + (i * 2))
                session.addMessage(LlmRole.ASSISTANT, "Old answer $i", timestamp = 1001L + (i * 2))
            }
            sessionManager.saveSession(session.sessionId, session)

            // Commit one more streaming turn (8 turns total > 6 max for On-Device Model) -> triggers compaction immediately
            val committed = fabric.commitStreamingTurn(
                sessionId = session.sessionId,
                userIntent = "Latest user question 4",
                assistantAnswer = "Latest assistant answer 4",
                spaceId = "mobile-opt",
                spaceName = "Mobile Opt"
            )
            assertTrue(committed)

            // Reload fresh engine from DB and verify history starts at the summary and drops old pre-summary turns
            val (_, _, freshOrch) = buildTestContext(testDir, summaryReplyProvider)
            val history = freshOrch.getSpaceHistory("mobile-opt", 100)
            assertEquals(4, history.size, "Expected 1 summary + 3 recent turns after compaction, got: $history")
            assertEquals("SYSTEM", history[0]["role"])
            assertTrue(history[0]["content"].toString().startsWith(AgentSession.CONVERSATION_SUMMARY_PREFIX))
            assertTrue(history[0]["content"].toString().contains("Discussed database size"))
            assertFalse(history.any { it["content"].toString().contains("Old question 1") })
            assertEquals("Latest assistant answer 4", history.last()["content"])

            // Archive disabled by default: no archive log file should be created
            val fs = PlatformFileSystem()
            val defaultArchivePath = fs.resolve(fs.resolve(testDir, "logs"), ChatHistoryArchiveWriter.ARCHIVE_FILE_NAME)
            assertFalse(fs.exists(defaultArchivePath), "Archive log should not exist when logging.chat.archive.enabled is false")
        } finally {
            val fs = PlatformFileSystem()
            if (fs.exists(testDir)) fs.delete(testDir, recursive = true)
        }
    }

    @Test
    fun testEnterpriseChatArchiveWriterCreatesAndRotatesJsonlLogs() {
        val testDir = "build/test-data/archive-logs-${PlatformSystem.currentTimeMillis()}"
        cleanTestDir(testDir)
        try {
            val fs = PlatformFileSystem()
            val summaryReplyProvider = object : LlmProvider {
                override fun invoke(request: LlmRequest): LlmResponse =
                    LlmResponse("Summary: Enterprise audit topic.", "stop", null, null, null)

                override fun isEnabled(): Boolean = true
                override fun isConfigured(): Boolean = true
                override fun getName(): String = "On-Device Model"
                override fun getSupportedIntents(): List<String> = TaskRoutingCategory.entries.map { it.name }
            }

            val (ctx, sessionManager, orchestration) = buildTestContext(testDir, summaryReplyProvider)
            ctx.properties?.setProperty(Constants.LOG_CHAT_ARCHIVE_ENABLED, "true")
            ctx.properties?.setProperty(Constants.LOG_CHAT_ARCHIVE_FILE_SIZE, "400")
            ctx.properties?.setProperty(Constants.LOG_CHAT_ARCHIVE_FILE_COUNT, "2")

            val fabric = AgenticFabric().apply {
                context = ctx
                start()
            }

            val session = orchestration.getOrCreateSpaceSession("audit-space", "Audit Space", "space-audit-space")
            for (i in 1..3) {
                session.addMessage(LlmRole.USER, "Compliance question $i with sufficient payload to test rotation", timestamp = 1000L + (i * 2))
                session.addMessage(LlmRole.ASSISTANT, "Compliance answer $i with sufficient payload to test rotation", timestamp = 1001L + (i * 2))
            }
            sessionManager.saveSession(session.sessionId, session)

            assertTrue(
                fabric.commitStreamingTurn(
                    sessionId = session.sessionId,
                    userIntent = "Compliance question 4",
                    assistantAnswer = "Compliance answer 4",
                    spaceId = "audit-space",
                    spaceName = "Audit Space"
                )
            )

            val logsDir = fs.resolve(testDir, "logs")
            val activeLog = fs.resolve(logsDir, ChatHistoryArchiveWriter.ARCHIVE_FILE_NAME)
            val rotatedLog1 = fs.resolve(logsDir, "${ChatHistoryArchiveWriter.ARCHIVE_FILE_NAME}.1")
            val rotatedLog2 = fs.resolve(logsDir, "${ChatHistoryArchiveWriter.ARCHIVE_FILE_NAME}.2")

            assertTrue(fs.exists(activeLog), "Expected active JSONL archive file at $activeLog")
            assertTrue(fs.exists(rotatedLog1), "Expected rotated backup .1 when size limit is small")
            assertFalse(fs.exists(rotatedLog2), "Total file count should be capped at logging.chat.archive.file.count=2")

            val combinedContent = fs.readText(rotatedLog1) + "\n" + fs.readText(activeLog)
            assertTrue(combinedContent.contains("\"spaceId\":\"audit-space\""))
            assertTrue(combinedContent.contains("\"compactedIntoSummary\":\"Summary: Enterprise audit topic.\""))
            assertTrue(combinedContent.contains("Compliance"))
        } finally {
            val fs = PlatformFileSystem()
            if (fs.exists(testDir)) fs.delete(testDir, recursive = true)
        }
    }
}
