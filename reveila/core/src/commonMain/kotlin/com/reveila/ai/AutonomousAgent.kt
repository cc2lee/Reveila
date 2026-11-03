package com.reveila.ai

import com.reveila.safety.KillSwitch
import com.reveila.safety.SafetyStatus
import com.reveila.service.DataService
import com.reveila.system.Constants
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlinx.datetime.Clock

/**
 * Autonomous System Agent and Sovereign Task Control Center.
 * Manages recurring and scheduled tasks defined in ${REVEILA_HOME}/tasks directory (one JSON file per task),
 * enforces per-task and global KillSwitch state, and records/queries AuditLog events via DataService.
 */
open class AutonomousAgent : SystemComponent(), KillSwitch {

    private var agenticFabric: AgenticFabric? = null
    private var orchestrationService: OrchestrationService? = null
    private var dataService: DataService? = null
    private var session: AgentSession? = null
    private var subject: Subject? = null

    private val killedTaskIds: MutableSet<String> = mutableSetOf()
    private val runningTaskIds: MutableSet<String> = mutableSetOf()
    private var globalEmergencyStopped: Boolean = false

    @Throws(Exception::class)
    override fun onStart() {
        val s = Subject()
        s.principals.add(RolePrincipal("system"))
        s.principals.add(RolePrincipal("autonomous-agent"))
        this.subject = s

        val timestamp = Clock.System.now().toString().substringBefore('T')

        try {
            val fabricProxy = context?.getProxy("AgenticFabric")
            this.agenticFabric = (fabricProxy?.getInstance() as? AgenticFabric) ?: fabricProxy as? AgenticFabric
        } catch (_: Exception) {
        }

        try {
            val orchProxy = context?.getProxy("OrchestrationService")
            this.orchestrationService = (orchProxy?.getInstance() as? OrchestrationService) ?: orchProxy as? OrchestrationService
        } catch (_: Exception) {
        }

        try {
            val dsProxy = context?.getProxy("DataService")
            this.dataService = (dsProxy?.getInstance() as? DataService) ?: dsProxy as? DataService
        } catch (_: Exception) {
        }

        this.session = orchestrationService?.createSession("autonomous-agent-session-$timestamp", null)
        ensureDefaultTasksDirectory()
        logger.info("AutonomousAgent started. Ready to manage and execute tasks.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        runningTaskIds.clear()
    }

    private fun resolveFabric(): AgenticFabric? {
        if (agenticFabric == null) {
            try {
                val p = context?.getProxy("AgenticFabric")
                agenticFabric = (p?.getInstance() as? AgenticFabric) ?: p as? AgenticFabric
            } catch (_: Exception) {
            }
        }
        return agenticFabric
    }

    private fun resolveOrchestration(): OrchestrationService? {
        if (orchestrationService == null) {
            try {
                val p = context?.getProxy("OrchestrationService")
                orchestrationService = (p?.getInstance() as? OrchestrationService) ?: p as? OrchestrationService
            } catch (_: Exception) {
            }
        }
        return orchestrationService
    }

    private fun resolveDataService(): DataService? {
        if (dataService == null) {
            try {
                val p = context?.getProxy("DataService")
                dataService = (p?.getInstance() as? DataService) ?: p as? DataService
            } catch (_: Exception) {
            }
        }
        return dataService
    }

    private fun resolveTasksDir(): String {
        val fs = PlatformFileSystem()
        val systemHome = context?.properties?.getProperty(Constants.SYSTEM_HOME)
            ?: context?.properties?.getProperty("system.home")
            ?: "."
        val tasksDir = fs.resolve(systemHome, "tasks")
        if (!fs.exists(tasksDir)) {
            fs.createDirectories(tasksDir)
        }
        return tasksDir
    }

    private fun ensureDefaultTasksDirectory() {
        try {
            val fs = PlatformFileSystem()
            val tasksDir = resolveTasksDir()
            val existing = fs.listRelativePaths(tasksDir, "json")
            if (existing.isEmpty()) {
                val defaultTask = linkedMapOf<String, Any?>(
                    "taskId" to "daily-news-alert",
                    "title" to "Daily AI Security Briefing",
                    "intent" to "news.search",
                    "prompt" to "Find the latest news about AI security and provide a concise summary.",
                    "frequency" to "daily",
                    "scheduleTime" to "08:00",
                    "enabled" to true,
                    "status" to "ACTIVE",
                    "requiresApproval" to false,
                    "approvalReason" to "",
                    "createdAt" to PlatformSystem.currentTimeMillis(),
                    "lastRunAt" to 0L,
                    "lastResult" to "Scheduled and waiting for next execution window."
                )
                val path = fs.resolve(tasksDir, "daily-news.json")
                fs.writeText(path, JsonUtil.toPrettyJsonString(defaultTask), append = false)
            }
        } catch (e: Exception) {
            logger.warning("Could not seed default task: ${e.message}")
        }
    }

    // =========================================================================
    // KillSwitch Interface Implementation
    // =========================================================================

    override fun isAuthorized(agentId: String): Boolean {
        if (globalEmergencyStopped) return false
        if (resolveOrchestration()?.isSuspended() == true) return false
        return !killedTaskIds.contains(agentId)
    }

    override fun emergencyStopAll() {
        globalEmergencyStopped = true
        runningTaskIds.clear()
        resolveOrchestration()?.toggleSuspend(true)

        try {
            val fs = PlatformFileSystem()
            val tasksDir = resolveTasksDir()
            val files = fs.listRelativePaths(tasksDir, "json")
            for (rel in files) {
                val fullPath = fs.resolve(tasksDir, rel)
                val map = JsonUtil.parseJsonStringToMap(fs.readText(fullPath)).toMutableMap<String, Any?>()
                val tId = (map["taskId"] as? String) ?: rel.removeSuffix(".json")
                killedTaskIds.add(tId)
                map["enabled"] = false
                map["status"] = "KILLED"
                fs.writeText(fullPath, JsonUtil.toPrettyJsonString(map), append = false)
            }
        } catch (e: Exception) {
            logger.warning("Failed to mark tasks KILLED during emergencyStopAll: ${e.message}")
        }

        recordAuditLog("KILL_SWITCH_EMERGENCY_STOP_ALL", "Global Kill Switch activated. All autonomous tasks halted.")
    }

    override fun getStatus(agentId: String): SafetyStatus {
        return when {
            globalEmergencyStopped || killedTaskIds.contains(agentId) -> SafetyStatus.KILLED
            resolveOrchestration()?.isSuspended() == true -> SafetyStatus.PENDING_AUTHORIZATION
            else -> SafetyStatus.ACTIVE
        }
    }

    // =========================================================================
    // Background Scheduled Execution Loop
    // =========================================================================

    /**
     * Entry point for recurring task execution, triggered by the 'runnable' configuration.
     * Iterates through the ${REVEILA_HOME}/tasks directory and executes due active workflows.
     */
    open fun doTask() {
        if (globalEmergencyStopped || resolveOrchestration()?.isSuspended() == true) {
            return
        }

        val fs = PlatformFileSystem()
        val tasksDir = resolveTasksDir()
        val taskFiles = fs.listRelativePaths(tasksDir, "json").map { fs.resolve(tasksDir, it) }
        if (taskFiles.isEmpty()) {
            return
        }

        val now = PlatformSystem.currentTimeMillis()
        for (file in taskFiles) {
            try {
                val content = fs.readText(file)
                val taskDef = JsonUtil.parseJsonStringToMap(content).toMutableMap<String, Any?>()
                val taskId = (taskDef["taskId"] as? String) ?: continue
                if (!isAuthorized(taskId)) continue

                val enabled = (taskDef["enabled"] as? Boolean)
                    ?: !((taskDef["status"] as? String)?.uppercase() in listOf("PAUSED", "STOPPED", "KILLED"))
                val status = (taskDef["status"] as? String)?.uppercase() ?: "ACTIVE"
                if (!enabled || status == "WAITING_APPROVAL" || status == "KILLED" || status == "PAUSED") {
                    continue
                }

                val lastRunAt = (taskDef["lastRunAt"] as? Number)?.toLong() ?: 0L
                val frequency = (taskDef["frequency"] as? String)?.lowercase() ?: "daily"
                val intervalMs = when {
                    frequency.contains("min") -> 15 * 60 * 1000L
                    frequency.contains("hour") -> 60 * 60 * 1000L
                    frequency.contains("week") -> 7 * 24 * 60 * 60 * 1000L
                    else -> 24 * 60 * 60 * 1000L
                }

                if (lastRunAt > 0L && (now - lastRunAt) < intervalMs) {
                    continue
                }

                executeTaskFile(file, taskDef, triggeredManually = false)
            } catch (e: Exception) {
                logger.severe("Failed to process autonomous task: $file. Error: ${e.message}")
            }
        }
    }

    private fun executeTaskFile(
        filePath: String,
        taskDef: MutableMap<String, Any?>,
        triggeredManually: Boolean,
        approvedByUser: Boolean = false
    ): Map<String, Any?> {
        val fs = PlatformFileSystem()
        val taskId = (taskDef["taskId"] as? String) ?: "task-${Uuid.randomUuid().take(6)}"
        val title = (taskDef["title"] as? String) ?: taskId
        val prompt = (taskDef["prompt"] as? String) ?: ""

        if (!isAuthorized(taskId) && !triggeredManually) {
            taskDef["status"] = "KILLED"
            taskDef["enabled"] = false
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
            return taskDef
        }

        val requiresApproval = (taskDef["requiresApproval"] as? Boolean) == true ||
            (!approvedByUser && isApprovalRequiredForPrompt(prompt))

        if (requiresApproval && !approvedByUser) {
            taskDef["status"] = "WAITING_APPROVAL"
            taskDef["approvalReason"] = "Task requires Human-In-The-Loop approval before executing: \"$title\""
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
            recordAuditLog("TASK_WAITING_APPROVAL", "Task [$taskId] ($title) paused awaiting user approval.")
            return taskDef
        }

        runningTaskIds.add(taskId)
        taskDef["status"] = "RUNNING"
        taskDef["approvalReason"] = ""
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
        recordAuditLog("TASK_STARTED", "Executing task [$taskId]: $prompt")

        try {
            val fabric = resolveFabric()
            val orch = resolveOrchestration()
            val taskSession = session ?: orch?.createSession("task-$taskId", null)
            val subj = subject ?: Subject().apply {
                principals.add(RolePrincipal("system"))
                principals.add(RolePrincipal("autonomous-agent"))
            }

            var answerText = "Task executed."
            var finalStatus = "ACTIVE"

            if (fabric != null && taskSession != null) {
                val rawResult = fabric.processIntent(taskSession, subj, prompt)
                val statusStr = rawResult["status"]?.toString() ?: ""
                if (statusStr.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                    finalStatus = "WAITING_APPROVAL"
                    val reason = rawResult["reasoning"]?.toString() ?: "Governance policy escalated task for user approval."
                    taskDef["approvalReason"] = reason
                    answerText = "Waiting for user approval: $reason"
                    recordAuditLog("TASK_WAITING_APPROVAL", "Task [$taskId] escalated for approval: $reason")
                } else {
                    answerText = fabric.interpretAiResponse(rawResult)
                    recordAuditLog("TASK_COMPLETED", "Task [$taskId] completed: ${answerText.take(160)}")
                }
            } else {
                answerText = "Processed task \"$title\": $prompt"
                recordAuditLog("TASK_COMPLETED", "Task [$taskId] completed.")
            }

            val enabled = (taskDef["enabled"] as? Boolean) ?: true
            taskDef["status"] = if (finalStatus == "WAITING_APPROVAL") {
                "WAITING_APPROVAL"
            } else if (enabled) {
                "ACTIVE"
            } else {
                "PAUSED"
            }
            taskDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
            taskDef["lastResult"] = answerText
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
        } catch (e: Exception) {
            taskDef["status"] = "ACTIVE"
            taskDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
            taskDef["lastResult"] = "Execution error: ${e.message}"
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
            recordAuditLog("TASK_FAILED", "Task [$taskId] failed: ${e.message}")
        } finally {
            runningTaskIds.remove(taskId)
        }

        return taskDef
    }

    private fun isApprovalRequiredForPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return lower.contains("delete") ||
            lower.contains("transfer") ||
            lower.contains("purchase") ||
            lower.contains("send email") ||
            lower.contains("publish") ||
            lower.contains("require approval") ||
            lower.contains("ask for approval")
    }

    // =========================================================================
    // Public Control Center API (Invokable via Bridge / Engine)
    // =========================================================================

    open fun getDashboardStateJson(): String {
        val fs = PlatformFileSystem()
        val tasksDir = resolveTasksDir()
        val files = fs.listRelativePaths(tasksDir, "json")

        val tasksList = mutableListOf<Map<String, Any?>>()
        for (rel in files) {
            try {
                val fullPath = fs.resolve(tasksDir, rel)
                val raw = fs.readText(fullPath)
                val map = JsonUtil.parseJsonStringToMap(raw).toMutableMap<String, Any?>()
                val taskId = (map["taskId"] as? String) ?: rel.removeSuffix(".json")
                map["taskId"] = taskId
                map["fileName"] = rel
                if (!map.containsKey("title") || (map["title"] as? String).isNullOrBlank()) {
                    map["title"] = formatTaskTitle(taskId, map["prompt"] as? String ?: "")
                }
                if (!map.containsKey("frequency")) {
                    map["frequency"] = "daily"
                }
                if (!map.containsKey("scheduleTime")) {
                    map["scheduleTime"] = "08:00"
                }
                val isKilled = killedTaskIds.contains(taskId)
                val isRunningNow = runningTaskIds.contains(taskId)
                val rawStatus = (map["status"] as? String)?.uppercase() ?: "ACTIVE"
                val enabled = (map["enabled"] as? Boolean) ?: (rawStatus != "PAUSED" && rawStatus != "KILLED" && rawStatus != "STOPPED")

                val effectiveStatus = when {
                    isRunningNow -> "RUNNING"
                    isKilled || rawStatus == "KILLED" -> "KILLED"
                    rawStatus == "WAITING_APPROVAL" -> "WAITING_APPROVAL"
                    enabled -> "ACTIVE"
                    else -> "PAUSED"
                }
                map["status"] = effectiveStatus
                map["enabled"] = enabled && !isKilled
                tasksList.add(map)
            } catch (e: Exception) {
                logger.warning("Skipping malformed task file $rel: ${e.message}")
            }
        }

        // Sort: RUNNING (0) -> WAITING_APPROVAL (1) -> ACTIVE (2) -> PAUSED (3) -> KILLED (4)
        val sortedTasks = tasksList.sortedWith(
            compareBy<Map<String, Any?>> { task ->
                when ((task["status"] as? String)?.uppercase()) {
                    "RUNNING" -> 0
                    "WAITING_APPROVAL" -> 1
                    "ACTIVE" -> 2
                    "PAUSED", "STOPPED" -> 3
                    else -> 4
                }
            }.thenByDescending { (it["lastRunAt"] as? Number)?.toLong() ?: 0L }
        )

        val activeSessions = try {
            resolveOrchestration()?.getActiveSessions() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val auditLogs = fetchRecentAuditLogs(25)
        val engineSuspended = globalEmergencyStopped || (resolveOrchestration()?.isSuspended() == true)

        val payload = linkedMapOf<String, Any?>(
            "tasksDir" to tasksDir,
            "killSwitchEngaged" to engineSuspended,
            "tasks" to sortedTasks,
            "activeSessions" to activeSessions,
            "auditLogs" to auditLogs
        )
        return JsonUtil.toJsonString(payload)
    }

    open fun createTask(
        title: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        requiresApproval: Boolean
    ): String {
        val fs = PlatformFileSystem()
        val tasksDir = resolveTasksDir()

        val slug = title.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .take(32)
            .ifBlank { "task-${Uuid.randomUuid().take(6)}" }

        var taskId = slug
        var filePath = fs.resolve(tasksDir, "$taskId.json")
        if (fs.exists(filePath)) {
            taskId = "$slug-${Uuid.randomUuid().take(4)}"
            filePath = fs.resolve(tasksDir, "$taskId.json")
        }

        globalEmergencyStopped = false
        killedTaskIds.remove(taskId)

        val cleanFreq = frequency.ifBlank { "daily" }
        val cleanTime = scheduleTime.ifBlank { "08:00" }
        val needsHitl = requiresApproval || isApprovalRequiredForPrompt(prompt)

        val taskMap = linkedMapOf<String, Any?>(
            "taskId" to taskId,
            "title" to title.ifBlank { formatTaskTitle(taskId, prompt) },
            "intent" to "autonomous.task",
            "prompt" to prompt,
            "frequency" to cleanFreq,
            "scheduleTime" to cleanTime,
            "enabled" to true,
            "status" to if (needsHitl) "WAITING_APPROVAL" else "ACTIVE",
            "requiresApproval" to needsHitl,
            "approvalReason" to if (needsHitl) "Awaiting user approval before first execution." else "",
            "createdAt" to PlatformSystem.currentTimeMillis(),
            "lastRunAt" to 0L,
            "lastResult" to if (needsHitl) "Waiting for user approval." else "Scheduled ($cleanFreq at $cleanTime)."
        )

        fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskMap), append = false)
        recordAuditLog("TASK_CREATED", "Created task [$taskId] (${taskMap["title"]}) - $cleanFreq at $cleanTime")
        return JsonUtil.toJsonString(taskMap)
    }

    open fun updateTask(
        taskId: String,
        title: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        enabled: Boolean
    ): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()

        if (enabled) {
            killedTaskIds.remove(taskId)
        }
        existing["title"] = title.ifBlank { existing["title"] ?: taskId }
        existing["prompt"] = prompt
        existing["frequency"] = frequency.ifBlank { "daily" }
        existing["scheduleTime"] = scheduleTime.ifBlank { "08:00" }
        existing["enabled"] = enabled
        val curStatus = (existing["status"] as? String)?.uppercase() ?: "ACTIVE"
        existing["status"] = when {
            !enabled -> "PAUSED"
            curStatus == "WAITING_APPROVAL" -> "WAITING_APPROVAL"
            else -> "ACTIVE"
        }

        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_UPDATED", "Updated task [$taskId] (${existing["title"]}), enabled=$enabled")
        return JsonUtil.toJsonString(existing)
    }

    open fun runTaskNow(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        killedTaskIds.remove(taskId)
        existing["enabled"] = true
        val updated = executeTaskFile(filePath, existing, triggeredManually = true, approvedByUser = false)
        return JsonUtil.toJsonString(updated)
    }

    open fun approveTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        killedTaskIds.remove(taskId)
        existing["enabled"] = true
        existing["requiresApproval"] = false
        existing["approvalReason"] = ""
        recordAuditLog("TASK_APPROVED", "User approved task [$taskId]. Executing now.")
        val updated = executeTaskFile(filePath, existing, triggeredManually = true, approvedByUser = true)
        return JsonUtil.toJsonString(updated)
    }

    open fun rejectTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        existing["enabled"] = false
        existing["status"] = "PAUSED"
        existing["approvalReason"] = ""
        existing["lastResult"] = "Execution rejected by user."
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_REJECTED", "User rejected pending approval for task [$taskId].")
        return JsonUtil.toJsonString(existing)
    }

    open fun killTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        killedTaskIds.add(taskId)
        runningTaskIds.remove(taskId)
        existing["enabled"] = false
        existing["status"] = "KILLED"
        existing["lastResult"] = "Halted by Kill Switch."
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_KILLED", "Kill Switch engaged for task [$taskId].")
        return JsonUtil.toJsonString(existing)
    }

    open fun pauseTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        runningTaskIds.remove(taskId)
        existing["enabled"] = false
        existing["status"] = "PAUSED"
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_PAUSED", "Paused task [$taskId].")
        return JsonUtil.toJsonString(existing)
    }

    open fun resumeTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        globalEmergencyStopped = false
        killedTaskIds.remove(taskId)
        existing["enabled"] = true
        existing["status"] = "ACTIVE"
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_RESUMED", "Resumed task [$taskId] to ACTIVE state.")
        return JsonUtil.toJsonString(existing)
    }

    open fun deleteTask(taskId: String): String {
        val (filePath, _) = findTaskFile(taskId)
            ?: return """{"deleted":false,"taskId":"$taskId"}"""
        val fs = PlatformFileSystem()
        killedTaskIds.remove(taskId)
        runningTaskIds.remove(taskId)
        fs.delete(filePath)
        recordAuditLog("TASK_DELETED", "Deleted task definition [$taskId].")
        return """{"deleted":true,"taskId":"$taskId"}"""
    }

    open fun setGlobalKillSwitch(engage: Boolean): String {
        if (engage) {
            emergencyStopAll()
        } else {
            globalEmergencyStopped = false
            killedTaskIds.clear()
            resolveOrchestration()?.toggleSuspend(false)
            recordAuditLog("KILL_SWITCH_RESET", "Global Kill Switch reset; autonomous task execution resumed.")
        }
        return getDashboardStateJson()
    }

    private fun findTaskFile(taskId: String): Pair<String, MutableMap<String, Any?>>? {
        val fs = PlatformFileSystem()
        val tasksDir = resolveTasksDir()
        val directPath = fs.resolve(tasksDir, "$taskId.json")
        if (fs.exists(directPath)) {
            val map = JsonUtil.parseJsonStringToMap(fs.readText(directPath)).toMutableMap<String, Any?>()
            return Pair(directPath, map)
        }
        val files = fs.listRelativePaths(tasksDir, "json")
        for (rel in files) {
            val fullPath = fs.resolve(tasksDir, rel)
            try {
                val map = JsonUtil.parseJsonStringToMap(fs.readText(fullPath)).toMutableMap<String, Any?>()
                if (taskId.equals(map["taskId"]?.toString(), ignoreCase = true) ||
                    taskId.equals(rel.removeSuffix(".json"), ignoreCase = true)
                ) {
                    return Pair(fullPath, map)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun formatTaskTitle(taskId: String, prompt: String): String {
        if (taskId.isNotBlank() && taskId != "autonomous.task") {
            return taskId.split('-', '_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
        }
        return prompt.take(40).ifBlank { "Scheduled Task" }
    }

    private fun recordAuditLog(action: String, details: String?) {
        try {
            val ds = resolveDataService() ?: return
            val log = mutableMapOf<String, Any?>()
            log["action"] = action
            log["details"] = details ?: ""
            log["timestamp"] = PlatformSystem.currentTimeMillis()
            ds.save("AuditLog", log)
        } catch (e: Exception) {
            logger.warning("Failed to write AuditLog from AutonomousAgent: ${e.message}")
        }
    }

    private fun fetchRecentAuditLogs(limit: Int): List<Map<String, Any?>> {
        return try {
            val ds = resolveDataService() ?: return emptyList()
            val page = ds.search(
                mapOf(
                    "entityType" to "AuditLog",
                    "page" to 0,
                    "size" to 100,
                    "isIncludeCount" to false
                )
            )
            page.content
                .map { entity ->
                    val attrs = entity.attributes
                    linkedMapOf<String, Any?>(
                        "action" to (attrs["action"]?.toString() ?: "EVENT"),
                        "details" to (attrs["details"]?.toString() ?: ""),
                        "timestamp" to ((attrs["timestamp"] as? Number)?.toLong() ?: 0L),
                        "pluginName" to (attrs["pluginName"]?.toString() ?: "AutonomousAgent")
                    )
                }
                .sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                .take(limit)
        } catch (e: Exception) {
            logger.warning("Failed to fetch AuditLog entries: ${e.message}")
            emptyList()
        }
    }
}
