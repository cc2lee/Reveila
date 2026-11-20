package com.reveila.aegin.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AeginDisplayMapperTest {

    @Test
    fun testTaskStatusesAndKeyValueForms() {
        assertEquals("Completed", AeginDisplayMapper.taskStatus("COMPLETED"))
        assertEquals("Completed", AeginDisplayMapper.taskStatus("status=completed"))
        assertEquals("Running", AeginDisplayMapper.taskStatus("RUNNING"))
        assertEquals("Needs Approval", AeginDisplayMapper.taskStatus("WAITING_APPROVAL"))
        assertEquals("Paused", AeginDisplayMapper.taskStatus("SUSPENDED"))
        assertEquals("Paused", AeginDisplayMapper.taskStatus("PAUSED"))
        assertEquals("Stopped", AeginDisplayMapper.taskStatus("KILLED"))
        assertEquals("Declined", AeginDisplayMapper.taskStatus("REJECTED"))
        assertEquals("Failed", AeginDisplayMapper.taskStatus("FAILED"))
        assertEquals("Custom Pending State", AeginDisplayMapper.taskStatus("CUSTOM_PENDING_STATE"))
    }

    @Test
    fun testFrequenciesTargetsAndCategories() {
        assertEquals("One-Time", AeginDisplayMapper.taskFrequency("ONCE"))
        assertEquals("Every 15 Min", AeginDisplayMapper.taskFrequency("EVERY 15M"))
        assertEquals("Hourly", AeginDisplayMapper.taskFrequency("hourly"))
        assertEquals("Daily", AeginDisplayMapper.taskFrequency("DAILY"))
        assertEquals("Weekly", AeginDisplayMapper.taskFrequency("weekly"))

        assertEquals("Smart Auto", AeginDisplayMapper.executionTarget("auto"))
        assertEquals("On-Device", AeginDisplayMapper.executionTarget("local"))
        assertEquals("Cloud AI", AeginDisplayMapper.executionTarget("cloud"))

        assertEquals("Summarize & Rewrite", AeginDisplayMapper.taskCategory("LOCAL_TEXT_TRANSFORM"))
        assertEquals("Deep Analysis", AeginDisplayMapper.taskCategory("CLOUD_DEEP_REASONING"))
        assertEquals("Summarize & Rewrite", AeginBridge.formatIntentLabel("LOCAL_TEXT_TRANSFORM"))
    }

    @Test
    fun testAuditActionsAndDetailsSanitization() {
        assertEquals("Task Completed", AeginDisplayMapper.auditAction("TASK_COMPLETED"))
        assertEquals("Approval Requested", AeginDisplayMapper.auditAction("TASK_WAITING_APPROVAL"))
        assertEquals("Emergency Stop Activated", AeginDisplayMapper.auditAction("KILL_SWITCH_EMERGENCY_STOP_ALL"))
        assertEquals("Unsafe Request Blocked", AeginDisplayMapper.auditAction("SECURITY_BREACH_PROMPT_BLOCKED"))

        val rawDetails = "Created task [task-99] (Daily Brief) - executionTarget=auto, category=CLOUD_LIVE_RETRIEVAL"
        val clean = AeginDisplayMapper.auditDetails(rawDetails)
        assertFalse(clean.contains("task-99"))
        assertFalse(clean.contains("executionTarget="))
        assertFalse(clean.contains("CLOUD_LIVE_RETRIEVAL"))
        assertTrue(clean.contains("Smart Auto"))
        assertTrue(clean.contains("Live Web Info"))
    }

    @Test
    fun testTaskStepsDownloadAndDeviceTiers() {
        assertEquals("Waiting for your approval", AeginDisplayMapper.taskStep("Paused at HITL governance gate"))
        assertEquals("Step 1 of 3 • Preparing task...", AeginDisplayMapper.taskStep("Stage 1/3 • Preprocessor [LOCAL_HEURISTIC] -> ☁️ CLOUD_DEEP_REASONING"))
        assertEquals("Step 2 of 3 • Searching your documents...", AeginDisplayMapper.taskStep("Stage 2/3 • Searching Reference Documents & building context"))
        assertEquals("Running in the background...", AeginDisplayMapper.taskStep("", isSuspended = false))
        assertEquals("Paused by you.", AeginDisplayMapper.taskStep("", isSuspended = true))

        assertEquals("Downloading", AeginDisplayMapper.downloadStatus("DOWNLOADING"))
        assertEquals("Downloaded", AeginDisplayMapper.downloadStatus("COMPLETED"))
        assertEquals("Download Failed", AeginDisplayMapper.downloadStatus("FAILED"))

        assertEquals("Capable (8 – 12 RAM)".replace("RAM", "GB RAM"), AeginDisplayMapper.hardwareTier("STANDARD TIER (8 – 12 GB RAM)"))
        assertEquals("Qwen 2.5 • Smart (3B)", AeginDisplayMapper.modelDisplayName(ModelTierCatalog.MODEL_3B))
    }

    @Test
    fun testProviderAndTemplateDisplayNames() {
        assertEquals("Smart Auto", AeginDisplayMapper.providerDisplayName("Auto"))
        assertEquals("Off", AeginDisplayMapper.providerDisplayName("Disable"))
        assertEquals("On-Device Model", AeginDisplayMapper.providerDisplayName("On-Device Model"))
        assertEquals("Google Gemini", AeginDisplayMapper.providerDisplayName("Google Gemini"))

        assertEquals("Document Monitor", AeginDisplayMapper.spaceTemplateCategory("RAG MONITOR"))
        assertEquals("Daily Summary", AeginDisplayMapper.spaceTemplateCategory("DAILY BRIEF"))
        assertEquals("Privacy Check", AeginDisplayMapper.spaceTemplateCategory("GUARDRAIL"))
        assertEquals("Roadmap", AeginDisplayMapper.spaceTemplateCategory("STRATEGY"))
    }

    @Test
    fun testSelectRecentSpacesForStrip() {
        val spaces = listOf(
            SpaceThreadSummary(spaceId = "main-space", spaceName = "Main Space", updatedAt = 50L),
            SpaceThreadSummary(spaceId = "alpha", spaceName = "Alpha", updatedAt = 100L),
            SpaceThreadSummary(spaceId = "beta", spaceName = "Beta", updatedAt = 400L),
            SpaceThreadSummary(spaceId = "gamma", spaceName = "Gamma", updatedAt = 300L),
            SpaceThreadSummary(spaceId = "delta", spaceName = "Delta", updatedAt = 200L)
        )

        val top3WithBetaActive = AeginBridge.selectRecentSpacesForStrip(
            persistedSpaces = spaces,
            activeSpaceId = "beta",
            activeSpaceName = "Beta",
            maxCount = 3
        )
        assertEquals(listOf("main-space", "beta", "gamma"), top3WithBetaActive.map { it.spaceId })
        assertEquals("Assistant", top3WithBetaActive.first().spaceName)

        // Active space outside top 2 other spaces is retained in the trailing 3rd slot while Assistant stays leftmost
        val top3WithAlphaActive = AeginBridge.selectRecentSpacesForStrip(
            persistedSpaces = spaces,
            activeSpaceId = "alpha",
            activeSpaceName = "Alpha",
            maxCount = 3
        )
        assertEquals(listOf("main-space", "beta", "alpha"), top3WithAlphaActive.map { it.spaceId })
        assertEquals("Assistant", top3WithAlphaActive.first().spaceName)

        // Empty persisted list always shows Assistant at leftmost position
        val emptyFallback = AeginBridge.selectRecentSpacesForStrip(
            persistedSpaces = emptyList(),
            activeSpaceId = "main-space",
            activeSpaceName = "Main Space",
            maxCount = 3
        )
        assertEquals(listOf("main-space"), emptyFallback.map { it.spaceId })
        assertEquals(listOf("Assistant"), emptyFallback.map { it.spaceName })
    }

    @Test
    fun testTrimSpaceHistoryToLastSummary() {
        val rawHistory = listOf(
            SpaceMessageEntry("USER", "Very old question 1", "main-space", 100L),
            SpaceMessageEntry("ASSISTANT", "Very old answer 1", "main-space", 101L),
            SpaceMessageEntry("SYSTEM", "Earlier conversation summary: First older summary.", "main-space", 150L),
            SpaceMessageEntry("USER", "Middle question 2", "main-space", 200L),
            SpaceMessageEntry("SYSTEM", "Earlier conversation summary: Latest summary of Q1-Q2.", "main-space", 299L),
            SpaceMessageEntry("SYSTEM", "Unrelated internal system note", "main-space", 300L),
            SpaceMessageEntry("USER", "Recent question 3", "main-space", 301L),
            SpaceMessageEntry("ASSISTANT", "Recent answer 3", "main-space", 302L)
        )

        val trimmed = AeginBridge.trimSpaceHistoryToLastSummary(rawHistory)
        assertEquals(3, trimmed.size)
        assertEquals("SYSTEM", trimmed[0].role)
        assertEquals("Latest summary of Q1-Q2.", AeginBridge.extractConversationSummaryText(trimmed[0].content))
        assertEquals("Recent question 3", trimmed[1].content)
        assertEquals("Recent answer 3", trimmed[2].content)
    }

    @Test
    fun testKnowledgeFolderHierarchyAndParsing() {
        val raw = """
            [
              {"id":"notes.txt","title":"notes.txt","relativePath":"notes.txt","chunkCount":2,"length":240,"timestamp":1},
              {"id":"Architecture/design.md","title":"design.md","relativePath":"Architecture/design.md","chunkCount":4,"length":1200,"timestamp":2},
              {"id":"Architecture/2026/roadmap.md","title":"roadmap.md","relativePath":"Architecture/2026/roadmap.md","chunkCount":3,"length":900,"timestamp":3},
              {"id":"Finance/budget.csv","title":"budget.csv","relativePath":"Finance/budget.csv","chunkCount":1,"length":150,"timestamp":4}
            ]
        """.trimIndent()
        val parsed = AeginBridge.parseKnowledgeDocumentsJson(raw)
        assertEquals(4, parsed.size)
        val folders = AeginBridge.buildKnowledgeFolderSummaries(parsed)
        assertEquals(listOf("__ROOT__", "Architecture/", "Architecture/2026/", "Finance/"), folders.map { it.scopeKey })
        assertEquals(listOf(1, 2, 1, 1), folders.map { it.fileCount })
    }
}
