package com.reveila.safety

import com.reveila.data.Entity
import com.reveila.service.DataService
import com.reveila.system.Context
import com.reveila.system.PlatformAdapter
import com.reveila.system.Plugin
import com.reveila.system.Properties
import com.reveila.system.Proxy
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import com.reveila.util.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandChainAuditTest {

    private fun createFlightRecorderFixture(): Triple<DefaultFlightRecorder, DataService, String> {
        val fs = PlatformFileSystem()
        val testDir = fs.resolve("build", "test-command-chain-${Uuid.randomUuid().take(8)}")
        if (!fs.exists(testDir)) {
            fs.createDirectories(testDir)
        }
        val props = Properties(
            mapOf(
                "system.home" to testDir,
                "data.dir" to fs.resolve(testDir, "data")
            )
        )
        val dataService = DataService()
        val baseContext = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override fun getProxy(name: String): Proxy = when (name) {
                "DataService" -> object : Proxy {
                    override fun getName(): String = "DataService"
                    override fun getRequiredRoles(): List<String> = emptyList()
                    override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
                    override fun getInstance(): Any = dataService
                }
                else -> throw IllegalArgumentException("Unknown proxy: $name")
            }
            override val properties: Properties = props
            override val platformAdapter: PlatformAdapter? = null
        }
        dataService.setContext(baseContext)
        dataService.start()

        val recorder = DefaultFlightRecorder()
        recorder.setContext(baseContext)
        recorder.start()

        return Triple(recorder, dataService, testDir)
    }

    @Test
    fun testCausalTreeAndHitlActorAttribution() {
        val (recorder, _, testDir) = createFlightRecorderFixture()
        try {
            val root = Plugin.create(
                name = "AgenticFabric",
                tenantId = "acme",
                actorId = "alice@acme.com",
                actorType = "USER"
            )
            recorder.recordCommandEvent(
                plugin = root,
                action = "PROMPT_ISSUED",
                details = "Prompt received: schedule wire transfer review",
                actorId = "alice@acme.com",
                actorType = "USER"
            )

            val taskAgent = root.deriveChild(
                childName = "AutonomousAgent",
                childActorId = "agent-planner",
                childActorType = "AGENT"
            )
            recorder.recordCommandEvent(
                plugin = taskAgent,
                action = "TASK_WAITING_APPROVAL",
                details = "Paused at HITL gate awaiting manager sign-off",
                actorId = "agent-planner",
                actorType = "AGENT"
            )

            val approvalStep = taskAgent.deriveChild(
                childName = "HitlGate",
                childActorId = "bob.manager@acme.com",
                childActorType = "USER"
            )
            recorder.recordCommandEvent(
                plugin = approvalStep,
                action = "TASK_APPROVED",
                details = "Manager approved execution",
                actorId = "bob.manager@acme.com",
                actorType = "USER"
            )

            val report = recorder.reconstructCommandChain(root.traceId)
            assertNotNull(report)
            assertEquals(root.traceId, report.traceId)
            assertEquals("alice@acme.com", report.initiatedBy)
            assertEquals(listOf("bob.manager@acme.com"), report.approvedBy)
            assertTrue(report.rejectedBy.isEmpty())
            assertTrue(report.chainIntegrityVerified, "Hash chain must verify before tampering")
            assertEquals(3, report.timeline.size)

            assertEquals(1, report.roots.size)
            val rootNode = report.roots.first()
            assertEquals("PROMPT_ISSUED", rootNode.event.action)
            assertEquals("alice@acme.com", rootNode.event.actorId)
            assertEquals(1, rootNode.children.size)

            val childNode = rootNode.children.first()
            assertEquals("TASK_WAITING_APPROVAL", childNode.event.action)
            assertEquals(rootNode.event.spanId, childNode.event.parentSpanId)
            assertEquals(1, childNode.children.size)

            val grandChild = childNode.children.first()
            assertEquals("TASK_APPROVED", grandChild.event.action)
            assertEquals("bob.manager@acme.com", grandChild.event.actorId)
            assertEquals(childNode.event.spanId, grandChild.event.parentSpanId)
        } finally {
            PlatformFileSystem().delete(testDir, recursive = true)
        }
    }

    @Test
    fun testTamperDetectionOnModifiedAuditLog() {
        val (recorder, dataService, testDir) = createFlightRecorderFixture()
        try {
            val root = Plugin.create(
                name = "AgenticFabric",
                tenantId = "acme",
                actorId = "alice@acme.com",
                actorType = "USER"
            )
            recorder.recordCommandEvent(
                plugin = root,
                action = "PROMPT_ISSUED",
                details = "Initial command",
                actorId = "alice@acme.com",
                actorType = "USER"
            )
            val approval = root.deriveChild("HitlGate", "bob@acme.com", "USER")
            recorder.recordCommandEvent(
                plugin = approval,
                action = "TASK_APPROVED",
                details = "Approved for \$100 limit",
                actorId = "bob@acme.com",
                actorType = "USER"
            )

            assertTrue(recorder.reconstructCommandChain(root.traceId)?.chainIntegrityVerified == true)

            // Simulate post-hoc tampering directly on persisted storage without updating SHA-256 chain
            val repo = dataService.getRepository("AuditLog")
            val stored = repo.fetchAll()
            val target = stored.first { it.attributes["action"] == "TASK_APPROVED" }
            val tamperedAttrs = target.attributes.toMutableMap().apply {
                this["details"] = "Approved for \$9999999 limit"
            }
            repo.store(Entity("AuditLog", target.key, tamperedAttrs))

            val tamperedReport = CommandChainAudit.reconstructTrace(root.traceId, dataService)
            assertFalse(
                tamperedReport.chainIntegrityVerified,
                "Tampering with persisted AuditLog details must fail chain verification"
            )
        } finally {
            PlatformFileSystem().delete(testDir, recursive = true)
        }
    }

    @Test
    fun testTraceContextHolderCausalPropagation() {
        try {
            TraceContextHolder.clear()
            TraceContextHolder.setActor("carol@acme.com", "USER")
            val root = TraceContextHolder.getContext()
            assertNotNull(root)
            assertEquals(root.traceId, TraceContextHolder.getTraceId())
            assertEquals("carol@acme.com", root.actorId)
            assertEquals("USER", root.actorType)

            val child = TraceContextHolder.deriveChildSpan("delegate-bot", "AGENT")
            assertEquals(root.traceId, child.traceId)
            assertEquals(root.spanId, child.parentSpanId)
            assertEquals("delegate-bot", child.actorId)
            assertEquals("AGENT", child.actorType)

            val current = TraceContextHolder.getContext()
            assertNotNull(current)
            assertEquals(child.spanId, current.spanId)
        } finally {
            TraceContextHolder.clear()
        }
    }
}
