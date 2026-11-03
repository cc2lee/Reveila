package com.reveila.aegin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.AuditLogItem
import com.reveila.aegin.bridge.TaskItem
import com.reveila.aegin.bridge.TasksDashboardState
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onBack: () -> Unit,
    onOpenSecurityPortal: () -> Unit
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    var dashboardState by remember { mutableStateOf(TasksDashboardState()) }
    var busyTaskId by remember { mutableStateOf<String?>(null) }
    var isEditingTask by remember { mutableStateOf(false) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var editTaskId by remember { mutableStateOf("") }
    var editTitle by remember { mutableStateOf("") }
    var editPrompt by remember { mutableStateOf("") }
    var editFrequency by remember { mutableStateOf("daily") }
    var editScheduleTime by remember { mutableStateOf("08:00") }
    var editEnabled by remember { mutableStateOf(true) }
    var editRequiresApproval by remember { mutableStateOf(false) }

    suspend fun refreshDashboard() {
        dashboardState = AeginBridge.getTasksDashboard()
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
        while (true) {
            delay(3000)
            refreshDashboard()
        }
    }

    val activeTasks = dashboardState.tasks.filter {
        val st = it.status.uppercase()
        st == "RUNNING" || st == "WAITING_APPROVAL" || st == "ACTIVE"
    }
    val inactiveTasks = dashboardState.tasks.filter {
        val st = it.status.uppercase()
        st != "RUNNING" && st != "WAITING_APPROVAL" && st != "ACTIVE"
    }

    fun openNewTaskModal() {
        isCreatingNew = true
        editTaskId = ""
        editTitle = ""
        editPrompt = ""
        editFrequency = "daily"
        editScheduleTime = "08:00"
        editEnabled = true
        editRequiresApproval = false
        isEditingTask = true
    }

    fun openEditTaskModal(task: TaskItem) {
        isCreatingNew = false
        editTaskId = task.taskId
        editTitle = task.title
        editPrompt = task.prompt
        editFrequency = task.frequency
        editScheduleTime = task.scheduleTime
        editEnabled = task.enabled
        editRequiresApproval = task.requiresApproval
        isEditingTask = true
    }

    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Tasks",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.primary)
                                .clickable { openNewTaskModal() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "+ NEW TASK",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                .clickable { onBack() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("← HOME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.headerBg,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Control Center Summary & Sovereign Kill Switch Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(
                        1.dp,
                        if (dashboardState.killSwitchEngaged) theme.danger else theme.border,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = if (dashboardState.killSwitchEngaged) "KILL SWITCH ENGAGED" else "Task Control Center",
                        color = if (dashboardState.killSwitchEngaged) theme.danger else theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${activeTasks.size} Active • ${inactiveTasks.size} Saved • \${REVEILA_HOME}/tasks",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (dashboardState.killSwitchEngaged) theme.success else theme.danger)
                        .clickable {
                            scope.launch {
                                AeginBridge.setGlobalKillSwitch(!dashboardState.killSwitchEngaged)
                                refreshDashboard()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (dashboardState.killSwitchEngaged) "RESUME ALL" else "KILL ALL",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            if (dashboardState.error.isNotBlank()) {
                Text(
                    text = "Engine error: ${dashboardState.error}",
                    color = theme.danger,
                    fontSize = 12.sp
                )
            }

            // 2. Active & Running Tasks Section (Top)
            Text(
                text = "ACTIVE & RUNNING (${activeTasks.size + 1})",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            // Root Chat Session Card (Always shown as the interactive foreground session)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.success.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "INTERACTIVE SESSION",
                                color = theme.success,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        val msgCount = dashboardState.activeSessions.sumOf { it.messageCount }
                        if (msgCount > 0) {
                            Text(
                                text = "$msgCount msgs",
                                color = theme.subText,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Root Agent Chat Session",
                        color = theme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ask questions over your Inference Documents or issue a prompt like \"Create a daily repeating task to show me the headline news at 8:00 AM\".",
                        color = theme.subText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary)
                        .clickable { onBack() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "OPEN CHAT",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            if (activeTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "No active repeating tasks running. Tap '+ NEW TASK' or ask the chat agent to schedule one.",
                        color = theme.subText,
                        fontSize = 13.sp
                    )
                }
            } else {
                activeTasks.forEach { task ->
                    TaskControlCard(
                        task = task,
                        isBusy = busyTaskId == task.taskId,
                        onRunNow = {
                            scope.launch {
                                busyTaskId = task.taskId
                                AeginBridge.runTaskNow(task.taskId)
                                refreshDashboard()
                                busyTaskId = null
                            }
                        },
                        onApprove = {
                            scope.launch {
                                busyTaskId = task.taskId
                                AeginBridge.approveTask(task.taskId)
                                refreshDashboard()
                                busyTaskId = null
                            }
                        },
                        onReject = {
                            scope.launch {
                                AeginBridge.rejectTask(task.taskId)
                                refreshDashboard()
                            }
                        },
                        onEdit = { openEditTaskModal(task) },
                        onPauseOrResume = {
                            scope.launch {
                                AeginBridge.pauseTask(task.taskId)
                                refreshDashboard()
                            }
                        },
                        onKill = {
                            scope.launch {
                                AeginBridge.killTask(task.taskId)
                                refreshDashboard()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                AeginBridge.deleteTask(task.taskId)
                                refreshDashboard()
                            }
                        }
                    )
                }
            }

            // 3. Inactive / Saved Tasks Section (Following)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "INACTIVE & SAVED TASKS (${inactiveTasks.size})",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            if (inactiveTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "No paused or stopped tasks.",
                        color = theme.subText,
                        fontSize = 13.sp
                    )
                }
            } else {
                inactiveTasks.forEach { task ->
                    TaskControlCard(
                        task = task,
                        isBusy = busyTaskId == task.taskId,
                        onRunNow = {
                            scope.launch {
                                busyTaskId = task.taskId
                                AeginBridge.runTaskNow(task.taskId)
                                refreshDashboard()
                                busyTaskId = null
                            }
                        },
                        onApprove = {},
                        onReject = {},
                        onEdit = { openEditTaskModal(task) },
                        onPauseOrResume = {
                            scope.launch {
                                AeginBridge.resumeTask(task.taskId)
                                refreshDashboard()
                            }
                        },
                        onKill = {
                            scope.launch {
                                AeginBridge.killTask(task.taskId)
                                refreshDashboard()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                AeginBridge.deleteTask(task.taskId)
                                refreshDashboard()
                            }
                        }
                    )
                }
            }

            // 4. Activity & Audit Log Feed (DataService AuditLog Repository)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RECENT ACTIVITY & AUDIT LOG (${dashboardState.auditLogs.size})",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dashboardState.auditLogs.isEmpty()) {
                    Text(
                        text = "No audit events recorded yet.",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                } else {
                    dashboardState.auditLogs.take(12).forEach { log ->
                        AuditLogRow(log)
                    }
                }
            }
        }
    }

    if (isEditingTask) {
        Dialog(onDismissRequest = { isEditingTask = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isCreatingNew) "Create Repeating Task" else "Edit Task: $editTaskId",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Task Title") },
                    placeholder = { Text("e.g. Daily Headline News") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.text,
                        unfocusedTextColor = theme.text,
                        focusedContainerColor = theme.inputBg,
                        unfocusedContainerColor = theme.inputBg,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                OutlinedTextField(
                    value = editPrompt,
                    onValueChange = { editPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("Agent Prompt / Instruction") },
                    placeholder = { Text("e.g. Show me the headline news summary.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.text,
                        unfocusedTextColor = theme.text,
                        focusedContainerColor = theme.inputBg,
                        unfocusedContainerColor = theme.inputBg,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                Text(
                    text = "Frequency",
                    color = theme.subText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("daily", "hourly", "weekly", "every 15m").forEach { freq ->
                        val selected = editFrequency.equals(freq, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) theme.primary.copy(alpha = 0.2f) else theme.inputBg)
                                .border(
                                    1.dp,
                                    if (selected) theme.primary else theme.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { editFrequency = freq }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = freq.uppercase(),
                                color = if (selected) theme.primary else theme.subText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editScheduleTime,
                    onValueChange = { editScheduleTime = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Schedule Time (24h HH:MM)") },
                    placeholder = { Text("08:00") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.text,
                        unfocusedTextColor = theme.text,
                        focusedContainerColor = theme.inputBg,
                        unfocusedContainerColor = theme.inputBg,
                        focusedBorderColor = theme.primary,
                        unfocusedBorderColor = theme.border
                    )
                )

                if (isCreatingNew) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Require user approval before execution",
                            color = theme.text,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = editRequiresApproval,
                            onCheckedChange = { editRequiresApproval = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = theme.warning
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Task Enabled (Active)",
                            color = theme.text,
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = editEnabled,
                            onCheckedChange = { editEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = theme.success
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                            .clickable { isEditingTask = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.primary)
                            .clickable {
                                if (editPrompt.isNotBlank()) {
                                    scope.launch {
                                        if (isCreatingNew) {
                                            AeginBridge.createTask(
                                                title = editTitle.ifBlank { editPrompt.take(30) },
                                                prompt = editPrompt.trim(),
                                                frequency = editFrequency,
                                                scheduleTime = editScheduleTime,
                                                requiresApproval = editRequiresApproval
                                            )
                                        } else {
                                            AeginBridge.updateTask(
                                                taskId = editTaskId,
                                                title = editTitle,
                                                prompt = editPrompt.trim(),
                                                frequency = editFrequency,
                                                scheduleTime = editScheduleTime,
                                                enabled = editEnabled
                                            )
                                        }
                                        isEditingTask = false
                                        refreshDashboard()
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save Task", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskControlCard(
    task: TaskItem,
    isBusy: Boolean,
    onRunNow: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEdit: () -> Unit,
    onPauseOrResume: () -> Unit,
    onKill: () -> Unit,
    onDelete: () -> Unit
) {
    val theme = LocalAeginColors.current
    val st = task.status.uppercase()
    val isActiveGroup = st == "RUNNING" || st == "WAITING_APPROVAL" || st == "ACTIVE"

    val (badgeColor, badgeText) = when (st) {
        "RUNNING" -> theme.cyan to "RUNNING"
        "WAITING_APPROVAL" -> theme.warning to "WAITING APPROVAL"
        "ACTIVE" -> theme.success to "ACTIVE"
        "KILLED" -> theme.danger to "KILLED"
        else -> theme.subText to "PAUSED"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.card)
            .border(
                1.dp,
                when (st) {
                    "WAITING_APPROVAL" -> theme.warning
                    "RUNNING" -> theme.cyan
                    "KILLED" -> theme.danger.copy(alpha = 0.5f)
                    else -> theme.border
                },
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header row: status badge + schedule badge + taskId
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.inputBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${task.frequency.uppercase()} @ ${task.scheduleTime}",
                        color = theme.subText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "${task.taskId}.json",
                color = theme.subText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Title & Prompt
        Text(
            text = task.title.ifBlank { task.taskId },
            color = theme.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = task.prompt,
            color = theme.subText,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        // Approval Banner if waiting for approval
        if (st == "WAITING_APPROVAL") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.warning.copy(alpha = 0.12f))
                    .border(1.dp, theme.warning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = task.approvalReason.ifBlank { "Waiting for user approval before executing task." },
                    color = theme.warning,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.success)
                            .clickable(enabled = !isBusy) { onApprove() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Approve & Run", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.danger)
                            .clickable(enabled = !isBusy) { onReject() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Reject", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Last Result / Timestamp
        if (task.lastResult.isNotBlank() || task.lastRunAt > 0L) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.inputBg)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (task.lastRunAt > 0L) {
                    Text(
                        text = "Last Run: ${formatEpochMs(task.lastRunAt)}",
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
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Control Center Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.primary)
                    .clickable(enabled = !isBusy) { onRunNow() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (isBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Running...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Text("▶ Run Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(6.dp))
                    .clickable { onEdit() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Edit", color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(6.dp))
                    .clickable { onPauseOrResume() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isActiveGroup) "Pause" else "Resume",
                    color = if (isActiveGroup) theme.warning else theme.success,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            if (isActiveGroup) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.danger.copy(alpha = 0.18f))
                        .border(1.dp, theme.danger, RoundedCornerShape(6.dp))
                        .clickable { onKill() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Kill", color = theme.danger, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, theme.danger.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Delete", color = theme.danger, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
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
            .clip(RoundedCornerShape(8.dp))
            .background(theme.inputBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Security",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("← HOME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.headerBg,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Security Portal",
                    color = theme.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Inspect authorization prompts and multi-tenant sandboxing checks for your local data operations.",
                    color = theme.subText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Return to Home",
                    color = theme.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(12.dp)
                )
            }
        }
    }
}
