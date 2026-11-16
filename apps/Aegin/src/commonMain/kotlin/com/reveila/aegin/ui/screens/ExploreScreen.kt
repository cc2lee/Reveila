package com.reveila.aegin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.AuditLogItem
import com.reveila.aegin.bridge.TaskItem
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginOrbAvatar
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class CuratedSpaceTemplate(
    val title: String,
    val subtitle: String,
    val category: String,
    val prompt: String,
    val defaultRecurrence: String,
    val accentColor: Color,
    val iconType: AeginIconType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenSecurityPortal: () -> Unit,
    onLaunchSpaceInChat: (String) -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    val tasks = remember { mutableStateListOf<TaskItem>() }
    val auditLogs = remember { mutableStateListOf<AuditLogItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var busyTaskId by remember { mutableStateOf<String?>(null) }
    var activeFilter by remember { mutableStateOf("Overview") } // "Overview" | "Approvals" | "Automations" | "Audit"

    // Create / Edit Task Dialog State
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var taskTitleInput by remember { mutableStateOf("") }
    var taskPromptInput by remember { mutableStateOf("") }
    var taskRecurrenceInput by remember { mutableStateOf("DAILY") }
    var taskScheduleTimeInput by remember { mutableStateOf("08:00") }
    var taskRequiresApprovalInput by remember { mutableStateOf(false) }

    // Kill Switch Confirmation Dialog
    var showKillAllConfirm by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val curatedSpaces = remember {
        listOf(
            CuratedSpaceTemplate(
                title = "Vault Watchdog",
                subtitle = "Continuously scan indexed documents & surface new strategic entities",
                category = "RAG MONITOR",
                prompt = "Scan my Knowledge Vault for newly added strategy or architecture notes and summarize key changes.",
                defaultRecurrence = "HOURLY",
                accentColor = Color(0xFFFF6B2C),
                iconType = AeginIconType.VAULT
            ),
            CuratedSpaceTemplate(
                title = "Morning Executive Digest",
                subtitle = "Synthesize daily priorities, deadlines & open questions from your vault",
                category = "DAILY BRIEF",
                prompt = "Compile a structured morning briefing with top 3 priorities and risk items from my indexed documents.",
                defaultRecurrence = "DAILY",
                accentColor = Color(0xFF06B6D4),
                iconType = AeginIconType.SPARKLES
            ),
            CuratedSpaceTemplate(
                title = "Privacy & Risk Sentinel",
                subtitle = "Audit confidential notes & prompts for compliance or sensitive exposure",
                category = "GUARDRAIL",
                prompt = "Perform a privacy and compliance audit across my indexed reference documents and flag sensitive items.",
                defaultRecurrence = "DAILY",
                accentColor = Color(0xFF10B981),
                iconType = AeginIconType.SHIELD
            ),
            CuratedSpaceTemplate(
                title = "Weekly Roadmap Pulse",
                subtitle = "Track technical milestones, blockers & architectural alignment",
                category = "STRATEGY",
                prompt = "Synthesize a weekly roadmap status report and highlight upcoming architectural milestones.",
                defaultRecurrence = "WEEKLY",
                accentColor = Color(0xFF8B5CF6),
                iconType = AeginIconType.BOLT
            )
        )
    }

    suspend fun refreshMissionControl(silent: Boolean = false) {
        if (!silent) isLoading = true
        try {
            val dashboard = AeginBridge.getTasksDashboard()
            tasks.clear()
            tasks.addAll(dashboard.tasks)
            auditLogs.clear()
            auditLogs.addAll(dashboard.auditLogs)
        } catch (_: Exception) {
        } finally {
            if (!silent) isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshMissionControl(silent = false)
        while (true) {
            val hasInFlight = tasks.any { it.status == "RUNNING" || it.status == "SUSPENDED" }
            delay(if (hasInFlight) 1000L else 3000L)
            refreshMissionControl(silent = true)
        }
    }

    fun openCreateDialog(
        prefillTitle: String = "",
        prefillPrompt: String = "",
        prefillRecurrence: String = "DAILY"
    ) {
        editingTaskId = null
        taskTitleInput = prefillTitle
        taskPromptInput = prefillPrompt
        taskRecurrenceInput = prefillRecurrence
        taskScheduleTimeInput = "08:00"
        taskRequiresApprovalInput = false
        showTaskDialog = true
    }

    fun openEditDialog(item: TaskItem) {
        editingTaskId = item.taskId
        taskTitleInput = item.title
        taskPromptInput = item.prompt
        taskRecurrenceInput = item.frequency.ifBlank { "DAILY" }.uppercase()
        taskScheduleTimeInput = item.scheduleTime.ifBlank { "08:00" }
        taskRequiresApprovalInput = item.requiresApproval
        showTaskDialog = true
    }

    fun handleSaveTask() {
        val title = taskTitleInput.trim().ifBlank { "Autonomous Task" }
        val prompt = taskPromptInput.trim()
        if (prompt.isBlank()) return

        showTaskDialog = false
        scope.launch {
            try {
                if (editingTaskId == null) {
                    AeginBridge.createTask(
                        title = title,
                        prompt = prompt,
                        frequency = taskRecurrenceInput.lowercase(),
                        scheduleTime = taskScheduleTimeInput.ifBlank { "08:00" },
                        requiresApproval = taskRequiresApprovalInput
                    )
                    statusBanner = "Space workflow '$title' created."
                } else {
                    AeginBridge.updateTask(
                        taskId = editingTaskId!!,
                        title = title,
                        prompt = prompt,
                        frequency = taskRecurrenceInput.lowercase(),
                        scheduleTime = taskScheduleTimeInput.ifBlank { "08:00" },
                        enabled = true,
                        requiresApproval = taskRequiresApprovalInput
                    )
                    statusBanner = "Space workflow '$title' updated."
                }
                refreshMissionControl(silent = true)
            } catch (e: Exception) {
                statusBanner = "Error saving task: ${e.message}"
            }
        }
    }

    fun handleTaskAction(taskId: String, method: String, bannerMsg: String? = null) {
        scope.launch {
            busyTaskId = taskId
            try {
                when (method) {
                    "runTaskNow" -> AeginBridge.runTaskNow(taskId)
                    "approveTask" -> AeginBridge.approveTask(taskId)
                    "rejectTask" -> AeginBridge.rejectTask(taskId)
                    "pauseTask" -> AeginBridge.pauseTask(taskId)
                    "resumeTask" -> AeginBridge.resumeTask(taskId)
                    "killTask" -> AeginBridge.killTask(taskId)
                    "deleteTask" -> AeginBridge.deleteTask(taskId)
                    else -> AeginBridge.invoke("AutonomousAgent", method, listOf(taskId))
                }
                if (bannerMsg != null) statusBanner = bannerMsg
                refreshMissionControl(silent = true)
            } catch (e: Exception) {
                statusBanner = "Action failed: ${e.message}"
            } finally {
                busyTaskId = null
            }
        }
    }

    fun handleKillAll() {
        showKillAllConfirm = false
        scope.launch {
            isLoading = true
            try {
                AeginBridge.setGlobalKillSwitch(true)
                statusBanner = "Sovereign Kill Switch engaged: All active tasks halted."
                refreshMissionControl(silent = true)
            } catch (e: Exception) {
                statusBanner = "Kill switch error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    PlatformBackHandler {
        when {
            showTaskDialog -> showTaskDialog = false
            showKillAllConfirm -> showKillAllConfirm = false
            else -> onBack()
        }
    }

    val activeTasks = tasks.filter {
        it.status == "RUNNING" ||
            it.status == "SUSPENDED" ||
            it.status == "QUEUED" ||
            it.status == "SCHEDULED" ||
            it.status == "ACTIVE"
    }
    val waitingApprovalTasks = tasks.filter { it.status == "WAITING_APPROVAL" }
    val finishedTasks = tasks.filter {
        it.status != "RUNNING" &&
            it.status != "SUSPENDED" &&
            it.status != "QUEUED" &&
            it.status != "SCHEDULED" &&
            it.status != "ACTIVE" &&
            it.status != "WAITING_APPROVAL"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
    ) {
        // ─────────────────────────────────────────────────────────────────────
        // 1. HEADER BAR
        // ─────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AeginOrbAvatar(
                    size = 36.dp,
                    isLocalMode = false,
                    isThinking = activeTasks.any { it.status == "RUNNING" },
                    showStatusDot = true
                )
                Column {
                    Text(
                        text = "Spaces & Tasks",
                        color = theme.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${activeTasks.size} active • ${waitingApprovalTasks.size} awaiting approval",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Security Portal Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onOpenSecurityPortal() },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.SHIELD,
                        tint = theme.cyan,
                        size = 16.dp
                    )
                }

                // + New Space Task Button
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.primaryGradient)
                        .clickable { openCreateDialog() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.PLUS,
                        tint = Color.White,
                        size = 13.dp
                    )
                    Text(
                        text = "New Task",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 2. SEGMENTED FILTER PILLS
        // ─────────────────────────────────────────────────────────────────────
        val filters = listOf(
            "Overview" to "All Spaces",
            "Approvals" to "Approvals (${waitingApprovalTasks.size})",
            "Automations" to "Tasks (${tasks.size})",
            "Audit" to "Flight Log (${auditLogs.size})"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (key, label) ->
                val selected = activeFilter == key
                val highlightColor = when (key) {
                    "Approvals" -> if (waitingApprovalTasks.isNotEmpty()) theme.warning else theme.cyan
                    else -> theme.cyan
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) highlightColor.copy(alpha = 0.18f)
                            else theme.card
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) highlightColor else theme.glassBorder,
                            shape = CircleShape
                        )
                        .clickable { activeFilter = key }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) highlightColor else theme.subText,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 3. MAIN SCROLLABLE CONTENT
        // ─────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SOVEREIGN GUARDRAIL & KILL SWITCH CARD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.card)
                    .border(
                        width = 1.dp,
                        color = if (activeTasks.isNotEmpty()) theme.danger.copy(alpha = 0.55f) else theme.glassBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (activeTasks.isNotEmpty()) theme.danger.copy(alpha = 0.16f)
                                else theme.success.copy(alpha = 0.14f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AeginVectorIcon(
                            type = AeginIconType.SHIELD,
                            tint = if (activeTasks.isNotEmpty()) theme.danger else theme.success,
                            size = 20.dp
                        )
                    }
                    Column {
                        Text(
                            text = "Sovereign Execution Guard",
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (activeTasks.isNotEmpty()) {
                                "${activeTasks.size} background workflow(s) active. Tap Kill Switch to halt immediately."
                            } else {
                                "All autonomous spaces nominal. HITL escalation gate is active."
                            },
                            color = theme.subText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (activeTasks.isNotEmpty() || waitingApprovalTasks.isNotEmpty()) {
                                theme.danger
                            } else {
                                theme.surfaceElevated
                            }
                        )
                        .clickable { showKillAllConfirm = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Kill Switch",
                        color = if (activeTasks.isNotEmpty() || waitingApprovalTasks.isNotEmpty()) Color.White else theme.subText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            // STATUS FEEDBACK BANNER
            if (!statusBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.cyan.copy(alpha = 0.14f))
                        .border(1.dp, theme.cyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusBanner ?: "",
                        color = theme.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        color = theme.subText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { statusBanner = null }
                            .padding(start = 8.dp)
                    )
                }
            }

            // 1. CURATED MUSE-STYLE SPACES CAROUSEL (Shown in Overview)
            if (activeFilter == "Overview") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PINNED SPACES",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.9.sp
                        )
                        Text(
                            text = "Interactive & Scheduled",
                            color = theme.cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        curatedSpaces.forEach { space ->
                            Column(
                                modifier = Modifier
                                    .width(245.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(theme.card)
                                    .border(1.dp, theme.glassBorder, RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(space.accentColor.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AeginVectorIcon(
                                            type = space.iconType,
                                            tint = space.accentColor,
                                            size = 18.dp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(space.accentColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = space.category,
                                            color = space.accentColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Text(
                                    text = space.title,
                                    color = theme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = space.subtitle,
                                    color = theme.subText,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(space.accentColor)
                                            .clickable { onLaunchSpaceInChat(space.prompt) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Open in Chat",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .border(1.dp, theme.glassBorder, CircleShape)
                                            .clickable {
                                                openCreateDialog(
                                                    prefillTitle = space.title,
                                                    prefillPrompt = space.prompt,
                                                    prefillRecurrence = space.defaultRecurrence
                                                )
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+ Schedule",
                                            color = theme.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. HUMAN-IN-THE-LOOP (HITL) APPROVALS SECTION
            if ((activeFilter == "Overview" || activeFilter == "Approvals") &&
                (waitingApprovalTasks.isNotEmpty() || activeFilter == "Approvals")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "HUMAN-IN-THE-LOOP APPROVALS (${waitingApprovalTasks.size})",
                        color = theme.warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.9.sp
                    )

                    if (waitingApprovalTasks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No Pending Approvals",
                                color = theme.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "When an autonomous task requests high-sensitivity access, it will pause here for your explicit authorization.",
                                color = theme.subText,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    } else {
                        waitingApprovalTasks.forEach { task ->
                            TaskControlCard(
                                task = task,
                                isBusy = busyTaskId == task.taskId,
                                onRunNow = { handleTaskAction(task.taskId, "runTaskNow", "Executing '${task.title}'...") },
                                onApprove = { handleTaskAction(task.taskId, "approveTask", "Approved & executed '${task.title}'.") },
                                onReject = { handleTaskAction(task.taskId, "rejectTask", "Rejected '${task.title}'.") },
                                onPauseOrResume = { handleTaskAction(task.taskId, "pauseTask", "Paused '${task.title}'.") },
                                onKill = { handleTaskAction(task.taskId, "killTask", "Killed '${task.title}'.") },
                                onEdit = { openEditDialog(task) },
                                onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                            )
                        }
                    }
                }
            }

            // 3. ACTIVE & SCHEDULED AUTOMATIONS SECTION
            if (activeFilter == "Overview" || activeFilter == "Automations") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE & SCHEDULED TASKS (${activeTasks.size})",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.9.sp
                        )
                        Text(
                            text = "+ Create Custom",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { openCreateDialog() }
                        )
                    }

                    if (activeTasks.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No active background tasks scheduled.",
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap '+ Schedule' on any Pinned Space above or '+ New Task' to automate recurring document intelligence.",
                                color = theme.subText,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    } else {
                        activeTasks.forEach { task ->
                            TaskControlCard(
                                task = task,
                                isBusy = busyTaskId == task.taskId,
                                onRunNow = { handleTaskAction(task.taskId, "runTaskNow", "Launched '${task.title}' in isolated sandbox.") },
                                onApprove = { handleTaskAction(task.taskId, "approveTask") },
                                onReject = { handleTaskAction(task.taskId, "rejectTask") },
                                onPauseOrResume = {
                                    if (task.status == "SUSPENDED") {
                                        handleTaskAction(task.taskId, "resumeTask", "Resumed '${task.title}' sandbox.")
                                    } else {
                                        handleTaskAction(
                                            task.taskId,
                                            "pauseTask",
                                            if (task.status == "RUNNING") "Suspended '${task.title}' sandbox." else "Paused '${task.title}'."
                                        )
                                    }
                                },
                                onKill = { handleTaskAction(task.taskId, "killTask", "Terminated '${task.title}' sandbox.") },
                                onEdit = { openEditDialog(task) },
                                onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                            )
                        }
                    }
                }

                // SAVED / COMPLETED TASKS
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RECENT & SAVED WORKFLOWS (${finishedTasks.size})",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.9.sp
                    )

                    if (finishedTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Completed, paused, or saved tasks will appear here.",
                                color = theme.subText,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        finishedTasks.forEach { task ->
                            TaskControlCard(
                                task = task,
                                isBusy = busyTaskId == task.taskId,
                                onRunNow = { handleTaskAction(task.taskId, "runTaskNow", "Re-ran '${task.title}'.") },
                                onApprove = { handleTaskAction(task.taskId, "approveTask") },
                                onReject = { handleTaskAction(task.taskId, "rejectTask") },
                                onPauseOrResume = { handleTaskAction(task.taskId, "resumeTask", "Resumed '${task.title}'.") },
                                onKill = { handleTaskAction(task.taskId, "killTask", "Killed '${task.title}'.") },
                                onEdit = { openEditDialog(task) },
                                onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                            )
                        }
                    }
                }
            }

            // 4. FLIGHT RECORDER & LIVE AUDIT STREAM
            if (activeFilter == "Overview" || activeFilter == "Audit") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(theme.cyan)
                            )
                            Text(
                                text = "FLIGHT RECORDER & AUDIT STREAM",
                                color = theme.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = "Refresh ↻",
                            color = theme.cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scope.launch { refreshMissionControl(silent = false) }
                            }
                        )
                    }

                    if (auditLogs.isEmpty()) {
                        Text(
                            text = "No audit events recorded yet.",
                            color = theme.subText,
                            fontSize = 12.sp
                        )
                    } else {
                        val displayLogs = if (activeFilter == "Audit") auditLogs else auditLogs.take(6)
                        displayLogs.forEach { log ->
                            AuditLogRow(log)
                        }
                    }
                }
            }
        }
    }

    // CREATE / EDIT AUTONOMOUS TASK MODAL
    if (showTaskDialog) {
        val recurrenceOptions = listOf("ONCE", "HOURLY", "DAILY", "WEEKLY")
        Dialog(onDismissRequest = { showTaskDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingTaskId == null) "New Space Automation" else "Edit Space Automation",
                        color = theme.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(theme.inputBg)
                            .clickable { showTaskDialog = false },
                        contentAlignment = Alignment.Center
                    ) {
                        AeginVectorIcon(type = AeginIconType.CLOSE, tint = theme.subText, size = 14.dp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SPACE / TASK TITLE", color = theme.subText, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    OutlinedTextField(
                        value = taskTitleInput,
                        onValueChange = { taskTitleInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("e.g., Daily Vault Executive Summary", color = theme.subText, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.border
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("AGENT INSTRUCTIONS / PROMPT", color = theme.subText, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    OutlinedTextField(
                        value = taskPromptInput,
                        onValueChange = { taskPromptInput = it },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                "Describe what Aegin should analyze or synthesize from your Knowledge Vault...",
                                color = theme.subText,
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.border
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("SCHEDULE RECURRENCE", color = theme.subText, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recurrenceOptions.forEach { opt ->
                            val selected = taskRecurrenceInput.equals(opt, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(if (selected) theme.primary else theme.inputBg)
                                    .border(1.dp, if (selected) theme.primary else theme.border, CircleShape)
                                    .clickable { taskRecurrenceInput = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    color = if (selected) Color.White else theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = taskScheduleTimeInput,
                        onValueChange = { taskScheduleTimeInput = it },
                        modifier = Modifier.weight(0.45f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Time (HH:MM)", color = theme.subText, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.border
                        )
                    )

                    Row(
                        modifier = Modifier
                            .weight(0.55f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(theme.inputBg)
                            .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Require HITL Approval",
                            color = theme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = taskRequiresApprovalInput,
                            onCheckedChange = { taskRequiresApprovalInput = it }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .border(1.dp, theme.border, CircleShape)
                            .clickable { showTaskDialog = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(CircleShape)
                            .background(theme.primaryGradient)
                            .clickable { handleSaveTask() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (editingTaskId == null) "Launch Space Task" else "Save Changes",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // KILL ALL CONFIRMATION DIALOG
    if (showKillAllConfirm) {
        AlertDialog(
            onDismissRequest = { showKillAllConfirm = false },
            title = { Text("Engage Sovereign Kill Switch?") },
            text = {
                Text("This will immediately terminate all running, queued, scheduled, and pending-approval autonomous tasks.")
            },
            confirmButton = {
                TextButton(onClick = { handleKillAll() }) {
                    Text("KILL ALL TASKS", color = theme.danger, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TaskControlCard(
    task: TaskItem,
    isBusy: Boolean,
    onRunNow: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onPauseOrResume: () -> Unit,
    onKill: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAeginColors.current

    val statusColor = when (task.status) {
        "RUNNING" -> theme.cyan
        "SUSPENDED" -> theme.warning
        "SCHEDULED", "QUEUED", "ACTIVE" -> theme.primary
        "WAITING_APPROVAL" -> theme.warning
        "COMPLETED" -> theme.success
        "KILLED", "FAILED", "REJECTED" -> theme.danger
        else -> theme.subText
    }

    val isWaitingApproval = task.status == "WAITING_APPROVAL"
    val isRunningInSandbox = task.status == "RUNNING"
    val isSuspendedInSandbox = task.status == "SUSPENDED"
    val isActiveGroup = task.status == "RUNNING" ||
        task.status == "SUSPENDED" ||
        task.status == "QUEUED" ||
        task.status == "SCHEDULED" ||
        task.status == "ACTIVE"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.card)
            .border(
                width = 1.dp,
                color = when {
                    isRunningInSandbox -> theme.cyan.copy(alpha = 0.7f)
                    isSuspendedInSandbox || isWaitingApproval -> theme.warning.copy(alpha = 0.7f)
                    else -> theme.glassBorder
                },
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row: Title + Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                color = theme.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val isCloudRoute = task.routeTarget.equals("CLOUD", ignoreCase = true)
                val routeColor = if (isCloudRoute) theme.cyan else theme.success
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(routeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isCloudRoute) "☁️ ${task.taskCategory}" else "📱 ${task.taskCategory}",
                        color = routeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.inputBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = task.frequency.ifBlank { "DAILY" }.uppercase(),
                        color = theme.subText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = task.status,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        // Prompt summary
        Text(
            text = task.prompt,
            color = theme.subText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        // Live Sandbox Telemetry Strip (Shown while RUNNING or SUSPENDED)
        if (isRunningInSandbox || isSuspendedInSandbox) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isRunningInSandbox) theme.cyan.copy(alpha = 0.12f)
                        else theme.warning.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isRunningInSandbox) theme.cyan.copy(alpha = 0.4f) else theme.warning.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isRunningInSandbox) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = theme.cyan
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(theme.warning)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (task.sandboxId.isNotBlank()) {
                        Text(
                            text = "SANDBOX • ${task.sandboxId}",
                            color = if (isRunningInSandbox) theme.cyan else theme.warning,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = task.currentStep.ifBlank {
                            if (isRunningInSandbox) "Executing in isolated sandbox..." else "Sandbox suspended by user."
                        },
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // HITL Approval Banner & Action Pills
        if (isWaitingApproval) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.warning.copy(alpha = 0.12f))
                    .border(1.dp, theme.warning.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = task.approvalReason.ifBlank { "Waiting for user authorization before executing task." },
                    color = theme.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(theme.success)
                            .clickable(enabled = !isBusy) { onApprove() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Approve & Run", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(theme.danger.copy(alpha = 0.18f))
                            .border(1.dp, theme.danger, CircleShape)
                            .clickable(enabled = !isBusy) { onReject() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Decline", color = theme.danger, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Last Result / Timestamp
        if (task.lastResult.isNotBlank() || task.lastRunAt > 0L) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.inputBg)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (task.lastRunAt > 0L) {
                    Text(
                        text = "LAST RUN • ${formatEpochMs(task.lastRunAt)}",
                        color = theme.subText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                if (task.lastResult.isNotBlank()) {
                    Text(
                        text = task.lastResult,
                        color = theme.text,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Action Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunningInSandbox || isSuspendedInSandbox) {
                // Dedicated In-Progress Sandbox Controls: Suspend/Resume + Kill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isRunningInSandbox) theme.warning.copy(alpha = 0.18f)
                            else theme.success.copy(alpha = 0.18f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isRunningInSandbox) theme.warning else theme.success,
                            shape = CircleShape
                        )
                        .clickable(enabled = !isBusy) { onPauseOrResume() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isRunningInSandbox) "⏸ Suspend" else "▶ Resume Sandbox",
                        color = if (isRunningInSandbox) theme.warning else theme.success,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.danger.copy(alpha = 0.18f))
                        .border(1.dp, theme.danger, CircleShape)
                        .clickable(enabled = !isBusy) { onKill() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "⏹ Kill",
                        color = theme.danger,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (task.sandboxId.isNotBlank()) {
                    Text(
                        text = task.sandboxId,
                        color = theme.subText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.primaryGradient)
                        .clickable(enabled = !isBusy) { onRunNow() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    if (isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Starting...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    } else {
                        Text("▶ Run Now", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.inputBg)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onEdit() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("Edit", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.inputBg)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onPauseOrResume() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isActiveGroup) "Pause" else "Resume",
                        color = if (isActiveGroup) theme.warning else theme.success,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isActiveGroup) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.danger.copy(alpha = 0.16f))
                            .border(1.dp, theme.danger.copy(alpha = 0.6f), CircleShape)
                            .clickable { onKill() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Kill", color = theme.danger, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.inputBg)
                            .clickable { onDelete() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text("Delete", color = theme.danger, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogRow(log: AuditLogItem) {
    val theme = LocalAeginColors.current
    val actUpper = log.action.uppercase()
    val dotColor = when {
        actUpper.contains("KILL") || actUpper.contains("BREACH") || actUpper.contains("FAIL") || actUpper.contains("DENIED") -> theme.danger
        actUpper.contains("APPROVAL") || actUpper.contains("HITL") || actUpper.contains("ESCALAT") || actUpper.contains("PAUSE") -> theme.warning
        actUpper.contains("COMPLETE") || actUpper.contains("CREATED") || actUpper.contains("APPROVED") || actUpper.contains("RESUMED") -> theme.success
        else -> theme.cyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.inputBg)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column {
                Text(
                    text = log.action,
                    color = dotColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (log.details.isNotBlank()) {
                    Text(
                        text = log.details,
                        color = theme.text,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (log.timestamp > 0L) {
            Text(
                text = formatEpochMs(log.timestamp),
                color = theme.subText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

private fun formatEpochMs(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return try {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
        val mo = dt.monthNumber.toString().padStart(2, '0')
        val d = dt.dayOfMonth.toString().padStart(2, '0')
        val h = dt.hour.toString().padStart(2, '0')
        val m = dt.minute.toString().padStart(2, '0')
        val s = dt.second.toString().padStart(2, '0')
        "$mo/$d $h:$m:$s"
    } catch (_: Exception) {
        ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPortalModal(
    onDismiss: () -> Unit
) {
    val theme = LocalAeginColors.current
    val auditLogs = remember { mutableStateListOf<AuditLogItem>() }

    LaunchedEffect(Unit) {
        try {
            val dashboard = AeginBridge.getTasksDashboard()
            auditLogs.clear()
            auditLogs.addAll(dashboard.auditLogs.take(8))
        } catch (_: Exception) {
        }
    }

    PlatformBackHandler {
        onDismiss()
    }

    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(theme.cyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AeginVectorIcon(type = AeginIconType.SHIELD, tint = theme.cyan, size = 20.dp)
                    }
                    Column {
                        Text(
                            text = "Security & Guardrail Portal",
                            color = theme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Sovereign Sandbox & Zero-Egress Telemetry",
                            color = theme.subText,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Close", color = theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Guardrail Pillars Grid
            val pillars = listOf(
                Triple("Zero-Egress GGUF Sandbox", "ACTIVE • MMAP", "Local llama.cpp inference runs entirely in isolated device memory without network egress."),
                Triple("HITL Escalation Gate", "ENFORCED", "Sensitive autonomous actions pause automatically for explicit Human-in-the-Loop approval."),
                Triple("Unified Callback Protocol", "v1.0.0 VERIFIED", "All distributed node & sandbox invocations enforce strict version handshake on /invoke."),
                Triple("Scoped Knowledge Vault", "ISOLATED", "Document embeddings remain stored locally inside your designated Vault folder.")
            )

            pillars.forEach { (title, badge, desc) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.success.copy(alpha = 0.16f))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badge,
                                color = theme.success,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = desc,
                        color = theme.subText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // Recent Security Audit Stream
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RECENT SECURITY & SANDBOX EVENTS",
                    color = theme.subText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                if (auditLogs.isEmpty()) {
                    Text(
                        text = "No security violations or escalations recorded.",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                } else {
                    auditLogs.forEach { log ->
                        AuditLogRow(log)
                    }
                }
            }
        }
    }
}
