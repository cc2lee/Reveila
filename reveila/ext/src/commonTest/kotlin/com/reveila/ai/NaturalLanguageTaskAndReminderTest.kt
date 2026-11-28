package com.reveila.ai

import com.reveila.channels.ProactiveAlertMessage
import com.reveila.crypto.Cryptographer
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
import com.reveila.tools.TaskSchedulerTool
import com.reveila.util.json.JsonUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaturalLanguageTaskAndReminderTest {

    private class SimpleProxy(
        private val componentName: String,
        private val target: Any
    ) : Proxy {
        override fun getName(): String = componentName
        override fun getRequiredRoles(): List<String> = emptyList()
        override fun invoke(methodName: String, args: Array<out Any?>?): Any? = null
        override fun getInstance(): Any = target
    }

    private fun freshTestDir(tag: String): String {
        val dir = "./build/tmp/nl-tasks-$tag-${PlatformSystem.currentTimeMillis()}"
        val fs = PlatformFileSystem()
        if (fs.exists(dir)) {
            fs.delete(dir, recursive = true)
        }
        fs.createDirectories(dir)
        return dir
    }

    private fun buildAutonomousAgent(dir: String): AutonomousAgent {
        val props = Properties().apply {
            setProperty("data.dir", dir)
            setProperty("system.home", dir)
        }
        val proxies = mutableMapOf<String, Proxy>()
        val ctx = object : Context {
            override fun getLogger(): PlatformLogger? = null
            override val properties: Properties = props
            override val cryptographer: Cryptographer? = null
            override val platformAdapter: PlatformAdapter? = null
            override fun getProxy(name: String): Proxy {
                return proxies[name] ?: throw IllegalArgumentException("Missing test proxy: $name")
            }
        }
        val dataService = DataService().apply { context = ctx; start() }
        proxies["DataService"] = SimpleProxy("DataService", dataService)
        val sessionManager = AgentSessionManager().apply { context = ctx; start() }
        proxies["AgentSessionManager"] = SimpleProxy("AgentSessionManager", sessionManager)
        val orchestration = OrchestrationService().apply { context = ctx; start() }
        proxies["OrchestrationService"] = SimpleProxy("OrchestrationService", orchestration)
        val metadataRegistry = MetadataRegistry().apply { context = ctx; start() }
        proxies["MetadataRegistry"] = SimpleProxy("MetadataRegistry", metadataRegistry)
        val managedInvocation = object : ManagedInvocation() {
            override fun onStart() {}
        }.apply { context = ctx; start() }
        proxies["ManagedInvocation"] = SimpleProxy("ManagedInvocation", managedInvocation)

        return AutonomousAgent().apply {
            context = ctx
            start()
        }
    }

    @Test
    fun reminderWaitsUntilScheduledDateTimeAndDeliversDirectlyWithoutLlmLoop() {
        val dir = freshTestDir("reminder-exact")
        val agent = buildAutonomousAgent(dir)
        val deliveredAlerts = mutableListOf<ProactiveAlertMessage>()
        agent.proactiveAlertListener = { alert, _ -> deliveredAlerts.add(alert) }

        val now = PlatformSystem.currentTimeMillis()
        // 1. Schedule a reminder 1 hour in the future -> doTask() must NOT fire it yet
        val futureMs = now + 60 * 60 * 1000L
        val futureJson = agent.createReminder(
            title = "Dentist Appointment",
            message = "Call Dr. Smith at 2:00 PM",
            scheduledAtMs = futureMs,
            replyChannel = "teams",
            replyTarget = "user-alice",
            tenantId = "acme",
            userId = "alice"
        )
        val futureTask = JsonUtil.parseJsonStringToMap(futureJson)
        val futureId = futureTask["taskId"].toString()
        assertEquals("REMINDER", futureTask["taskType"])
        assertEquals("ACTIVE", futureTask["status"])

        agent.doTask()
        assertFalse(deliveredAlerts.any { it.taskId == futureId }, "Future reminder must not fire before scheduledAtMs")

        // 2. Schedule a reminder whose scheduledAtMs is due now -> doTask() delivers immediately and marks COMPLETED
        val dueJson = agent.createReminder(
            title = "Submit Q4 Expenses",
            message = "Submit Q4 expense report before 5 PM",
            scheduledAtMs = now - 1000L,
            replyChannel = "whatsapp",
            replyTarget = "+15550100",
            tenantId = "acme",
            userId = "alice"
        )
        val dueTask = JsonUtil.parseJsonStringToMap(dueJson)
        val dueId = dueTask["taskId"].toString()

        agent.doTask()
        val fired = deliveredAlerts.firstOrNull { it.taskId == dueId }
        assertNotNull(fired, "Due reminder should have delivered a proactive alert")
        assertTrue(fired.summary.contains("Submit Q4 expense report"))
        assertEquals("whatsapp", fired.metadata["replyChannel"])
        assertEquals("+15550100", fired.metadata["replyTarget"])

        // Verify one-time reminder is now COMPLETED & disabled so it won't fire again
        val after = agent.listTasks().first { it["taskId"] == dueId }
        assertEquals("COMPLETED", after["status"])
        assertEquals(false, after["enabled"])
    }

    @Test
    fun manageTasksToolCreatesListsPausesResumesAndCancelsViaNaturalLanguageArgs() {
        val dir = freshTestDir("tool-crud")
        val agent = buildAutonomousAgent(dir)
        val coordinator = ReveilaToolCoordinator(
            systemHome = dir,
            secretManagerProvider = { null },
            downloadManagerProvider = { null },
            autonomousAgentProvider = { agent },
            tenantIdProvider = { "acme" }
        )

        assertTrue(coordinator.shouldRouteToAgenticLoop("Remind me to call John tomorrow at 2pm"))
        assertTrue(coordinator.shouldRouteToAgenticLoop("Every Friday at 9am summarize my expenses"))
        assertTrue(coordinator.shouldRouteToAgenticLoop("Cancel the dentist reminder"))

        val session = AgentSession("sess-teams-1", "trace-1", spaceId = "work").apply {
            put("_tenant_id", "acme")
            put("_user_id", "alice")
            put("_reply_channel", "teams")
            put("_reply_target", "teams-conv-42")
        }

        // Create a reminder with ISO-8601 scheduledAt
        val createRemOut = coordinator.authorizeAndExecute(
            toolCallObj = mapOf(
                "name" to "manage_tasks",
                "arguments" to mapOf(
                    "action" to "create_reminder",
                    "title" to "Board Prep",
                    "message" to "Review Q4 board deck",
                    "scheduledAt" to "2026-12-03T14:30:00"
                )
            ),
            session = session,
            actorId = "alice",
            preferredProviderName = null
        )
        val createdRemMap = JsonUtil.parseJsonStringToMap(createRemOut)
        assertEquals(true, createdRemMap["ok"])
        @Suppress("UNCHECKED_CAST")
        val remTask = createdRemMap["task"] as Map<String, Any?>
        val remId = remTask["taskId"].toString()
        assertEquals("teams", remTask["replyChannel"])
        assertEquals("teams-conv-42", remTask["replyTarget"])
        assertTrue(((remTask["nextRunAtMs"] as Number).toLong()) > 0L)

        // Create a recurring task
        val createTaskOut = coordinator.authorizeAndExecute(
            toolCallObj = mapOf(
                "name" to "manage_tasks",
                "arguments" to mapOf(
                    "action" to "create_task",
                    "title" to "Weekly Invoice Summary",
                    "prompt" to "Summarize all invoices in my expenses folder",
                    "frequency" to "weekly",
                    "scheduleTime" to "09:00"
                )
            ),
            session = session,
            actorId = "alice",
            preferredProviderName = null
        )
        assertTrue(createTaskOut.contains("\"ok\":true") || createTaskOut.contains("\"ok\": true"))

        // Pause by title match
        val pauseOut = coordinator.authorizeAndExecute(
            toolCallObj = mapOf(
                "name" to "manage_tasks",
                "arguments" to mapOf("action" to "pause", "title" to "Weekly Invoice")
            ),
            session = session,
            actorId = "alice",
            preferredProviderName = null
        )
        assertTrue(pauseOut.contains("\"action\":\"paused\"") || pauseOut.contains("\"action\": \"paused\""))

        // Cancel reminder by ID
        val cancelOut = coordinator.authorizeAndExecute(
            toolCallObj = mapOf(
                "name" to "manage_tasks",
                "arguments" to mapOf("action" to "cancel", "taskId" to remId)
            ),
            session = session,
            actorId = "alice",
            preferredProviderName = null
        )
        assertTrue(cancelOut.contains("\"ok\":true") || cancelOut.contains("\"ok\": true"))
        assertFalse(agent.listTasks().any { it["taskId"] == remId })
    }

    @Test
    fun systemPromptIncludesCurrentLocalDateTimeForRelativeScheduling() {
        val rendered = Prompt.buildSystemPrompt(
            role = "Reveila AI Assistant",
            mode = PromptMode.CONVERSATIONAL
        )
        assertTrue(
            rendered.systemPrompt.contains("Current local date/time:"),
            "System prompt should include current local date/time anchor"
        )
        assertTrue(TaskSchedulerTool.parseDateTimeToEpochMs("2026-12-03T14:30:00") > 0L)
        assertTrue(TaskSchedulerTool.parseDateTimeToEpochMs("2026-12-03 14:30") > 0L)
        assertTrue(TaskSchedulerTool.parseDateTimeToEpochMs("2026-12-03") > 0L)
    }
}
