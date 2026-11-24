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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.AeginDisplayMapper
import com.reveila.aegin.bridge.AuditLogItem
import com.reveila.aegin.bridge.TaskItem
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import com.reveila.aegin.ui.theme.pillMinWidthAtLeastHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private data class TaskPresetTemplate(
    val title: String,
    val category: String,
    val prompt: String,
    val defaultRecurrence: String,
    val accentColor: Color,
    val defaultSourceUrl: String = ""
)

private enum class TasksTabSection(val label: String) {
    TASKS("Tasks"),
    APPROVALS("Approvals"),
    ACTIVITY("Activity")
}

/**
 * Dedicated screen for managing automated background Tasks, human approvals,
 * and execution activity logs — completely separated from chat Spaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onOpenSecurityPortal: () -> Unit,
    initialDraftPrompt: String? = null,
    onConsumedInitialDraft: () -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    val tasks = remember { mutableStateListOf<TaskItem>() }
    val auditLogs = remember { mutableStateListOf<AuditLogItem>() }
    var isLoading by remember { mutableStateOf(false) }
    var busyTaskId by remember { mutableStateOf<String?>(null) }
    var activeSection by remember { mutableStateOf(TasksTabSection.TASKS) }
    var showTaskHistory by remember { mutableStateOf(false) }

    // Create / Edit Task Dialog State
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var taskTitleInput by remember { mutableStateOf("") }
    var taskPromptInput by remember { mutableStateOf("") }
    var taskSourceUrlInput by remember { mutableStateOf("") }
    var taskRecurrenceInput by remember { mutableStateOf("DAILY") }
    var taskScheduleTimeInput by remember { mutableStateOf("08:00") }
    var taskExecutionTargetInput by remember { mutableStateOf("auto") }
    var taskRequiresApprovalInput by remember { mutableStateOf(false) }

    // Stop-All Confirmation Dialog
    var showKillAllConfirm by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val taskPresets = remember {
        listOf(
            TaskPresetTemplate(
                title = "News Brief",
                category = "NEWS BRIEF",
                prompt = "Retrieve the latest news headlines using the virtual browser and summarize the top stories with clickable source links.",
                defaultRecurrence = "DAILY",
                accentColor = Color(0xFF38BDF8),
                defaultSourceUrl = "https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en"
            ),
            TaskPresetTemplate(
                title = "Knowledge Folder Watch",
                category = "RAG MONITOR",
                prompt = "Scan my Personal Knowledge Folder for newly added or updated files and summarize key changes.",
                defaultRecurrence = "HOURLY",
                accentColor = Color(0xFFFF6B2C)
            ),
            TaskPresetTemplate(
                title = "Morning Digest",
                category = "DAILY BRIEF",
                prompt = "Compile a structured morning briefing with top 3 priorities and risk items from the files in my Personal Knowledge Folder.",
                defaultRecurrence = "DAILY",
                accentColor = Color(0xFF06B6D4)
            ),
            TaskPresetTemplate(
                title = "Privacy Audit",
                category = "GUARDRAIL",
                prompt = "Perform a privacy and compliance audit across my indexed reference documents and flag sensitive items.",
                defaultRecurrence = "DAILY",
                accentColor = Color(0xFF10B981)
            ),
            TaskPresetTemplate(
                title = "Roadmap Pulse",
                category = "STRATEGY",
                prompt = "Synthesize a weekly roadmap status report and highlight upcoming architectural milestones.",
                defaultRecurrence = "WEEKLY",
                accentColor = Color(0xFF8B5CF6)
            )
        )
    }

    suspend fun refreshDashboard(silent: Boolean = false) {
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
        refreshDashboard(silent = false)
        while (true) {
            val hasInFlight = tasks.any { it.status == "RUNNING" || it.status == "SUSPENDED" }
            delay(if (hasInFlight) 1000L else 3000L)
            refreshDashboard(silent = true)
        }
    }

    fun openCreateDialog(
        prefillTitle: String = "",
        prefillPrompt: String = "",
        prefillRecurrence: String = "DAILY",
        prefillTarget: String = "auto",
        prefillSourceUrl: String = ""
    ) {
        editingTaskId = null
        taskTitleInput = prefillTitle
        taskPromptInput = prefillPrompt
        taskSourceUrlInput = prefillSourceUrl
        taskRecurrenceInput = prefillRecurrence
        taskScheduleTimeInput = "08:00"
        taskExecutionTargetInput = prefillTarget
        taskRequiresApprovalInput = false
        showTaskDialog = true
    }

    fun openEditDialog(item: TaskItem) {
        editingTaskId = item.taskId
        taskTitleInput = item.title
        taskPromptInput = item.prompt
        taskSourceUrlInput = item.sourceUrl
        taskRecurrenceInput = item.frequency.ifBlank { "DAILY" }.uppercase()
        taskScheduleTimeInput = item.scheduleTime.ifBlank { "08:00" }
        taskExecutionTargetInput = item.executionTarget.ifBlank { "auto" }
        taskRequiresApprovalInput = item.requiresApproval
        showTaskDialog = true
    }

    LaunchedEffect(initialDraftPrompt) {
        if (!initialDraftPrompt.isNullOrBlank()) {
            onConsumedInitialDraft()
            openCreateDialog(
                prefillTitle = "Follow-Up Task",
                prefillPrompt = initialDraftPrompt
            )
        }
    }

    fun handleSaveTask(runImmediately: Boolean = false) {
        val title = taskTitleInput.trim().ifBlank { "Automated Task" }
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
                        requiresApproval = if (runImmediately) false else taskRequiresApprovalInput,
                        executionTarget = taskExecutionTargetInput,
                        sourceUrl = taskSourceUrlInput.trim()
                    )
                    refreshDashboard(silent = true)
                    if (runImmediately) {
                        val createdTask = tasks.firstOrNull { it.title == title }
                        if (createdTask != null) {
                            AeginBridge.runTaskNow(createdTask.taskId)
                            refreshDashboard(silent = true)
                        }
                        statusBanner = "Task '$title' created and started."
                    } else {
                        statusBanner = "Task '$title' created."
                    }
                } else {
                    AeginBridge.updateTask(
                        taskId = editingTaskId!!,
                        title = title,
                        prompt = prompt,
                        frequency = taskRecurrenceInput.lowercase(),
                        scheduleTime = taskScheduleTimeInput.ifBlank { "08:00" },
                        enabled = true,
                        requiresApproval = taskRequiresApprovalInput,
                        executionTarget = taskExecutionTargetInput,
                        sourceUrl = taskSourceUrlInput.trim()
                    )
                    statusBanner = "Task '$title' updated."
                    refreshDashboard(silent = true)
                }
                activeSection = TasksTabSection.TASKS
            } catch (e: Exception) {
                statusBanner = "Could not save task: ${e.message ?: "Please try again."}"
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
                refreshDashboard(silent = true)
            } catch (e: Exception) {
                statusBanner = "Could not complete action: ${e.message ?: "Please try again."}"
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
                statusBanner = "All active background tasks have been stopped."
                refreshDashboard(silent = true)
            } catch (e: Exception) {
                statusBanner = "Could not stop tasks: ${e.message ?: "Please try again."}"
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

    val headerSubtitle = buildList {
        if (activeTasks.isNotEmpty()) add("${activeTasks.size} active")
        if (waitingApprovalTasks.isNotEmpty()) add("${waitingApprovalTasks.size} waiting approval")
    }.joinToString(" • ").ifEmpty { "Scheduled automations & approvals" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom))
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.BACK,
                        tint = theme.text,
                        size = 15.dp
                    )
                }
                Column {
                    Text(
                        text = "Tasks",
                        color = theme.text,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = headerSubtitle,
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

        // 3-Tab Segmented Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(CircleShape)
                .background(theme.card)
                .border(1.dp, theme.glassBorder, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TasksTabSection.entries.forEach { section ->
                val selected = activeSection == section
                val badgeCount = when (section) {
                    TasksTabSection.TASKS -> activeTasks.size
                    TasksTabSection.APPROVALS -> waitingApprovalTasks.size
                    TasksTabSection.ACTIVITY -> 0
                }
                val accentColor = if (section == TasksTabSection.APPROVALS && waitingApprovalTasks.isNotEmpty()) {
                    theme.warning
                } else {
                    theme.cyan
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    listOf(accentColor.copy(alpha = 0.24f), accentColor.copy(alpha = 0.12f))
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                            }
                        )
                        .clickable { activeSection = section }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.label,
                        color = if (selected) accentColor else theme.subText,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badgeCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = if (selected) 0.28f else 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$badgeCount",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
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

            when (activeSection) {
                TasksTabSection.TASKS -> {
                    // Safety & Stop-All Bar
                    if (activeTasks.isNotEmpty() || waitingApprovalTasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(theme.card)
                                .border(1.dp, theme.danger.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(theme.danger.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AeginVectorIcon(
                                        type = AeginIconType.SHIELD,
                                        tint = theme.danger,
                                        size = 18.dp
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${activeTasks.size} active task(s)",
                                        color = theme.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Tap Stop All to pause or cancel running tasks anytime.",
                                        color = theme.subText,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(theme.danger)
                                    .clickable { showKillAllConfirm = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Stop All",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AeginVectorIcon(type = AeginIconType.SHIELD, tint = theme.success, size = 15.dp)
                                Text(
                                    text = "All quiet • Safety checks enabled",
                                    color = theme.subText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Safety Portal",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenSecurityPortal() }
                            )
                        }
                    }

                    if (waitingApprovalTasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.warning.copy(alpha = 0.12f))
                                .border(1.dp, theme.warning.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable { activeSection = TasksTabSection.APPROVALS }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${waitingApprovalTasks.size} task(s) waiting for your approval",
                                color = theme.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Review →",
                                color = theme.warning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Quick Automation Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Quick Task Presets",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            taskPresets.forEach { preset ->
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(theme.card)
                                        .border(1.dp, preset.accentColor.copy(alpha = 0.4f), CircleShape)
                                        .clickable {
                                            openCreateDialog(
                                                prefillTitle = preset.title,
                                                prefillPrompt = preset.prompt,
                                                prefillRecurrence = preset.defaultRecurrence,
                                                prefillSourceUrl = preset.defaultSourceUrl
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(preset.accentColor)
                                    )
                                    Text(
                                        text = "+ ${preset.title}",
                                        color = theme.text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Active & Scheduled Tasks
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Active & Scheduled",
                                    color = theme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(theme.cardAlt)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${activeTasks.size}",
                                        color = theme.subText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(theme.primary.copy(alpha = 0.14f))
                                    .border(1.dp, theme.primary.copy(alpha = 0.4f), CircleShape)
                                    .clickable { openCreateDialog() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+ New Task",
                                    color = theme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                    text = "No active tasks scheduled",
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap '+ New Task' or choose a preset above to automate recurring summaries, checks, or briefings.",
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
                                    onRunNow = { handleTaskAction(task.taskId, "runTaskNow", "Started '${task.title}'.") },
                                    onApprove = { handleTaskAction(task.taskId, "approveTask") },
                                    onReject = { handleTaskAction(task.taskId, "rejectTask") },
                                    onPauseOrResume = {
                                        if (task.status == "SUSPENDED") {
                                            handleTaskAction(task.taskId, "resumeTask", "Resumed '${task.title}'.")
                                        } else {
                                            handleTaskAction(task.taskId, "pauseTask", "Paused '${task.title}'.")
                                        }
                                    },
                                    onKill = { handleTaskAction(task.taskId, "killTask", "Stopped '${task.title}'.") },
                                    onEdit = { openEditDialog(task) },
                                    onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                                )
                            }
                        }
                    }

                    // Completed & Paused History (Collapsible when active tasks are present)
                    if (finishedTasks.isNotEmpty() || activeTasks.isEmpty()) {
                        val isHistoryExpanded = showTaskHistory || activeTasks.isEmpty()
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = activeTasks.isNotEmpty()) {
                                        showTaskHistory = !showTaskHistory
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Completed & Paused (${finishedTasks.size})",
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (activeTasks.isNotEmpty()) {
                                    Text(
                                        text = if (isHistoryExpanded) "Hide ▴" else "Show ▾",
                                        color = theme.subText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isHistoryExpanded) {
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
                                            onKill = { handleTaskAction(task.taskId, "killTask", "Stopped '${task.title}'.") },
                                            onEdit = { openEditDialog(task) },
                                            onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                TasksTabSection.APPROVALS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Waiting for Approval (${waitingApprovalTasks.size})",
                            color = theme.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        if (waitingApprovalTasks.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(theme.card)
                                    .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AeginVectorIcon(
                                    type = AeginIconType.SHIELD,
                                    tint = theme.success,
                                    size = 26.dp
                                )
                                Text(
                                    text = "All Caught Up",
                                    color = theme.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "When an automated task asks for permission before running a sensitive step, it will appear here for you to approve or decline.",
                                    color = theme.subText,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            waitingApprovalTasks.forEach { task ->
                                TaskControlCard(
                                    task = task,
                                    isBusy = busyTaskId == task.taskId,
                                    onRunNow = { handleTaskAction(task.taskId, "runTaskNow", "Started '${task.title}'.") },
                                    onApprove = { handleTaskAction(task.taskId, "approveTask", "Approved and started '${task.title}'.") },
                                    onReject = { handleTaskAction(task.taskId, "rejectTask", "Declined '${task.title}'.") },
                                    onPauseOrResume = { handleTaskAction(task.taskId, "pauseTask", "Paused '${task.title}'.") },
                                    onKill = { handleTaskAction(task.taskId, "killTask", "Stopped '${task.title}'.") },
                                    onEdit = { openEditDialog(task) },
                                    onDelete = { handleTaskAction(task.taskId, "deleteTask", "Deleted '${task.title}'.") }
                                )
                            }
                        }
                    }
                }

                TasksTabSection.ACTIVITY -> {
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
                                    text = "Recent Activity",
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Refresh ↻",
                                color = theme.cyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    scope.launch { refreshDashboard(silent = false) }
                                }
                            )
                        }

                        if (auditLogs.isEmpty()) {
                            Text(
                                text = "No activity recorded yet.",
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
    }

    // Create / Edit Automated Task Modal
    if (showTaskDialog) {
        val recurrenceOptions = listOf("ONCE", "EVERY 15M", "HOURLY", "DAILY", "WEEKLY")
        val targetOptions = listOf("auto", "local", "cloud")

        Dialog(onDismissRequest = { showTaskDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(22.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingTaskId == null) "New Automated Task" else "Edit Automated Task",
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
                    Text("Task Name", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = taskTitleInput,
                        onValueChange = { taskTitleInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("e.g., Daily Document Summary", color = theme.subText, fontSize = 13.sp) },
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
                    Text("What should Aegin do?", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = taskPromptInput,
                        onValueChange = { taskPromptInput = it },
                        modifier = Modifier.fillMaxWidth().height(104.dp),
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                "Describe what Aegin should summarize, check, or organize...",
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
                    Text("Source / Search URL (Optional)", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = taskSourceUrlInput,
                        onValueChange = { taskSourceUrlInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                "Leave blank for default, or e.g. https://html.duckduckgo.com/html/?q={query}",
                                color = theme.subText,
                                fontSize = 12.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.cyan,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    val urlQuickPresets = listOf(
                        "Default Auto" to "",
                        "DuckDuckGo" to "https://html.duckduckgo.com/html/?q={query}",
                        "Google News" to "https://news.google.com/rss/search?q={query}&hl=en-US&gl=US&ceid=US:en",
                        "Bing News" to "https://www.bing.com/news/search?q={query}&format=rss",
                        "Hacker News" to "https://news.ycombinator.com"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        urlQuickPresets.forEach { (label, presetUrl) ->
                            val isSelected = taskSourceUrlInput.trim() == presetUrl
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) theme.cyan.copy(alpha = 0.2f) else theme.inputBg)
                                    .border(1.dp, if (isSelected) theme.cyan else theme.border, CircleShape)
                                    .clickable { taskSourceUrlInput = presetUrl }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) theme.cyan else theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where Should This Run?", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        targetOptions.forEach { targetKey ->
                            val selected = taskExecutionTargetInput.equals(targetKey, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) theme.cyan.copy(alpha = 0.2f) else theme.inputBg)
                                    .border(1.dp, if (selected) theme.cyan else theme.border, CircleShape)
                                    .clickable { taskExecutionTargetInput = targetKey }
                                    .pillMinWidthAtLeastHeight()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = AeginDisplayMapper.executionTarget(targetKey),
                                    color = if (selected) theme.cyan else theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How Often", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recurrenceOptions.forEach { opt ->
                            val selected = taskRecurrenceInput.equals(opt, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) theme.primary else theme.inputBg)
                                    .border(1.dp, if (selected) theme.primary else theme.border, CircleShape)
                                    .clickable { taskRecurrenceInput = opt }
                                    .pillMinWidthAtLeastHeight()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = AeginDisplayMapper.taskFrequency(opt),
                                    color = if (selected) Color.White else theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                        label = { Text("Time (e.g. 08:00)", color = theme.subText, fontSize = 11.sp) },
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
                            text = "Ask Before Running",
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.primaryGradient)
                            .clickable { handleSaveTask(runImmediately = false) }
                            .pillMinWidthAtLeastHeight()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (editingTaskId == null) "Save Task" else "Save Changes",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (editingTaskId == null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.cyan.copy(alpha = 0.16f))
                                .border(1.dp, theme.cyan, CircleShape)
                                .clickable { handleSaveTask(runImmediately = true) }
                                .pillMinWidthAtLeastHeight()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⚡ Save & Run",
                                color = theme.cyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Stop All Confirmation Dialog
    if (showKillAllConfirm) {
        AlertDialog(
            onDismissRequest = { showKillAllConfirm = false },
            title = { Text("Stop All Background Tasks?") },
            text = {
                Text("This will immediately stop all running, scheduled, and pending-approval tasks.")
            },
            confirmButton = {
                TextButton(onClick = { handleKillAll() }) {
                    Text("Stop All Tasks", color = theme.danger, fontWeight = FontWeight.Black)
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

    val frequencyLabel = AeginDisplayMapper.taskFrequency(task.frequency)
    val routeLabel = AeginDisplayMapper.routeTarget(task.routeTarget)
    val categoryLabel = AeginDisplayMapper.taskCategory(task.taskCategory)
    val scheduleText = if (task.scheduleTime.isNotBlank()) "$frequencyLabel at ${task.scheduleTime}" else frequencyLabel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.card)
            .border(
                width = 1.dp,
                color = when {
                    isRunningInSandbox -> theme.cyan.copy(alpha = 0.7f)
                    isSuspendedInSandbox || isWaitingApproval -> theme.warning.copy(alpha = 0.7f)
                    else -> theme.glassBorder
                },
                shape = RoundedCornerShape(18.dp)
            )
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = AeginDisplayMapper.taskStatus(task.status),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "$scheduleText • $routeLabel • $categoryLabel",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = task.prompt,
            color = theme.subText,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (task.sourceUrl.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.cyan.copy(alpha = 0.10f))
                    .border(1.dp, theme.cyan.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🌐 Source:",
                    color = theme.cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = task.sourceUrl,
                    color = theme.text,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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
                Text(
                    text = AeginDisplayMapper.taskStep(task.currentStep, isSuspended = isSuspendedInSandbox),
                    color = theme.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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
                    text = AeginDisplayMapper.sanitizeTechnicalText(task.approvalReason)
                        .ifBlank { "Waiting for your approval before running this task." },
                    color = theme.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.success)
                            .clickable(enabled = !isBusy) { onApprove() }
                            .pillMinWidthAtLeastHeight()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Approve & Run", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.danger.copy(alpha = 0.18f))
                            .border(1.dp, theme.danger, CircleShape)
                            .clickable(enabled = !isBusy) { onReject() }
                            .pillMinWidthAtLeastHeight()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Decline", color = theme.danger, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

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
                        text = "Last run • ${formatEpochMs(task.lastRunAt)}",
                        color = theme.subText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (task.lastResult.isNotBlank()) {
                    Text(
                        text = task.lastResult,
                        color = theme.text,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunningInSandbox || isSuspendedInSandbox) {
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
                        text = if (isRunningInSandbox) "⏸ Pause" else "▶ Resume",
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
                        text = "⏹ Stop",
                        color = theme.danger,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
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
                        Text("Stop", color = theme.danger, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
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

    val friendlyTitle = AeginDisplayMapper.auditAction(log.action)
    val friendlyDetails = AeginDisplayMapper.auditDetails(log.details)

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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = friendlyTitle,
                    color = dotColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (friendlyDetails.isNotBlank()) {
                    Text(
                        text = friendlyDetails,
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
        "$mo/$d $h:$m"
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
                            text = "Privacy & Safety Portal",
                            color = theme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "On-device protection & activity overview",
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

            val pillars = listOf(
                Triple("Private On-Device AI", "Active", "Local AI runs directly on your device without sending your private files over the internet."),
                Triple("Approval Safeguard", "Enabled", "Sensitive automated actions pause and wait for your explicit approval before running."),
                Triple("Verified System Bridge", "Verified", "All internal app actions and background workflows run through verified checks."),
                Triple("Personal Knowledge Folder", "Isolated", "File search indexes stay stored locally inside your chosen folder.")
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
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
                    text = "Recent Safety Activity",
                    color = theme.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (auditLogs.isEmpty()) {
                    Text(
                        text = "No safety alerts or approvals recorded.",
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
