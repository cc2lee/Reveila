package com.reveila.ai

import com.reveila.safety.CommandChainAudit
import com.reveila.safety.DefaultFlightRecorder
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecretManager
import com.reveila.safety.TraceContextHolder
import com.reveila.service.DataService
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.Subject
import com.reveila.system.UserPrincipal
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutonomousAgentChainTest {

    private data class AgentChainFixture(
        val agent: AutonomousAgent,
        val fabric: AgenticFabric,
        val recorder: DefaultFlightRecorder,
        val dataService: DataService,
        val homeDir: String
    )

    private fun createFixture(): AgentChainFixture {
        val fs = PlatformFileSystem()
        val homeDir = fs.resolve("build", "test-agent-chain-${Uuid.randomUuid().take(8)}")
        fs.createDirectories(homeDir)

        val props = Properties(
            mapOf(
                "system.home" to homeDir,
                "data.dir" to fs.resolve(homeDir, "data")
            )
        )

        val dataService = DataService()
        val recorder = DefaultFlightRecorder()
        val secretManager = SecretManager()
        val managedInvocation = ManagedInvocation()
        val metadataRegistry = MetadataRegistry()
        val sessionManager = AgentSessionManager()
        val orchestrationService = OrchestrationService()
        val llmFactory = LlmProviderFactory()
        val fabric = AgenticFabric()
        val agent = AutonomousAgent()

        val proxies = mapOf<String, Proxy>(
            "DataService" to simpleProxy("DataService", dataService),
            "FlightRecorder" to simpleProxy("FlightRecorder", recorder),
            "SecretManager" to simpleProxy("SecretManager", secretManager),
            "ManagedInvocation" to simpleProxy("ManagedInvocation", managedInvocation),
            "MetadataRegistry" to simpleProxy("MetadataRegistry", metadataRegistry),
            "AgentSessionManager" to simpleProxy("AgentSessionManager", sessionManager),
            "OrchestrationService" to simpleProxy("OrchestrationService", orchestrationService),
            "LlmProviderFactory" to simpleProxy("LlmProviderFactory", llmFactory),
            "AgenticFabric" to simpleProxy("AgenticFabric", fabric),
            "AutonomousAgent" to simpleProxy("AutonomousAgent", agent)
        )

        val ctx = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy =
                proxies[name] ?: throw IllegalArgumentException("Unknown proxy: $name")
            override val properties: Properties = props
            override val platformAdapter: PlatformAdapter? = null
        }

        listOf(
            dataService,
            recorder,
            secretManager,
            managedInvocation,
            metadataRegistry,
            sessionManager,
            orchestrationService,
            llmFactory,
            fabric,
            agent
        ).forEach { component ->
            component.setContext(ctx)
            component.start()
        }

        return AgentChainFixture(agent, fabric, recorder, dataService, homeDir)
    }

    private fun simpleProxy(name: String, instance: Any): Proxy = object : Proxy {
        override fun getName(): String = name
        override fun getRequiredRoles(): List<String> = emptyList()
        override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
        override fun getInstance(): Any = instance
    }

    @Test
    fun testCreateApproveAndReconstructTraceChainJson() {
        val fixture = createFixture()
        try {
            val createdJson = fixture.agent.createTaskWithIntent(
                title = "Vendor Payout Batch",
                intent = "finance.payout",
                prompt = "Summarize pending vendor ledger entries",
                frequency = "once",
                scheduleTime = "09:00",
                requiresApproval = true,
                executionTarget = "auto",
                actorId = "alice@acme.com"
            )
            val createdMap = JsonUtil.parseJsonStringToMap(createdJson)
            val taskId = createdMap["taskId"] as String
            val traceId = createdMap["traceId"] as String
            assertEquals("alice@acme.com", createdMap["initiatedBy"])
            assertEquals("WAITING_APPROVAL", createdMap["status"])

            val approvedJson = fixture.agent.approveTask(taskId, "bob.approver@acme.com")
            val approvedMap = JsonUtil.parseJsonStringToMap(approvedJson)
            assertEquals("bob.approver@acme.com", approvedMap["approvedBy"])

            val chainJson = fixture.agent.getTraceChainJson(traceId)
            val chainMap = JsonUtil.parseJsonStringToMap(chainJson)
            assertEquals(traceId, chainMap["traceId"])
            assertEquals("alice@acme.com", chainMap["initiatedBy"])
            assertEquals(listOf("bob.approver@acme.com"), chainMap["approvedBy"])
            assertEquals(true, chainMap["chainIntegrityVerified"])

            @Suppress("UNCHECKED_CAST")
            val timeline = chainMap["timeline"] as List<Map<String, Any?>>
            val actions = timeline.map { it["action"]?.toString() }
            assertTrue("TASK_CREATED" in actions)
            assertTrue("TASK_APPROVED" in actions)
            assertTrue("TASK_STARTED" in actions)

            val createdEvent = timeline.first { it["action"] == "TASK_CREATED" }
            val approvedEvent = timeline.first { it["action"] == "TASK_APPROVED" }
            val startedEvent = timeline.first { it["action"] == "TASK_STARTED" }
            assertEquals(createdEvent["spanId"], approvedEvent["parentSpanId"])
            assertEquals(approvedEvent["spanId"], startedEvent["parentSpanId"])
        } finally {
            fixture.agent.stop()
            PlatformFileSystem().delete(fixture.homeDir, recursive = true)
        }
    }

    @Test
    fun testCreateRejectTracksInitiatorAndRejector() {
        val fixture = createFixture()
        try {
            val createdJson = fixture.agent.createTaskWithIntent(
                title = "Database Cleanup",
                intent = "ops.cleanup",
                prompt = "Delete expired staging snapshots",
                frequency = "once",
                scheduleTime = "23:00",
                requiresApproval = true,
                executionTarget = "auto",
                actorId = "alice@acme.com"
            )
            val createdMap = JsonUtil.parseJsonStringToMap(createdJson)
            val taskId = createdMap["taskId"] as String
            val traceId = createdMap["traceId"] as String

            val rejectedJson = fixture.agent.rejectTask(taskId, "charlie.secops@acme.com")
            val rejectedMap = JsonUtil.parseJsonStringToMap(rejectedJson)
            assertEquals("PAUSED", rejectedMap["status"])
            assertEquals("charlie.secops@acme.com", rejectedMap["rejectedBy"])

            val report = fixture.recorder.reconstructCommandChain(traceId)
            assertNotNull(report)
            assertEquals("alice@acme.com", report.initiatedBy)
            assertEquals(listOf("charlie.secops@acme.com"), report.rejectedBy)
            assertTrue(report.approvedBy.isEmpty())
            assertTrue(report.chainIntegrityVerified)
        } finally {
            fixture.agent.stop()
            PlatformFileSystem().delete(fixture.homeDir, recursive = true)
        }
    }

    @Test
    fun testAgenticFabricRecordsPromptIssuedWithUserPrincipal() {
        val fixture = createFixture()
        try {
            TraceContextHolder.clear()
            val subject = Subject().apply {
                principals.add(UserPrincipal("dave@acme.com"))
            }
            val session = AgentSession("chat-session-1", null, 10)
            fixture.fabric.processIntent(session, subject, "Check policy compliance summary")

            val allLogs = fixture.dataService.getRepository("AuditLog").fetchAll()
            val promptEvent = allLogs.firstOrNull {
                it.attributes["action"] == "PROMPT_ISSUED" && it.attributes["actorId"] == "dave@acme.com"
            }
            assertNotNull(promptEvent, "Expected PROMPT_ISSUED AuditLog event attributed to UserPrincipal dave@acme.com")
            val traceId = promptEvent.attributes["traceId"]?.toString().orEmpty()
            assertTrue(traceId.isNotBlank())

            val report = CommandChainAudit.reconstructTrace(traceId, fixture.dataService)
            assertEquals("dave@acme.com", report.initiatedBy)
            assertTrue(report.chainIntegrityVerified)
        } finally {
            TraceContextHolder.clear()
            fixture.agent.stop()
            PlatformFileSystem().delete(fixture.homeDir, recursive = true)
        }
    }
}
