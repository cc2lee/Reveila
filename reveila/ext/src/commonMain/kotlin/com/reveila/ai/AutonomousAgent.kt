package com.reveila.ai

import com.reveila.channels.AlertSeverity
import com.reveila.channels.ChannelDeliveryResult
import com.reveila.channels.EnterpriseChannelHub
import com.reveila.channels.ProactiveAlertMessage
import com.reveila.safety.CommandChainAudit
import com.reveila.safety.CommandChainReport
import com.reveila.safety.DefaultFlightRecorder
import com.reveila.safety.FlightRecorder
import com.reveila.safety.GuardedRuntime
import com.reveila.safety.KillSwitch
import com.reveila.safety.SafetyStatus
import com.reveila.safety.SecretManager
import com.reveila.safety.TraceContextHolder
import com.reveila.safety.TraceContextSnapshot
import com.reveila.service.DataService
import com.reveila.system.Constants
import com.reveila.system.Plugin
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.system.SystemComponent
import com.reveila.system.UserPrincipal
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.tools.ToolActivityHub
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.jvm.JvmOverloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Autonomous System Agent and Sovereign Task Control Center.
 *
 * Architecture:
 * - Non-blocking Scanner (`doTask`) inspects `${REVEILA_HOME}/tasks` JSON files and dispatches due tasks.
 * - Multi-worker Sandbox Executor runs each task in an isolated [TaskExecutionSandbox] with its own
 *   [AgentSession], [com.reveila.safety.SecurityPerimeter], cooperative suspend gate, and preemptive kill handle.
 * - Publishes real-time task lifecycle notifications so the main Assistant UI can display live progress
 *   and completion cards without locking up interactive chat.
 */
open class AutonomousAgent : SystemComponent(), KillSwitch {

    private var agenticFabric: AgenticFabric? = null
    private var orchestrationService: OrchestrationService? = null
    private var dataService: DataService? = null
    private var flightRecorder: FlightRecorder? = null
    private var guardedRuntime: GuardedRuntime? = null
    private var llmFactory: LlmProviderFactory? = null
    private var subject: Subject? = null

    private var taskWorkerScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val killedTaskIds: MutableSet<String> = mutableSetOf()
    private val runningTaskIds: MutableSet<String> = mutableSetOf()
    private val activeSandboxes: MutableMap<String, TaskExecutionSandbox> = mutableMapOf()
    private val taskNotifications: MutableList<Map<String, Any?>> = mutableListOf()
    private val fallbackTraceHashes: MutableMap<String, String> = mutableMapOf()
    private var lastFallbackTimestamp: Long = 0L
    private var globalEmergencyStopped: Boolean = false

    /**
     * Enterprise channel hub for proactive push alerts (Slack, Microsoft Teams, Email).
     * Can be replaced in tests or customized at runtime via [EnterpriseChannelHub.registerChannel].
     */
    var channelHub: EnterpriseChannelHub = EnterpriseChannelHub(
        systemHomeProvider = { context?.properties?.getProperty("system.home") },
        secretManagerProvider = {
            try {
                val p = context?.getProxy("SecretManager")
                (p?.getInstance() as? SecretManager) ?: p as? SecretManager
            } catch (_: Exception) {
                null
            }
        },
        allowCustomChannelsProvider = { !isEnterpriseEdition() }
    )

    /**
     * Optional real-time listener invoked whenever a proactive alert is emitted (e.g., to surface
     * native Desktop/Mobile OS push notifications alongside Slack/Teams/Email channel deliveries).
     */
    var proactiveAlertListener: ((ProactiveAlertMessage, List<ChannelDeliveryResult>) -> Unit)? = null

    @Throws(Exception::class)
    override fun onStart() {
        val s = Subject()
        s.principals.add(RolePrincipal("system"))
        s.principals.add(RolePrincipal("autonomous-agent"))
        this.subject = s

        this.taskWorkerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        resolveFabric()
        resolveOrchestration()
        resolveDataService()
        resolveFlightRecorder()
        resolveGuardedRuntime()
        resolveLlmFactory()

        ensureDefaultTasksDirectory()
        logger.info("AutonomousAgent started with multi-sandbox worker pool. Ready to manage and execute tasks.")
    }

    @Throws(Exception::class)
    override fun onStop() {
        for (sbx in activeSandboxes.values.toList()) {
            sbx.killExecution()
        }
        activeSandboxes.clear()
        runningTaskIds.clear()
        taskWorkerScope.cancel()
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

    private fun resolveFlightRecorder(): FlightRecorder? {
        if (flightRecorder == null) {
            try {
                val p = context?.getProxy("FlightRecorder")
                flightRecorder = (p?.getInstance() as? FlightRecorder) ?: p as? FlightRecorder
            } catch (_: Exception) {
            }
        }
        return flightRecorder
    }

    private fun resolveActorId(explicitActorId: String = ""): String {
        val explicit = explicitActorId.trim()
        if (explicit.isNotEmpty()) return explicit
        val fromCtx = TraceContextHolder.getContext()?.actorId?.trim()
        if (!fromCtx.isNullOrEmpty() && fromCtx != "system") return fromCtx
        return "local-user"
    }

    private fun resolveGuardedRuntime(): GuardedRuntime? {
        if (guardedRuntime == null) {
            try {
                val p = context?.getProxy("GuardedRuntime")
                guardedRuntime = (p?.getInstance() as? GuardedRuntime) ?: p as? GuardedRuntime
            } catch (_: Exception) {
            }
        }
        return guardedRuntime
    }

    private fun resolveLlmFactory(): LlmProviderFactory? {
        if (llmFactory == null) {
            try {
                val p = context?.getProxy("LlmProviderFactory")
                llmFactory = (p?.getInstance() as? LlmProviderFactory) ?: p as? LlmProviderFactory
            } catch (_: Exception) {
            }
        }
        return llmFactory
    }

    private fun resolveSystemHome(): String {
        return context?.properties?.getProperty(Constants.SYSTEM_HOME)
            ?: context?.properties?.getProperty("system.home")
            ?: "."
    }

    private fun resolveTasksDir(): String {
        val fs = PlatformFileSystem()
        val systemHome = resolveSystemHome()
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
                    "prompt" to "Find the latest news about AI security and provide a summary.",
                    "frequency" to "daily",
                    "scheduleTime" to "08:00",
                    "executionTarget" to "auto",
                    "requiresApproval" to false,
                    "approvalReason" to "",
                    "enabled" to true,
                    "status" to "ACTIVE",
                    "routeTarget" to "CLOUD",
                    "taskCategory" to "CLOUD_LIVE_RETRIEVAL",
                    "routingReason" to "Static intent 'news.search' mapped to CLOUD_LIVE_RETRIEVAL in O(1) time.",
                    "createdAt" to PlatformSystem.currentTimeMillis(),
                    "lastRunAt" to 0L,
                    "lastResult" to "Scheduled (daily at 08:00)."
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
        val gr = resolveGuardedRuntime()
        for (sbx in activeSandboxes.values.toList()) {
            sbx.killExecution()
            gr?.killSandbox(sbx.sandboxId)
        }
        activeSandboxes.clear()
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
                map["sandboxId"] = ""
                map["currentStep"] = "Halted by Global Kill Switch"
                fs.writeText(fullPath, JsonUtil.toPrettyJsonString(map), append = false)
            }
        } catch (e: Exception) {
            logger.warning("Failed to mark tasks KILLED during emergencyStopAll: ${e.message}")
        }

        recordAuditLog("KILL_SWITCH_EMERGENCY_STOP_ALL", "Global Kill Switch activated. All autonomous task sandboxes halted.")
        emitTaskNotification(
            taskId = "global-kill-switch",
            title = "Global Kill Switch",
            status = "KILLED",
            summary = "All active autonomous task sandboxes were immediately terminated."
        )
    }

    override fun getStatus(agentId: String): SafetyStatus {
        return when {
            globalEmergencyStopped || killedTaskIds.contains(agentId) -> SafetyStatus.KILLED
            activeSandboxes[agentId]?.isSuspended() == true || resolveOrchestration()?.isSuspended() == true ->
                SafetyStatus.PENDING_AUTHORIZATION
            else -> SafetyStatus.ACTIVE
        }
    }

    // =========================================================================
    // Non-Blocking Dispatcher & Per-Task Sandbox Execution
    // =========================================================================

    /**
     * Entry point for recurring task scanning, triggered by the 'runnable' configuration.
     * Scans `${REVEILA_HOME}/tasks` without blocking and dispatches due tasks into isolated sandboxes.
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

                // Skip if already running or suspended in an active sandbox
                val existingSandbox = activeSandboxes[taskId]
                if (existingSandbox != null && existingSandbox.isActive()) {
                    continue
                }

                val rawStatus = (taskDef["status"] as? String)?.uppercase() ?: "ACTIVE"
                val enabled = (taskDef["enabled"] as? Boolean)
                    ?: !(rawStatus in listOf("PAUSED", "SUSPENDED", "STOPPED", "KILLED", "COMPLETED"))
                if (!enabled || rawStatus in listOf("WAITING_APPROVAL", "KILLED", "PAUSED", "SUSPENDED", "COMPLETED")) {
                    continue
                }

                val lastRunAt = (taskDef["lastRunAt"] as? Number)?.toLong() ?: 0L
                val frequency = (taskDef["frequency"] as? String)?.lowercase() ?: "daily"
                val intervalMs = when {
                    frequency.contains("once") -> Long.MAX_VALUE
                    frequency.contains("min") -> 15 * 60 * 1000L
                    frequency.contains("hour") -> 60 * 60 * 1000L
                    frequency.contains("week") -> 7 * 24 * 60 * 60 * 1000L
                    else -> 24 * 60 * 60 * 1000L
                }

                if (lastRunAt > 0L && (now - lastRunAt) < intervalMs) {
                    continue
                }

                dispatchTaskToSandbox(file, taskDef, triggeredManually = false, approvedByUser = false)
            } catch (e: Exception) {
                logger.severe("Failed to scan autonomous task: $file. Error: ${e.message}")
            }
        }
    }

    private fun dispatchTaskToSandbox(
        filePath: String,
        taskDef: MutableMap<String, Any?>,
        triggeredManually: Boolean,
        approvedByUser: Boolean = false
    ): Map<String, Any?> {
        val fs = PlatformFileSystem()
        val taskId = (taskDef["taskId"] as? String) ?: "task-${Uuid.randomUuid().take(6)}"
        val title = (taskDef["title"] as? String) ?: formatTaskTitle(taskId, taskDef["prompt"] as? String ?: "")
        val prompt = (taskDef["prompt"] as? String) ?: ""

        // If already active in a sandbox, return its live state instead of spawning a duplicate
        val existingSandbox = activeSandboxes[taskId]
        if (existingSandbox != null && existingSandbox.isActive()) {
            taskDef["status"] = existingSandbox.state
            taskDef["sandboxId"] = existingSandbox.sandboxId
            taskDef["currentStep"] = existingSandbox.currentStep
            return taskDef
        }

        if (!isAuthorized(taskId) && !triggeredManually) {
            taskDef["status"] = "KILLED"
            taskDef["enabled"] = false
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
            return taskDef
        }

        val requiresApproval = (taskDef["requiresApproval"] as? Boolean) == true ||
            (!approvedByUser && isApprovalRequiredForPrompt(prompt))

        val traceId = (taskDef["traceId"] as? String)?.takeIf { it.isNotBlank() }
            ?: TraceContextHolder.getTraceId()
            ?: Uuid.randomUuid()
        taskDef["traceId"] = traceId
        val initiatedBy = (taskDef["initiatedBy"] as? String)?.takeIf { it.isNotBlank() }
            ?: resolveActorId()
        taskDef["initiatedBy"] = initiatedBy

        if (requiresApproval && !approvedByUser) {
            val reason = "Task requires Human-In-The-Loop approval before executing: \"$title\""
            taskDef["status"] = "WAITING_APPROVAL"
            taskDef["approvalReason"] = reason
            taskDef["sandboxId"] = ""
            taskDef["currentStep"] = "Paused at HITL governance gate"
            recordTaskCommandEvent(
                taskDef = taskDef,
                action = "TASK_WAITING_APPROVAL",
                details = "Task [$taskId] ($title) paused awaiting user approval.",
                actorId = initiatedBy,
                actorType = "USER"
            )
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
            emitTaskNotification(
                taskId = taskId,
                title = title,
                status = "WAITING_APPROVAL",
                summary = reason
            )
            return taskDef
        }

        val shortRunId = Uuid.randomUuid().take(6)
        val sandboxId = "sbx-$taskId-$shortRunId"
        val orch = resolveOrchestration()
        val isolatedSession = orch?.createSession("task-$taskId-$shortRunId", traceId)
            ?: AgentSession("task-$taskId-$shortRunId", traceId, 10)

        val sandbox = TaskExecutionSandbox(
            sandboxId = sandboxId,
            taskId = taskId,
            title = title,
            session = isolatedSession
        )

        activeSandboxes[taskId] = sandbox
        runningTaskIds.add(taskId)
        resolveGuardedRuntime()?.resumeSandbox(sandboxId)

        taskDef["status"] = TaskExecutionSandbox.STATE_RUNNING
        taskDef["approvalReason"] = ""
        taskDef["sandboxId"] = sandboxId
        taskDef["currentStep"] = sandbox.currentStep
        recordTaskCommandEvent(
            taskDef = taskDef,
            action = "TASK_STARTED",
            details = "Launched task [$taskId] in isolated sandbox [$sandboxId]: $prompt",
            actorId = "autonomous-agent",
            actorType = "AGENT"
        )
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskDef), append = false)
        emitTaskNotification(
            taskId = taskId,
            title = title,
            status = "RUNNING",
            summary = "Started in isolated sandbox [$sandboxId]."
        )

        val job = taskWorkerScope.launch {
            executeInsideSandbox(filePath, taskDef, sandbox)
        }
        sandbox.workerJob = job

        return taskDef
    }

    private suspend fun executeInsideSandbox(
        filePath: String,
        initialTaskDef: MutableMap<String, Any?>,
        sandbox: TaskExecutionSandbox
    ) {
        val fs = PlatformFileSystem()
        val taskId = sandbox.taskId
        val title = sandbox.title
        val taskIntent = (initialTaskDef["intent"] as? String) ?: "auto"
        val executionTarget = TaskCapabilityRouter.normalizeExecutionTarget(initialTaskDef["executionTarget"] as? String)
        val prompt = (initialTaskDef["prompt"] as? String) ?: ""

        try {
            // Stage 1: 3-Tier TaskRouter Resolution (1. Explicit executionTarget override -> 2. Static intent registry -> 3. Local SLM GBNF Preprocessor)
            val factory = resolveLlmFactory()
            val localProvider = factory?.getProvider("On-Device Model")
            val assessment = TaskCapabilityRouter.assessTask(
                intent = taskIntent,
                prompt = prompt,
                executionTarget = executionTarget,
                systemHome = resolveSystemHome(),
                localProvider = localProvider
            )

            // Fail fast if executionTarget="local" is forced on a task requiring live internet (CLOUD_LIVE_RETRIEVAL)
            if (assessment.hasCapabilityConflict) {
                val conflictMsg = assessment.capabilityConflictError ?: "Capability conflict for executionTarget='local'."
                sandbox.markFailed(conflictMsg)
                val latestDef = try {
                    JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
                } catch (_: Exception) {
                    initialTaskDef
                }
                latestDef["status"] = "PAUSED"
                latestDef["enabled"] = false
                latestDef["sandboxId"] = ""
                latestDef["executionTarget"] = executionTarget
                latestDef["routeTarget"] = "LOCAL"
                latestDef["taskCategory"] = assessment.taskCategory
                latestDef["routingReason"] = conflictMsg
                latestDef["currentStep"] = conflictMsg
                latestDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
                latestDef["lastResult"] = conflictMsg
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)

                recordAuditLog("TASK_CAPABILITY_CONFLICT", "Task [$taskId] ($title) halted: $conflictMsg")
                emitTaskNotification(
                    taskId = taskId,
                    title = title,
                    status = "FAILED",
                    summary = conflictMsg
                )
                return
            }

            recordAuditLog(
                "TASK_PREPROCESSOR_ROUTED",
                "Task [$taskId] ($title) -> ${assessment.targetRoute} [target=$executionTarget, stage=${assessment.routingStage}, category=${assessment.taskCategory}]"
            )

            sandbox.checkExecutionGate(
                stepLabel = "Stage 1/3 • Preprocessor [${assessment.routingStage}] -> ${assessment.toCompactBadge()}",
                isGlobalSuspended = { resolveOrchestration()?.isSuspended() == true }
            )
            delay(1400)

            // Stage 2: Reference Documents retrieval
            sandbox.checkExecutionGate(
                stepLabel = "Stage 2/3 • Searching Reference Documents & building context",
                isGlobalSuspended = { resolveOrchestration()?.isSuspended() == true }
            )
            val ragContext = try {
                val kvProxy = context?.getProxy("KnowledgeVault")
                val kv = (kvProxy?.getInstance() as? KnowledgeVault) ?: kvProxy as? KnowledgeVault
                kv?.search(prompt, 3)
            } catch (_: Exception) {
                null
            }
            delay(1400)

            // Stage 3: Check Governance LLM & Worker LLM capability against assessment.taskCategory
            // - When Worker/Governance LLM is "Auto", automatically selects best LLM using local-first rule.
            // - When an explicit LLM is selected, that LLM must be used; alert user if it cannot handle the category.
            val govSelection = factory?.resolveGovernanceProviderForIntent(assessment.taskCategory)
            if (govSelection != null && !govSelection.isCapable) {
                val alertMsg = govSelection.errorMessage
                    ?: "Selected Governance LLM '${govSelection.providerName}' cannot handle '${assessment.taskCategory}'."
                sandbox.markFailed(alertMsg)
                val latestDef = try {
                    JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
                } catch (_: Exception) {
                    initialTaskDef
                }
                latestDef["status"] = "PAUSED"
                latestDef["enabled"] = false
                latestDef["sandboxId"] = ""
                latestDef["currentStep"] = alertMsg
                latestDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
                latestDef["lastResult"] = "⚠️ $alertMsg"
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
                recordAuditLog("TASK_LLM_INCOMPATIBLE", "Task [$taskId] ($title): $alertMsg")
                emitTaskNotification(
                    taskId = taskId,
                    title = title,
                    status = "FAILED",
                    summary = "⚠️ $alertMsg"
                )
                return
            }

            val workerSelection = when (executionTarget) {
                "local" -> {
                    val p = factory?.getProvider("On-Device Model")
                    LlmProviderFactory.ProviderSelectionResult(
                        provider = p,
                        providerName = "On-Device Model",
                        isAutoMode = false,
                        isCapable = p != null,
                        intentCategory = assessment.taskCategory
                    )
                }
                "cloud" -> {
                    val configuredCloud = factory?.getConfiguredCloudProvider()
                    val p = configuredCloud?.let { factory.getProvider(it) }
                    if (p == null) {
                        LlmProviderFactory.ProviderSelectionResult(
                            provider = null,
                            providerName = "Cloud LLM",
                            isAutoMode = false,
                            isCapable = false,
                            intentCategory = assessment.taskCategory,
                            errorMessage = "Task requires a Cloud LLM (executionTarget='cloud'), but no Cloud LLM is configured. Please configure one in Studio -> Configuration."
                        )
                    } else {
                        LlmProviderFactory.ProviderSelectionResult(
                            provider = p,
                            providerName = p.getName(),
                            isAutoMode = false,
                            isCapable = true,
                            intentCategory = assessment.taskCategory
                        )
                    }
                }
                else -> factory?.resolveWorkerProviderForIntent(assessment.taskCategory)
            }

            if (workerSelection != null && !workerSelection.isCapable) {
                val alertMsg = workerSelection.errorMessage
                    ?: "Selected Worker LLM '${workerSelection.providerName}' cannot handle '${assessment.taskCategory}'."
                sandbox.markFailed(alertMsg)
                val latestDef = try {
                    JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
                } catch (_: Exception) {
                    initialTaskDef
                }
                latestDef["status"] = "PAUSED"
                latestDef["enabled"] = false
                latestDef["sandboxId"] = ""
                latestDef["currentStep"] = alertMsg
                latestDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
                latestDef["lastResult"] = "⚠️ $alertMsg"
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
                recordAuditLog("TASK_LLM_INCOMPATIBLE", "Task [$taskId] ($title): $alertMsg")
                emitTaskNotification(
                    taskId = taskId,
                    title = title,
                    status = "FAILED",
                    summary = "⚠️ $alertMsg"
                )
                return
            }

            val matchedByIntent = workerSelection?.provider
            val selectedProviderName = workerSelection?.providerName ?: "On-Device Model"
            val isSelectedLocal = selectedProviderName.equals("On-Device Model", ignoreCase = true)

            val stage3Label = if (isSelectedLocal && (!assessment.routeToCloud || matchedByIntent?.supportsIntent(assessment.taskCategory) == true)) {
                "Stage 3/3 • Executing on [$selectedProviderName] (${assessment.taskCategory})"
            } else if (!isSelectedLocal) {
                "Stage 3/3 • Executing on Cloud LLM [$selectedProviderName] (${assessment.taskCategory})"
            } else {
                "Stage 3/3 • Cloud intent (${assessment.taskCategory}) — running local fallback [$selectedProviderName]"
            }

            sandbox.checkExecutionGate(
                stepLabel = stage3Label,
                isGlobalSuspended = { resolveOrchestration()?.isSuspended() == true }
            )

            val fabric = resolveFabric()
            val initiatedBy = (initialTaskDef["initiatedBy"] as? String)?.takeIf { it.isNotBlank() } ?: "local-user"
            val traceId = (initialTaskDef["traceId"] as? String)?.takeIf { it.isNotBlank() }
                ?: sandbox.session.parentTraceId
                ?: Uuid.randomUuid()
            val dispatchSpanId = (initialTaskDef["lastSpanId"] as? String)?.takeIf { it.isNotBlank() }
            val subj = Subject().apply {
                principals.addAll(subject?.principals ?: listOf(RolePrincipal("system"), RolePrincipal("autonomous-agent")))
                principals.add(UserPrincipal(initiatedBy))
            }

            var answerText: String
            var finalStatus = "ACTIVE"
            var escalatedReason = ""
            var capturedToolAttachment: Map<String, Any?>? = null
            val resolvedRouteBadge = if (isSelectedLocal && (!assessment.routeToCloud || matchedByIntent?.supportsIntent(assessment.taskCategory) == true)) {
                "LOCAL ($selectedProviderName)"
            } else if (!isSelectedLocal) {
                "CLOUD ($selectedProviderName)"
            } else {
                "CLOUD -> LOCAL FALLBACK ($selectedProviderName)"
            }

            val taskSourceUrl = (initialTaskDef["sourceUrl"] as? String)?.trim().orEmpty()
            val effectivePrompt = if (taskSourceUrl.isNotBlank()) {
                "$prompt\n\n[Configured Task Source URL: $taskSourceUrl — use virtual_browser with this URL or search template.]"
            } else {
                prompt
            }

            if (fabric != null) {
                val browserUpdatedBefore = ToolActivityHub.getSnapshot().browser.updatedAtMs
                val rawResult = try {
                    if (taskSourceUrl.isNotBlank()) {
                        fabric.getToolCoordinator()?.virtualBrowser?.activeTaskSearchUrlOverride = taskSourceUrl
                    }
                    TraceContextHolder.setContext(
                        TraceContextSnapshot(
                            traceId = traceId,
                            spanId = Uuid.randomUuid().replace("-", "").take(16),
                            parentSpanId = dispatchSpanId,
                            actorId = initiatedBy,
                            actorType = "AGENT"
                        )
                    )
                    fabric.processIntent(sandbox.session, subj, effectivePrompt, preferredProviderName = selectedProviderName)
                } finally {
                    fabric.getToolCoordinator()?.virtualBrowser?.activeTaskSearchUrlOverride = null
                    TraceContextHolder.clear()
                }
                @Suppress("UNCHECKED_CAST")
                val resultAttachment = rawResult["toolAttachment"] as? Map<String, Any?>
                if (resultAttachment != null) {
                    capturedToolAttachment = resultAttachment
                } else {
                    val bSnap = ToolActivityHub.getSnapshot().browser
                    if (bSnap.updatedAtMs > browserUpdatedBefore && bSnap.url.isNotBlank()) {
                        val isSynthesizedFeed = bSnap.rawHtml.contains("<!-- reveila-feed-html -->", ignoreCase = true)
                        capturedToolAttachment = mapOf(
                            "tool" to "virtual_browser",
                            "url" to bSnap.url,
                            "title" to bSnap.title,
                            "statusCode" to bSnap.statusCode,
                            "lastAction" to bSnap.lastAction,
                            "contentPreview" to bSnap.contentPreview.take(1500),
                            "rawHtml" to if (isSynthesizedFeed) bSnap.rawHtml.take(12_000) else ""
                        )
                    }
                }
                val statusStr = rawResult["status"]?.toString() ?: ""
                if (statusStr.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                    finalStatus = "WAITING_APPROVAL"
                    escalatedReason = rawResult["reasoning"]?.toString()
                        ?: "Governance policy escalated task for user approval."
                    answerText = "Waiting for user approval: $escalatedReason"
                } else {
                    val interpreted = fabric.interpretAiResponse(rawResult)
                    val baseOutput = if (interpreted.contains("Failed to get response from LLM") ||
                        interpreted.contains("LLM Invocation Failure")
                    ) {
                        buildFallbackRagSynthesis(title, prompt, ragContext)
                    } else {
                        interpreted
                    }
                    answerText = buildString {
                        appendLine("[Selected LLM: $resolvedRouteBadge • Intent: ${assessment.taskCategory}]")
                        if (assessment.routeToCloud && isSelectedLocal && matchedByIntent?.supportsIntent(assessment.taskCategory) != true) {
                            appendLine("Note: Task classified as ${assessment.taskCategory}. Configure a Cloud LLM with this intent in Studio -> Configuration for live cloud execution.")
                        }
                        append(baseOutput)
                    }.trim()
                }
            } else {
                answerText = buildFallbackRagSynthesis(title, prompt, ragContext)
            }

            // Stage 4: Final gate before committing results
            sandbox.checkExecutionGate(
                stepLabel = "Finalizing output & persisting to tasks/$taskId.json",
                isGlobalSuspended = { resolveOrchestration()?.isSuspended() == true }
            )

            val latestDef = try {
                JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
            } catch (_: Exception) {
                initialTaskDef
            }

            val freq = (latestDef["frequency"] as? String)?.lowercase() ?: "daily"
            val enabled = (latestDef["enabled"] as? Boolean) ?: true

            val resolvedStatus = when {
                finalStatus == "WAITING_APPROVAL" -> "WAITING_APPROVAL"
                freq == "once" -> "COMPLETED"
                enabled -> "ACTIVE"
                else -> "PAUSED"
            }

            latestDef["status"] = resolvedStatus
            latestDef["enabled"] = if (freq == "once") false else enabled
            latestDef["sandboxId"] = ""
            latestDef["routeTarget"] = assessment.targetRoute
            latestDef["taskCategory"] = assessment.taskCategory
            latestDef["routingReason"] = assessment.reason
            latestDef["currentStep"] = if (finalStatus == "WAITING_APPROVAL") {
                "Waiting for user approval"
            } else {
                "Last run [$resolvedRouteBadge] in ${sandbox.sandboxId}"
            }
            if (finalStatus == "WAITING_APPROVAL") {
                latestDef["approvalReason"] = escalatedReason
            }
            latestDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
            latestDef["lastResult"] = answerText
            if (capturedToolAttachment != null) {
                latestDef["lastToolAttachment"] = capturedToolAttachment
            }
            if (latestDef["traceId"] == null || (latestDef["traceId"] as? String).isNullOrBlank()) {
                latestDef["traceId"] = initialTaskDef["traceId"] ?: sandbox.session.parentTraceId
            }
            if (latestDef["lastSpanId"] == null || (latestDef["lastSpanId"] as? String).isNullOrBlank()) {
                latestDef["lastSpanId"] = initialTaskDef["lastSpanId"]
            }

            sandbox.markCompleted()

            if (finalStatus == "WAITING_APPROVAL") {
                recordTaskCommandEvent(
                    taskDef = latestDef,
                    action = "TASK_WAITING_APPROVAL",
                    details = "Task [$taskId] escalated for approval: $escalatedReason",
                    actorId = "autonomous-agent",
                    actorType = "AGENT"
                )
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
                emitTaskNotification(
                    taskId = taskId,
                    title = title,
                    status = "WAITING_APPROVAL",
                    summary = "Escalated for approval: $escalatedReason"
                )
            } else {
                recordTaskCommandEvent(
                    taskDef = latestDef,
                    action = "TASK_COMPLETED",
                    details = "Task [$taskId] completed via $resolvedRouteBadge in sandbox [${sandbox.sandboxId}]: ${answerText.take(160)}",
                    actorId = "autonomous-agent",
                    actorType = "AGENT"
                )
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
                emitTaskNotification(
                    taskId = taskId,
                    title = title,
                    status = "COMPLETED",
                    summary = answerText.take(1200),
                    toolAttachment = capturedToolAttachment
                )
            }
        } catch (ce: CancellationException) {
            sandbox.killExecution()
            try {
                val latestDef = JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
                latestDef["status"] = "KILLED"
                latestDef["enabled"] = false
                latestDef["sandboxId"] = ""
                latestDef["currentStep"] = "Terminated in sandbox [${sandbox.sandboxId}]"
                latestDef["lastResult"] = "Task execution terminated by user."
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            sandbox.markFailed("Error: ${e.message}")
            try {
                val latestDef = JsonUtil.parseJsonStringToMap(fs.readText(filePath)).toMutableMap<String, Any?>()
                latestDef["status"] = "ACTIVE"
                latestDef["sandboxId"] = ""
                latestDef["currentStep"] = "Failed: ${e.message}"
                latestDef["lastRunAt"] = PlatformSystem.currentTimeMillis()
                latestDef["lastResult"] = "Execution error: ${e.message}"
                if (latestDef["traceId"] == null || (latestDef["traceId"] as? String).isNullOrBlank()) {
                    latestDef["traceId"] = initialTaskDef["traceId"] ?: sandbox.session.parentTraceId
                }
                if (latestDef["lastSpanId"] == null || (latestDef["lastSpanId"] as? String).isNullOrBlank()) {
                    latestDef["lastSpanId"] = initialTaskDef["lastSpanId"]
                }
                recordTaskCommandEvent(
                    taskDef = latestDef,
                    action = "TASK_FAILED",
                    details = "Task [$taskId] failed in sandbox [${sandbox.sandboxId}]: ${e.message}",
                    actorId = "autonomous-agent",
                    actorType = "AGENT"
                )
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(latestDef), append = false)
            } catch (_: Exception) {
                recordTaskCommandEvent(
                    taskDef = initialTaskDef,
                    action = "TASK_FAILED",
                    details = "Task [$taskId] failed in sandbox [${sandbox.sandboxId}]: ${e.message}",
                    actorId = "autonomous-agent",
                    actorType = "AGENT"
                )
            }
            emitTaskNotification(
                taskId = taskId,
                title = title,
                status = "FAILED",
                summary = "Execution failed: ${e.message ?: "Unknown error"}"
            )
        } finally {
            runningTaskIds.remove(taskId)
            activeSandboxes.remove(taskId, sandbox)
            resolveOrchestration()?.closeSession(sandbox.session.sessionId)
        }
    }

    private fun buildFallbackRagSynthesis(title: String, prompt: String, ragSnippets: String?): String {
        return buildString {
            appendLine("Sovereign Sandbox Synthesis ($title):")
            if (!ragSnippets.isNullOrBlank() && !ragSnippets.contains("No relevant internal documents")) {
                appendLine(ragSnippets.trim())
            } else {
                appendLine("Completed autonomous execution for \"$prompt\" against Reference Documents within isolated sandbox perimeter.")
            }
        }.trim()
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

    internal fun emitTaskNotification(
        taskId: String,
        title: String,
        status: String,
        summary: String,
        toolAttachment: Map<String, Any?>? = null
    ) {
        val now = PlatformSystem.currentTimeMillis()
        val eventId = "evt-$now-${Uuid.randomUuid().take(4)}"
        val alert = ProactiveAlertMessage(
            eventId = eventId,
            taskId = taskId,
            title = title,
            status = status,
            summary = summary,
            severity = AlertSeverity.fromStatus(status),
            timestampMs = now
        )
        val deliveries = try {
            channelHub.dispatchAlert(alert)
        } catch (e: Exception) {
            logger.warning("Proactive channel dispatch error for task [$taskId]: ${e.message}")
            emptyList()
        }
        try {
            proactiveAlertListener?.invoke(alert, deliveries)
        } catch (_: Exception) {
        }

        val event = linkedMapOf<String, Any?>(
            "eventId" to eventId,
            "taskId" to taskId,
            "title" to title,
            "status" to status,
            "severity" to alert.severity.name,
            "summary" to summary,
            "timestamp" to now
        )
        if (deliveries.isNotEmpty()) {
            event["channelDeliveries"] = deliveries.map {
                mapOf(
                    "channelId" to it.channelId,
                    "type" to it.channelType.name,
                    "ok" to it.ok,
                    "message" to it.message
                )
            }
        }
        if (toolAttachment != null) {
            event["toolAttachment"] = toolAttachment
        }
        taskNotifications.add(0, event)
        while (taskNotifications.size > 40) {
            taskNotifications.removeAt(taskNotifications.lastIndex)
        }
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

                val liveSandbox = activeSandboxes[taskId]
                val isKilled = killedTaskIds.contains(taskId)
                val rawStatus = (map["status"] as? String)?.uppercase() ?: "ACTIVE"
                val enabled = (map["enabled"] as? Boolean)
                    ?: (rawStatus !in listOf("PAUSED", "SUSPENDED", "KILLED", "STOPPED", "COMPLETED"))

                val effectiveStatus = when {
                    liveSandbox != null && liveSandbox.isSuspended() -> "SUSPENDED"
                    liveSandbox != null && liveSandbox.isActive() -> "RUNNING"
                    isKilled || rawStatus == "KILLED" -> "KILLED"
                    rawStatus == "WAITING_APPROVAL" -> "WAITING_APPROVAL"
                    rawStatus == "COMPLETED" -> "COMPLETED"
                    rawStatus == "SUSPENDED" -> "PAUSED"
                    enabled -> "ACTIVE"
                    else -> "PAUSED"
                }
                map["status"] = effectiveStatus
                map["enabled"] = enabled && !isKilled
                map["sandboxId"] = liveSandbox?.sandboxId ?: (map["sandboxId"] as? String ?: "")
                map["currentStep"] = liveSandbox?.currentStep ?: (map["currentStep"] as? String ?: "")
                val execTarget = TaskCapabilityRouter.normalizeExecutionTarget(map["executionTarget"] as? String)
                map["executionTarget"] = execTarget

                // Populate or normalize 6-category Preprocessor Route preview
                val rawCat = (map["taskCategory"] as? String).orEmpty()
                val isModernCat = TaskRoutingCategory.entries.any { it.name == rawCat }
                if (!isModernCat || (map["routeTarget"] as? String).isNullOrBlank()) {
                    val previewAssessment = TaskCapabilityRouter.assessTask(
                        intent = (map["intent"] as? String) ?: "auto",
                        prompt = (map["prompt"] as? String) ?: "",
                        executionTarget = execTarget,
                        systemHome = resolveSystemHome(),
                        localProvider = null
                    )
                    map["routeTarget"] = previewAssessment.targetRoute
                    map["taskCategory"] = previewAssessment.taskCategory
                    map["routingReason"] = previewAssessment.reason
                }
                tasksList.add(map)
            } catch (e: Exception) {
                logger.warning("Skipping malformed task file $rel: ${e.message}")
            }
        }

        // Sort: RUNNING (0) -> SUSPENDED (1) -> WAITING_APPROVAL (2) -> ACTIVE (3) -> PAUSED (4) -> COMPLETED/KILLED (5)
        val sortedTasks = tasksList.sortedWith(
            compareBy<Map<String, Any?>> { task ->
                when ((task["status"] as? String)?.uppercase()) {
                    "RUNNING" -> 0
                    "SUSPENDED" -> 1
                    "WAITING_APPROVAL" -> 2
                    "ACTIVE" -> 3
                    "PAUSED", "STOPPED" -> 4
                    else -> 5
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
            "activeSandboxes" to activeSandboxes.values.map { it.toMetadataMap() },
            "notifications" to taskNotifications.take(20),
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
        return createTaskWithIntent(
            title = title,
            intent = "auto",
            prompt = prompt,
            frequency = frequency,
            scheduleTime = scheduleTime,
            requiresApproval = requiresApproval
        )
    }

    @JvmOverloads
    open fun createTaskWithIntent(
        title: String,
        intent: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        requiresApproval: Boolean,
        executionTarget: String = "auto",
        actorId: String = "",
        sourceUrl: String = ""
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

        val cleanIntent = intent.trim().ifBlank { "auto" }
        val cleanTarget = TaskCapabilityRouter.normalizeExecutionTarget(executionTarget)
        val cleanFreq = frequency.ifBlank { "daily" }
        val cleanTime = scheduleTime.ifBlank { "08:00" }
        val needsHitl = requiresApproval || isApprovalRequiredForPrompt(prompt)
        val resolvedTitle = title.ifBlank { formatTaskTitle(taskId, prompt) }
        val initiator = resolveActorId(actorId)
        val activeCtx = TraceContextHolder.getContext()
        val traceId = activeCtx?.traceId?.takeIf { it.isNotBlank() } ?: Uuid.randomUuid()
        val rootSpanId = Uuid.randomUuid().replace("-", "").take(16)

        val assessment = TaskCapabilityRouter.assessTask(
            intent = cleanIntent,
            prompt = prompt,
            executionTarget = cleanTarget,
            systemHome = resolveSystemHome(),
            localProvider = resolveLlmFactory()?.getProvider("On-Device Model")
        )

        val taskMap = linkedMapOf<String, Any?>(
            "taskId" to taskId,
            "title" to resolvedTitle,
            "intent" to cleanIntent,
            "prompt" to prompt,
            "sourceUrl" to sourceUrl.trim(),
            "frequency" to cleanFreq,
            "scheduleTime" to cleanTime,
            "executionTarget" to cleanTarget,
            "enabled" to true,
            "status" to if (needsHitl) "WAITING_APPROVAL" else "ACTIVE",
            "requiresApproval" to needsHitl,
            "approvalReason" to if (needsHitl) "Awaiting user approval before first execution." else "",
            "sandboxId" to "",
            "traceId" to traceId,
            "lastSpanId" to rootSpanId,
            "initiatedBy" to initiator,
            "approvedBy" to "",
            "rejectedBy" to "",
            "routeTarget" to assessment.targetRoute,
            "taskCategory" to assessment.taskCategory,
            "routingReason" to assessment.reason,
            "currentStep" to if (needsHitl) "Waiting for user approval" else "Scheduled ($cleanFreq at $cleanTime) • ${assessment.taskCategory}",
            "createdAt" to PlatformSystem.currentTimeMillis(),
            "lastRunAt" to 0L,
            "lastResult" to if (needsHitl) "Waiting for user approval." else "Scheduled ($cleanFreq at $cleanTime). ${assessment.reason}"
        )

        recordTaskCommandEvent(
            taskDef = taskMap,
            action = "TASK_CREATED",
            details = "Created task [$taskId] ($resolvedTitle) - executionTarget=$cleanTarget, category=${assessment.taskCategory}",
            actorId = initiator,
            actorType = "USER",
            explicitSpanId = rootSpanId,
            explicitParentSpanId = activeCtx?.spanId
        )
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(taskMap), append = false)
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
        return updateTaskWithIntent(
            taskId = taskId,
            title = title,
            intent = "",
            prompt = prompt,
            frequency = frequency,
            scheduleTime = scheduleTime,
            enabled = enabled,
            requiresApproval = false
        )
    }

    @JvmOverloads
    open fun updateTaskWithIntent(
        taskId: String,
        title: String,
        intent: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        enabled: Boolean,
        requiresApproval: Boolean = false,
        executionTarget: String = "",
        sourceUrl: String = ""
    ): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()

        if (enabled) {
            killedTaskIds.remove(taskId)
        }
        val cleanIntent = intent.trim().ifBlank { (existing["intent"] as? String) ?: "auto" }
        val cleanTarget = TaskCapabilityRouter.normalizeExecutionTarget(
            executionTarget.ifBlank { (existing["executionTarget"] as? String) ?: "auto" }
        )
        val assessment = TaskCapabilityRouter.assessTask(
            intent = cleanIntent,
            prompt = prompt,
            executionTarget = cleanTarget,
            systemHome = resolveSystemHome(),
            localProvider = resolveLlmFactory()?.getProvider("On-Device Model")
        )
        val needsHitl = requiresApproval || isApprovalRequiredForPrompt(prompt)

        existing["title"] = title.ifBlank { existing["title"] ?: taskId }
        existing["intent"] = cleanIntent
        existing["prompt"] = prompt
        existing["sourceUrl"] = sourceUrl.trim()
        existing["frequency"] = frequency.ifBlank { "daily" }
        existing["scheduleTime"] = scheduleTime.ifBlank { "08:00" }
        existing["executionTarget"] = cleanTarget
        existing["requiresApproval"] = needsHitl
        if (needsHitl && (existing["approvalReason"] as? String).isNullOrBlank()) {
            existing["approvalReason"] = "Awaiting user approval before execution."
        } else if (!needsHitl) {
            existing["approvalReason"] = ""
        }
        existing["enabled"] = enabled
        existing["routeTarget"] = assessment.targetRoute
        existing["taskCategory"] = assessment.taskCategory
        existing["routingReason"] = assessment.reason
        val curStatus = (existing["status"] as? String)?.uppercase() ?: "ACTIVE"
        existing["status"] = when {
            !enabled -> "PAUSED"
            needsHitl || curStatus == "WAITING_APPROVAL" -> "WAITING_APPROVAL"
            else -> "ACTIVE"
        }

        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_UPDATED", "Updated task [$taskId] (${existing["title"]}), executionTarget=$cleanTarget, category=${assessment.taskCategory}")
        return JsonUtil.toJsonString(existing)
    }

    open fun updateTaskSourceUrl(taskId: String, sourceUrl: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        val cleaned = sourceUrl.trim()
        existing["sourceUrl"] = cleaned
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_SOURCE_URL_UPDATED", "Updated sourceUrl for task [$taskId] to '${cleaned.ifBlank { "default" }}'")
        return JsonUtil.toJsonString(existing)
    }

    open fun runTaskNow(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        globalEmergencyStopped = false
        killedTaskIds.remove(taskId)
        existing["enabled"] = true
        val updated = dispatchTaskToSandbox(filePath, existing, triggeredManually = true, approvedByUser = false)
        return JsonUtil.toJsonString(updated)
    }

    @JvmOverloads
    open fun approveTask(taskId: String, approverId: String = ""): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val approver = resolveActorId(approverId)
        globalEmergencyStopped = false
        killedTaskIds.remove(taskId)
        existing["enabled"] = true
        existing["requiresApproval"] = false
        existing["approvalReason"] = ""
        existing["approvedBy"] = approver
        existing["approvedAt"] = PlatformSystem.currentTimeMillis()
        recordTaskCommandEvent(
            taskDef = existing,
            action = "TASK_APPROVED",
            details = "User [$approver] approved task [$taskId]. Launching in sandbox.",
            actorId = approver,
            actorType = "USER"
        )
        val updated = dispatchTaskToSandbox(filePath, existing, triggeredManually = true, approvedByUser = true)
        return JsonUtil.toJsonString(updated)
    }

    @JvmOverloads
    open fun rejectTask(taskId: String, rejectorId: String = ""): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        val rejector = resolveActorId(rejectorId)
        existing["enabled"] = false
        existing["status"] = "PAUSED"
        existing["approvalReason"] = ""
        existing["rejectedBy"] = rejector
        existing["rejectedAt"] = PlatformSystem.currentTimeMillis()
        existing["lastResult"] = "Execution rejected by user [$rejector]."
        recordTaskCommandEvent(
            taskDef = existing,
            action = "TASK_REJECTED",
            details = "User [$rejector] rejected pending approval for task [$taskId].",
            actorId = rejector,
            actorType = "USER"
        )
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        return JsonUtil.toJsonString(existing)
    }

    open fun killTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        val title = (existing["title"] as? String) ?: taskId

        val liveSandbox = activeSandboxes.remove(taskId)
        if (liveSandbox != null) {
            liveSandbox.killExecution()
            resolveGuardedRuntime()?.killSandbox(liveSandbox.sandboxId)
            resolveOrchestration()?.closeSession(liveSandbox.session.sessionId)
        }

        killedTaskIds.add(taskId)
        runningTaskIds.remove(taskId)
        existing["enabled"] = false
        existing["status"] = "KILLED"
        existing["sandboxId"] = ""
        existing["currentStep"] = "Terminated by user"
        existing["lastResult"] = "Halted by per-task Kill Switch."
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)

        recordAuditLog("TASK_KILLED", "Kill Switch engaged for task [$taskId] (sandbox=${liveSandbox?.sandboxId ?: "none"}).")
        emitTaskNotification(
            taskId = taskId,
            title = title,
            status = "KILLED",
            summary = "Task terminated by user${if (liveSandbox != null) " in sandbox [${liveSandbox.sandboxId}]" else ""}."
        )
        return JsonUtil.toJsonString(existing)
    }

    open fun pauseTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        val title = (existing["title"] as? String) ?: taskId

        val liveSandbox = activeSandboxes[taskId]
        if (liveSandbox != null && liveSandbox.isActive()) {
            liveSandbox.suspendExecution()
            resolveGuardedRuntime()?.suspendSandbox(liveSandbox.sandboxId)
            existing["status"] = "SUSPENDED"
            existing["currentStep"] = liveSandbox.currentStep
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
            recordAuditLog("TASK_SUSPENDED", "Suspended in-progress task [$taskId] in sandbox [${liveSandbox.sandboxId}].")
            emitTaskNotification(
                taskId = taskId,
                title = title,
                status = "SUSPENDED",
                summary = "Suspended execution in sandbox [${liveSandbox.sandboxId}]."
            )
            return JsonUtil.toJsonString(existing)
        }

        runningTaskIds.remove(taskId)
        existing["enabled"] = false
        existing["status"] = "PAUSED"
        existing["currentStep"] = "Paused by user"
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_PAUSED", "Paused scheduled task [$taskId].")
        return JsonUtil.toJsonString(existing)
    }

    open fun resumeTask(taskId: String): String {
        val (filePath, existing) = findTaskFile(taskId)
            ?: return """{"error":"Task not found: $taskId"}"""
        val fs = PlatformFileSystem()
        val title = (existing["title"] as? String) ?: taskId
        globalEmergencyStopped = false
        killedTaskIds.remove(taskId)

        val liveSandbox = activeSandboxes[taskId]
        if (liveSandbox != null && liveSandbox.isSuspended()) {
            liveSandbox.resumeExecution()
            resolveGuardedRuntime()?.resumeSandbox(liveSandbox.sandboxId)
            existing["enabled"] = true
            existing["status"] = "RUNNING"
            existing["currentStep"] = liveSandbox.currentStep
            fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
            recordAuditLog("TASK_RESUMED", "Resumed suspended task [$taskId] in sandbox [${liveSandbox.sandboxId}].")
            emitTaskNotification(
                taskId = taskId,
                title = title,
                status = "RUNNING",
                summary = "Resumed execution in sandbox [${liveSandbox.sandboxId}]."
            )
            return JsonUtil.toJsonString(existing)
        }

        existing["enabled"] = true
        existing["status"] = "ACTIVE"
        existing["currentStep"] = "Scheduled and active"
        fs.writeText(filePath, JsonUtil.toPrettyJsonString(existing), append = false)
        recordAuditLog("TASK_RESUMED", "Resumed task [$taskId] to ACTIVE state.")
        return JsonUtil.toJsonString(existing)
    }

    open fun deleteTask(taskId: String): String {
        val (filePath, _) = findTaskFile(taskId)
            ?: return """{"deleted":false,"taskId":"$taskId"}"""
        val fs = PlatformFileSystem()
        val liveSandbox = activeSandboxes.remove(taskId)
        if (liveSandbox != null) {
            liveSandbox.killExecution()
            resolveGuardedRuntime()?.killSandbox(liveSandbox.sandboxId)
            resolveOrchestration()?.closeSession(liveSandbox.session.sessionId)
        }
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

    open fun getTraceChainJson(traceId: String): String {
        val cleanTraceId = traceId.trim()
        if (cleanTraceId.isEmpty()) {
            return JsonUtil.toJsonString(
                mapOf(
                    "traceId" to "",
                    "error" to "traceId is required",
                    "eventCount" to 0,
                    "chainIntegrityVerified" to false
                )
            )
        }
        val fr = resolveFlightRecorder()
        val report: CommandChainReport? = if (fr is DefaultFlightRecorder) {
            fr.reconstructCommandChain(cleanTraceId)
        } else {
            resolveDataService()?.let { CommandChainAudit.reconstructTrace(cleanTraceId, it) }
        }
        if (report == null) {
            return JsonUtil.toJsonString(
                mapOf(
                    "traceId" to cleanTraceId,
                    "error" to "DataService not available",
                    "eventCount" to 0,
                    "chainIntegrityVerified" to false
                )
            )
        }
        return report.toJsonString()
    }

    private fun recordTaskCommandEvent(
        taskDef: MutableMap<String, Any?>?,
        action: String,
        details: String,
        actorId: String,
        actorType: String,
        explicitSpanId: String? = null,
        explicitParentSpanId: String? = null
    ) {
        try {
            val traceId = (taskDef?.get("traceId") as? String)?.takeIf { it.isNotBlank() }
                ?: TraceContextHolder.getTraceId()
                ?: Uuid.randomUuid()
            val parentSpanId = explicitParentSpanId
                ?: (taskDef?.get("lastSpanId") as? String)?.takeIf { it.isNotBlank() }
                ?: TraceContextHolder.getContext()?.spanId
            val spanId = explicitSpanId ?: Uuid.randomUuid().replace("-", "").take(16)
            if (taskDef != null) {
                taskDef["traceId"] = traceId
                taskDef["lastSpanId"] = spanId
            }

            val fr = resolveFlightRecorder()
            if (fr != null) {
                val sessionId = (taskDef?.get("sandboxId") as? String)?.takeIf { it.isNotBlank() }
                    ?: (taskDef?.get("taskId") as? String)?.takeIf { it.isNotBlank() }
                    ?: "autonomous-agent"
                val plugin = Plugin(
                    sessionId = sessionId,
                    name = "AutonomousAgent",
                    tenantId = "default",
                    traceId = traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    actorId = actorId,
                    actorType = actorType
                )
                val meta = mutableMapOf<String, Any>(
                    "component" to "AutonomousAgent"
                )
                val taskId = taskDef?.get("taskId") as? String
                if (!taskId.isNullOrBlank()) {
                    meta["taskId"] = taskId
                }
                fr.recordCommandEvent(
                    plugin = plugin,
                    action = action,
                    details = details,
                    actorId = actorId,
                    actorType = actorType,
                    metadata = meta
                )
                return
            }

            val ds = resolveDataService() ?: return
            val nowMs = PlatformSystem.currentTimeMillis()
            val timestamp = if (nowMs <= lastFallbackTimestamp) lastFallbackTimestamp + 1L else nowMs
            lastFallbackTimestamp = timestamp
            val recordId = "${timestamp}-${Uuid.randomUuid().take(8)}"
            val prevHash = fallbackTraceHashes[traceId] ?: ""
            val eventHash = CommandChainAudit.computeEventHash(
                prevEventHash = prevHash,
                traceId = traceId,
                spanId = spanId,
                parentSpanId = parentSpanId,
                actorId = actorId,
                actorType = actorType,
                action = action,
                details = details,
                timestamp = timestamp
            )
            fallbackTraceHashes[traceId] = eventHash

            val log = mutableMapOf<String, Any?>()
            log["recordId"] = recordId
            log["traceId"] = traceId
            log["spanId"] = spanId
            log["parentSpanId"] = parentSpanId ?: ""
            log["actorId"] = actorId
            log["actorType"] = actorType
            log["pluginName"] = "AutonomousAgent"
            log["sessionId"] = taskDef?.get("sandboxId")?.toString().orEmpty()
            log["tenantId"] = "default"
            log["action"] = action
            log["details"] = details
            log["timestamp"] = timestamp
            log["prevEventHash"] = prevHash
            log["eventHash"] = eventHash
            ds.save("AuditLog", log)
        } catch (e: Exception) {
            logger.warning("Failed to write AuditLog from AutonomousAgent: ${e.message}")
        }
    }

    private fun recordAuditLog(action: String, details: String?) {
        val ctx = TraceContextHolder.getContext()
        recordTaskCommandEvent(
            taskDef = null,
            action = action,
            details = details ?: "",
            actorId = ctx?.actorId ?: "system",
            actorType = ctx?.actorType ?: "SYSTEM"
        )
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

    // =========================================================================
    // Enterprise / Consumer Channel Configuration & Test Alert RPCs
    // =========================================================================

    /**
     * Returns the configured deployment edition from `reveila.properties` (`app.edition`):
     * `"enterprise"` or `"individual"` (default).
     */
    open fun getAppEdition(): String {
        val raw = context?.properties?.getProperty("app.edition")?.trim()?.lowercase()
        return if (raw == "enterprise") "enterprise" else "individual"
    }

    open fun isEnterpriseEdition(): Boolean = getAppEdition() == "enterprise"

    private fun resolveChannelsConfigPath(): String {
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: fs.getDefaultSystemHome()
        return fs.resolve(home, "configs/settings/channels.json")
    }

    open fun getChannelsConfigJson(): String {
        val fs = PlatformFileSystem()
        val path = resolveChannelsConfigPath()
        if (fs.exists(path)) {
            return try {
                fs.readText(path)
            } catch (_: Exception) {
                defaultChannelsConfigJson()
            }
        }
        return defaultChannelsConfigJson()
    }

    open fun saveChannelsConfigJson(rawJson: String): String {
        return try {
            val fs = PlatformFileSystem()
            val root = JsonUtil.parseJsonStringToMap(rawJson).toMutableMap<String, Any?>()
            val rawChannels = (root["channels"] ?: root["Channels"]) as? List<*>
                ?: return "Error: 'channels' array is required."

            val enterpriseMode = isEnterpriseEdition()
            val approvedIds = if (enterpriseMode) {
                com.reveila.channels.EnterpriseChannelHub.parseChannelsJson(getChannelsConfigJson())
                    .map { it.id.lowercase() }
                    .toSet()
            } else {
                emptySet()
            }

            val sanitizedChannels = mutableListOf<MutableMap<String, Any?>>()
            for (item in rawChannels) {
                val map = (item as? Map<*, *>)?.entries
                    ?.associate { (k, v) -> k.toString() to v }
                    ?.toMutableMap()
                    ?: continue
                val id = ((map["Id"] ?: map["id"]) as? String)?.trim().orEmpty()
                val type = ((map["Type"] ?: map["type"]) as? String)?.trim()?.uppercase().orEmpty()
                if (id.isBlank()) continue
                if (enterpriseMode) {
                    val parsedType = com.reveila.channels.ChannelType.fromRaw(type)
                    if (parsedType == com.reveila.channels.ChannelType.CUSTOM_WEBHOOK) {
                        return "Error: Custom webhook channels are disabled in enterprise edition."
                    }
                    if (approvedIds.isNotEmpty() && !approvedIds.contains(id.lowercase())) {
                        return "Error: Adding unapproved channel '$id' is disabled in enterprise edition."
                    }
                }
                val safePrefix = id.replace(Regex("[^A-Za-z0-9]+"), "_").uppercase()

                // Store raw Api.Key blindly in SecretManager and replace with REF:
                val rawApiKey = ((map["Api.Key"] ?: map["Secret.Ref"] ?: map["secretRef"]) as? String)?.trim().orEmpty()
                if (rawApiKey.isNotBlank() && !rawApiKey.startsWith("REF:", ignoreCase = true) && !rawApiKey.startsWith("SEC:", ignoreCase = true)) {
                    val secretKey = "${safePrefix}_API_KEY"
                    try {
                        context?.getProxy("SecretManager")?.invoke("storeSecret", arrayOf<Any?>(secretKey, rawApiKey))
                        map.remove("secretRef")
                        map.remove("Secret.Ref")
                        map["Api.Key"] = "REF:$secretKey"
                    } catch (e: Exception) {
                        logger.warning("Failed to store channel API key in SecretManager: ${e.message}")
                    }
                }

                // Store raw Slack/Teams/Custom webhook URLs in SecretManager so webhook tokens aren't left in plaintext
                val rawEndpoint = ((map["Endpoint"] ?: map["endpoint"] ?: map["Webhook.Url"]) as? String)?.trim().orEmpty()
                if ((type == "SLACK" || type == "MICROSOFT_TEAMS" || type == "CUSTOM_WEBHOOK") &&
                    rawEndpoint.startsWith("https://", ignoreCase = true) &&
                    (rawEndpoint.contains("webhook", ignoreCase = true) ||
                        rawEndpoint.contains("hooks.slack.com", ignoreCase = true) ||
                        rawEndpoint.contains("discord.com/api/webhooks", ignoreCase = true))
                ) {
                    val secretKey = "${safePrefix}_WEBHOOK_URL"
                    try {
                        context?.getProxy("SecretManager")?.invoke("storeSecret", arrayOf<Any?>(secretKey, rawEndpoint))
                        map.remove("endpoint")
                        map.remove("Webhook.Url")
                        map["Endpoint"] = "REF:$secretKey"
                    } catch (e: Exception) {
                        logger.warning("Failed to store channel webhook URL in SecretManager: ${e.message}")
                    }
                }

                sanitizedChannels.add(map)
            }

            val outMap = linkedMapOf<String, Any?>("channels" to sanitizedChannels)
            val path = resolveChannelsConfigPath()
            val parentDir = path.replace('\\', '/').substringBeforeLast('/', "")
            if (parentDir.isNotBlank() && !fs.exists(parentDir)) {
                fs.createDirectories(parentDir)
            }
            fs.writeText(path, JsonUtil.toPrettyJsonString(outMap), append = false)
            recordAuditLog("CHANNELS_CONFIG_UPDATED", "Updated ${sanitizedChannels.size} proactive alert channels.")
            "OK"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    open fun sendTestChannelAlert(channelId: String): String {
        val cleanId = channelId.trim()
        if (cleanId.isBlank()) {
            return JsonUtil.toJsonString(mapOf("ok" to false, "message" to "Channel ID is required."))
        }
        val configured = channelHub.loadConfiguredChannels()
            .firstOrNull { it.id.equals(cleanId, ignoreCase = true) }
            ?: return JsonUtil.toJsonString(
                mapOf("ok" to false, "message" to "Channel '$cleanId' not found. Save your configuration first.")
            )
        if (!configured.enabled) {
            return JsonUtil.toJsonString(
                mapOf("ok" to false, "message" to "Channel '${configured.id}' is currently disabled. Enable it before testing.")
            )
        }
        val testAlert = ProactiveAlertMessage(
            eventId = "test-${PlatformSystem.currentTimeMillis()}",
            taskId = "channel-test",
            title = "Aegin Test Alert",
            status = "COMPLETED",
            summary = "Your ${configured.type.name.replace('_', ' ')} alert connector (${configured.id}) is connected and ready for proactive notifications.",
            severity = AlertSeverity.CRITICAL,
            timestampMs = PlatformSystem.currentTimeMillis()
        )
        val result = channelHub.dispatchAlert(testAlert, onlyChannelIds = setOf(configured.id)).firstOrNull()
            ?: return JsonUtil.toJsonString(
                mapOf("ok" to false, "message" to "No delivery attempted for '${configured.id}'.")
            )
        return JsonUtil.toJsonString(
            mapOf(
                "ok" to result.ok,
                "channelId" to result.channelId,
                "type" to result.channelType.name,
                "message" to result.message
            )
        )
    }

    private fun defaultChannelsConfigJson(): String {
        return JsonUtil.toPrettyJsonString(
            mapOf(
                "channels" to listOf(
                    mapOf(
                        "Id" to "slack-ops",
                        "Type" to "SLACK",
                        "Enabled" to false,
                        "Endpoint" to "",
                        "Channel" to "#alerts",
                        "Min.Severity" to "SUCCESS"
                    ),
                    mapOf(
                        "Id" to "teams-ops",
                        "Type" to "MICROSOFT_TEAMS",
                        "Enabled" to false,
                        "Endpoint" to "",
                        "Min.Severity" to "ACTION_REQUIRED"
                    ),
                    mapOf(
                        "Id" to "email-digest",
                        "Type" to "EMAIL",
                        "Enabled" to false,
                        "Endpoint" to "https://api.resend.com/emails",
                        "Api.Key" to "",
                        "From" to "",
                        "To" to emptyList<String>(),
                        "Min.Severity" to "WARNING"
                    )
                )
            )
        )
    }

    // =========================================================================
    // Governance Policy RPCs (Editable in Individual Edition, Read-Only in Enterprise)
    // =========================================================================

    private fun resolveGovernancePolicyPath(): String {
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home")
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?: fs.getDefaultSystemHome()
        return fs.resolve(fs.resolve(home, "configs"), "governance-policy.json")
    }

    open fun getGovernancePolicyJson(): String {
        val fs = PlatformFileSystem()
        val path = resolveGovernancePolicyPath()
        if (fs.exists(path)) {
            return try {
                fs.readText(path)
            } catch (_: Exception) {
                defaultGovernancePolicyJson()
            }
        }
        return defaultGovernancePolicyJson()
    }

    open fun saveGovernancePolicyJson(rawJson: String): String {
        if (isEnterpriseEdition()) {
            return "Error: Governance policy is read-only and managed by IT in enterprise edition."
        }
        return try {
            val fs = PlatformFileSystem()
            val incoming = JsonUtil.parseJsonStringToMap(rawJson)
            val existing = try {
                JsonUtil.parseJsonStringToMap(getGovernancePolicyJson()).toMutableMap<String, Any?>()
            } catch (_: Exception) {
                JsonUtil.parseJsonStringToMap(defaultGovernancePolicyJson()).toMutableMap<String, Any?>()
            }

            // Update user-customizable restricted keywords
            val rawKeywords = incoming["restrictedKeywords"] as? List<*>
            if (rawKeywords != null) {
                val cleanedKeywords = rawKeywords
                    .mapNotNull { it?.toString()?.trim()?.uppercase()?.takeIf { s -> s.isNotEmpty() } }
                    .distinct()
                existing["restrictedKeywords"] = cleanedKeywords
            }

            // Update risk thresholds with safe clamping while keeping core anti-malware patterns untouched
            val existingThresholds = (existing["thresholds"] as? Map<*, *>)
                ?.entries?.associate { (k, v) -> k.toString() to v }
                ?.toMutableMap()
                ?: mutableMapOf()
            val incomingThresholds = incoming["thresholds"] as? Map<*, *>
            val rawAlert = (incomingThresholds?.get("alertThreshold") ?: incoming["alertThreshold"])
                ?.toString()?.toDoubleOrNull()?.coerceIn(0.20, 0.95)
            val rawHitl = (incomingThresholds?.get("hitlApprovalThreshold") ?: incoming["hitlApprovalThreshold"])
                ?.toString()?.toDoubleOrNull()?.coerceIn(0.25, 0.98)
            val rawBlock = (incomingThresholds?.get("blockThreshold") ?: incoming["blockThreshold"])
                ?.toString()?.toDoubleOrNull()?.coerceIn(0.40, 0.99)

            val alertVal = rawAlert ?: (existingThresholds["alertThreshold"]?.toString()?.toDoubleOrNull() ?: 0.75)
            val hitlVal = (rawHitl ?: (existingThresholds["hitlApprovalThreshold"]?.toString()?.toDoubleOrNull() ?: 0.80))
                .coerceAtLeast(alertVal)
            val blockVal = (rawBlock ?: (existingThresholds["blockThreshold"]?.toString()?.toDoubleOrNull() ?: 0.95))
                .coerceAtLeast(hitlVal)

            existingThresholds["alertThreshold"] = alertVal
            existingThresholds["hitlApprovalThreshold"] = hitlVal
            existingThresholds["blockThreshold"] = blockVal
            existing["thresholds"] = existingThresholds
            existing["alertThreshold"] = alertVal

            // Update audit log retention days
            val incomingRetention = incoming["dataRetention"] as? Map<*, *>
            val retentionDays = (incomingRetention?.get("auditLogRetentionDays") ?: incoming["auditLogRetentionDays"])
                ?.toString()?.toIntOrNull()?.coerceIn(7, 3650)
            if (retentionDays != null) {
                val existingRetention = (existing["dataRetention"] as? Map<*, *>)
                    ?.entries?.associate { (k, v) -> k.toString() to v }
                    ?.toMutableMap()
                    ?: mutableMapOf()
                existingRetention["auditLogRetentionDays"] = retentionDays
                existing["dataRetention"] = existingRetention
            }

            val path = resolveGovernancePolicyPath()
            val parentDir = path.replace('\\', '/').substringBeforeLast('/', "")
            if (parentDir.isNotBlank() && !fs.exists(parentDir)) {
                fs.createDirectories(parentDir)
            }
            fs.writeText(path, JsonUtil.toPrettyJsonString(existing), append = false)
            resolveFabric()?.reloadGovernancePolicy()
            recordAuditLog(
                "GOVERNANCE_POLICY_UPDATED",
                "Personal edition governance policy updated (keywords=${(existing["restrictedKeywords"] as? List<*>)?.size ?: 0}, alert=$alertVal, hitl=$hitlVal, block=$blockVal)."
            )
            "OK"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    open fun resetGovernancePolicyToDefaults(): String {
        if (isEnterpriseEdition()) {
            return "Error: Governance policy is read-only and managed by IT in enterprise edition."
        }
        return try {
            val fs = PlatformFileSystem()
            val path = resolveGovernancePolicyPath()
            val parentDir = path.replace('\\', '/').substringBeforeLast('/', "")
            if (parentDir.isNotBlank() && !fs.exists(parentDir)) {
                fs.createDirectories(parentDir)
            }
            fs.writeText(path, defaultGovernancePolicyJson(), append = false)
            resolveFabric()?.reloadGovernancePolicy()
            recordAuditLog("GOVERNANCE_POLICY_RESET", "Reset personal governance policy to safe defaults.")
            "OK"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun defaultGovernancePolicyJson(): String {
        val def = com.reveila.safety.GovernancePolicy.defaultPolicy()
        return JsonUtil.toPrettyJsonString(
            linkedMapOf(
                "policyName" to def.policyName,
                "version" to def.version,
                "restrictedKeywords" to def.restrictedKeywords,
                "alertThreshold" to def.alertThreshold,
                "promptInjectionPatterns" to def.promptInjectionPatterns,
                "prohibitedSystemCommands" to def.prohibitedSystemCommands,
                "sensitiveDataPatterns" to def.sensitiveDataPatterns,
                "thresholds" to linkedMapOf(
                    "baseRiskScore" to def.baseRiskScore,
                    "keywordPenalty" to def.keywordPenalty,
                    "injectionPenalty" to def.injectionPenalty,
                    "commandPenalty" to def.commandPenalty,
                    "alertThreshold" to def.alertThreshold,
                    "hitlApprovalThreshold" to def.hitlApprovalThreshold,
                    "blockThreshold" to def.blockThreshold
                ),
                "dataRetention" to linkedMapOf(
                    "auditLogRetentionDays" to 365,
                    "forensicStorageMoat" to "sovereign-local-data",
                    "immutableLineage" to true
                )
            )
        )
    }
}
