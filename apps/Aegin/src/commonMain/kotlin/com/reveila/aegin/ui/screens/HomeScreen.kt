package com.reveila.aegin.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.TaskItem
import com.reveila.aegin.bridge.VaultStats
import com.reveila.aegin.ui.components.SetupWizard
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginOrbAvatar
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class ChatMessage(
    val id: String,
    val sender: String, // "user" | "agent" | "system"
    val text: String,
    val modeBadge: String = "",
    val timestampLabel: String = "Just now"
)

private data class StarterSpaceCardData(
    val title: String,
    val subtitle: String,
    val badge: String,
    val prompt: String,
    val iconType: AeginIconType,
    val accentColor: Color
)

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onToggleTheme: () -> Unit,
    initialPrompt: String? = null,
    onConsumedInitialPrompt: () -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var prompt by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var showWizard by remember { mutableStateOf(false) }
    var vaultFolder by remember { mutableStateOf("Documents/Reveila") }
    var vaultStats by remember { mutableStateOf(VaultStats(1, 1, "READY")) }
    var isLocalMode by remember { mutableStateOf(true) }
    var cloudProviderName by remember { mutableStateOf("Google Gemini") }
    var isStatusExpanded by remember { mutableStateOf(false) }
    var activeProcessNote by remember { mutableStateOf("Idle • Local embeddings & inference ready") }

    // Non-blocking background task telemetry & status reporting
    val inFlightTasks = remember { mutableStateListOf<TaskItem>() }
    val seenTaskEvents = remember { mutableSetOf<String>() }
    var initializedTaskEvents by remember { mutableStateOf(false) }

    // Muse-inspired lightweight conversation threads ("Main Space", "Vault Research", "Side Chat")
    val threadNames = remember { listOf("Main Space", "Vault Research", "Side Chat") }
    var activeThread by remember { mutableStateOf("Main Space") }

    val threadMessagesMap = remember {
        mutableStateMapOf(
            "Main Space" to listOf(
                ChatMessage(
                    id = "init-main",
                    sender = "agent",
                    text = "Ready. Ask a question, synthesize your Knowledge Vault, or launch an autonomous Space.",
                    modeBadge = "On-Device GGUF"
                )
            ),
            "Vault Research" to listOf(
                ChatMessage(
                    id = "init-vault",
                    sender = "agent",
                    text = "Vault Research thread active. Every query here prioritizes semantic retrieval across your indexed documents.",
                    modeBadge = "Vault RAG"
                )
            ),
            "Side Chat" to listOf(
                ChatMessage(
                    id = "init-side",
                    sender = "agent",
                    text = "Side Chat open. Use this scratchpad for quick brainstorming without cluttering your main stream.",
                    modeBadge = "Scratchpad"
                )
            )
        )
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun syncThreadMessages(thread: String) {
        messages.clear()
        messages.addAll(threadMessagesMap[thread] ?: emptyList())
    }

    fun persistCurrentThreadMessages() {
        threadMessagesMap[activeThread] = messages.toList()
    }

    LaunchedEffect(activeThread) {
        syncThreadMessages(activeThread)
    }

    suspend fun loadActiveMode() {
        try {
            val savedMode = AeginBridge.getStorageItem("selected_llm_mode")
            val savedProvider = AeginBridge.getStorageItem("selected_llm_provider")
            val rawLlm = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
            val (_, wProv, _) = AeginBridge.parseLlmSettingsJson(rawLlm)

            if (wProv.isNotBlank()) {
                if (wProv.equals("On-Device Model", ignoreCase = true) || wProv.contains("Local", ignoreCase = true)) {
                    isLocalMode = true
                } else {
                    isLocalMode = false
                    cloudProviderName = wProv
                }
            } else if (!savedMode.isNullOrBlank()) {
                isLocalMode = savedMode == "local"
                if (!savedProvider.isNullOrBlank() && savedProvider != "On-Device Model") {
                    cloudProviderName = savedProvider
                }
            }
        } catch (_: Exception) {
        }
    }

    fun toggleQuickMode() {
        scope.launch {
            val nextLocal = !isLocalMode
            isLocalMode = nextLocal
            val targetProvider = if (nextLocal) "On-Device Model" else cloudProviderName
            val modeLabel = if (nextLocal) "On-Device GGUF (Offline)" else "Cloud AI ($cloudProviderName)"

            activeProcessNote = "Switched active engine to $modeLabel"

            AeginBridge.setStorageItem("selected_llm_mode", if (nextLocal) "local" else "cloud")
            AeginBridge.setStorageItem("selected_llm_provider", targetProvider)

            try {
                val rawLlm = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
                val (providers, _, gProv) = AeginBridge.parseLlmSettingsJson(rawLlm)
                val payload = AeginBridge.buildLlmSettingsJson(
                    providers = providers,
                    workerProvider = targetProvider,
                    governanceProvider = gProv
                )
                AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", payload))
            } catch (_: Exception) {
            }

            messages.add(
                ChatMessage(
                    id = Clock.System.now().toEpochMilliseconds().toString(),
                    sender = "agent",
                    text = if (nextLocal) {
                        "Switched to On-Device Model. Inference now runs 100% locally via the embedded GGUF engine."
                    } else {
                        "Switched to Cloud AI ($cloudProviderName). Prompts will route through your configured cloud provider."
                    },
                    modeBadge = if (nextLocal) "On-Device GGUF" else cloudProviderName
                )
            )
            persistCurrentThreadMessages()
        }
    }

    suspend fun refreshVaultSummary() {
        try {
            val folderInfo = AeginBridge.getVaultFolder()
            if (folderInfo.folder.isNotBlank()) {
                vaultFolder = folderInfo.folder
            }
            val stats = AeginBridge.getVaultStats()
            vaultStats = stats
        } catch (_: Exception) {
        }
    }

    fun dispatchPrompt(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || isThinking) return

        val currentBadge = if (isLocalMode) "On-Device GGUF" else cloudProviderName
        val userMsg = ChatMessage(
            id = Clock.System.now().toEpochMilliseconds().toString(),
            sender = "user",
            text = trimmed,
            modeBadge = currentBadge
        )
        messages.add(userMsg)
        persistCurrentThreadMessages()
        prompt = ""
        isThinking = true
        activeProcessNote = "Retrieving Vault vectors & running ${if (isLocalMode) "local GGUF" else cloudProviderName} inference..."

        val currentHistory = messages.toList()

        scope.launch {
            delay(50)
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }

            val historyBuilder = StringBuilder()
            val recentMessages = currentHistory.takeLast(9)
            for (msg in recentMessages) {
                if (msg.id.startsWith("init-")) continue
                val roleLabel = if (msg.sender == "user") "User" else "Assistant"
                historyBuilder.append("$roleLabel: ${msg.text}\n")
            }
            historyBuilder.append("Assistant:")

            val contextualPrompt = if (historyBuilder.length > trimmed.length + 25) {
                historyBuilder.toString()
            } else {
                trimmed
            }

            val agentMsgId = (Clock.System.now().toEpochMilliseconds() + 1).toString()
            messages.add(
                ChatMessage(
                    id = agentMsgId,
                    sender = "agent",
                    text = "Synthesizing response...",
                    modeBadge = currentBadge
                )
            )
            persistCurrentThreadMessages()

            val updateBubble: (String) -> Unit = { newText ->
                val idx = messages.indexOfFirst { it.id == agentMsgId }
                if (idx >= 0) {
                    messages[idx] = ChatMessage(
                        id = agentMsgId,
                        sender = "agent",
                        text = newText,
                        modeBadge = currentBadge
                    )
                    persistCurrentThreadMessages()
                }
            }

            try {
                val reply = if (isLocalMode) {
                    val streamedText = StringBuilder()
                    AeginBridge.streamPrompt(
                        prompt = trimmed,
                        sessionId = activeThread,
                        prevSummary = null
                    ).collect { piece ->
                        streamedText.append(piece)
                        updateBubble(streamedText.toString())
                    }
                    if (streamedText.isNotEmpty()) {
                        streamedText.toString()
                    } else {
                        val (ans, _) = AeginBridge.askAgent(trimmed, activeThread, null)
                        ans
                    }
                } else {
                    val cloudRes = AeginBridge.invoke(
                        "OrchestrationService",
                        "processPrompt",
                        listOf(contextualPrompt)
                    )
                    if (cloudRes.contains("Error", ignoreCase = true) ||
                        cloudRes.contains("401", ignoreCase = true) ||
                        cloudRes.contains("API key", ignoreCase = true)
                    ) {
                        "⚠️ Cloud AI ($cloudProviderName) returned an error:\n$cloudRes\n\nTip: Configure your API key in Studio → AI & Vault, or tap the mode pill to switch to On-Device mode."
                    } else {
                        cloudRes
                    }
                }

                updateBubble(reply)
                activeProcessNote = "Completed via $currentBadge • ${vaultStats.chunkCount} vectors active"
            } catch (e: Exception) {
                val fallback = try {
                    if (isLocalMode) {
                        val (ans, _) = AeginBridge.askAgent(trimmed, activeThread, null)
                        ans
                    } else {
                        "⚠️ Failed to reach Cloud AI ($cloudProviderName): ${e.message ?: "Network error"}."
                    }
                } catch (inner: Exception) {
                    "Error processing request: ${inner.message ?: "Engine unavailable"}"
                }
                updateBubble(fallback)
                activeProcessNote = "Ready • Fallback handled"
            } finally {
                isThinking = false
                delay(50)
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val setupDone = AeginBridge.getStorageItem("reveila_setup_completed")
        if (setupDone != "true") {
            try {
                AeginBridge.ensureVaultDirectory("Documents/Reveila")
                val stats = AeginBridge.getVaultStats()
                vaultStats = stats
            } catch (_: Exception) {
            }
        }
        refreshVaultSummary()
        loadActiveMode()

        // Background task telemetry loop (never locks up interactive Assistant chat)
        while (true) {
            try {
                val dashboard = AeginBridge.getTasksDashboard()
                val activeSandboxes = dashboard.tasks.filter {
                    it.status == "RUNNING" || it.status == "SUSPENDED"
                }
                inFlightTasks.clear()
                inFlightTasks.addAll(activeSandboxes)

                if (!initializedTaskEvents) {
                    dashboard.notifications.forEach { seenTaskEvents.add(it.eventId) }
                    initializedTaskEvents = true
                } else {
                    val newEvents = dashboard.notifications
                        .filter { !seenTaskEvents.contains(it.eventId) }
                        .sortedBy { it.timestamp }
                    for (evt in newEvents) {
                        seenTaskEvents.add(evt.eventId)
                        if (evt.status in listOf("COMPLETED", "WAITING_APPROVAL", "FAILED", "KILLED")) {
                            val statusIcon = when (evt.status) {
                                "COMPLETED" -> "✅"
                                "WAITING_APPROVAL" -> "⚠️"
                                "KILLED" -> "⏹"
                                else -> "❌"
                            }
                            val cardText = buildString {
                                appendLine("$statusIcon Background Task ${evt.status}: ${evt.title}")
                                if (evt.summary.isNotBlank()) {
                                    append(evt.summary)
                                }
                            }.trim()
                            messages.add(
                                ChatMessage(
                                    id = evt.eventId,
                                    sender = "agent",
                                    text = cardText,
                                    modeBadge = "Task • ${evt.status}"
                                )
                            )
                            persistCurrentThreadMessages()
                        }
                    }
                }
            } catch (_: Exception) {
            }
            delay(if (inFlightTasks.isNotEmpty()) 1000L else 2800L)
        }
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            val toRun = initialPrompt
            onConsumedInitialPrompt()
            dispatchPrompt(toRun)
        }
    }

    val starterCards = remember(theme) {
        listOf(
            StarterSpaceCardData(
                title = "Vault Synthesis",
                subtitle = "Distill strategy, architecture & key takeaways from indexed docs",
                badge = "RAG • LOCAL",
                prompt = "Summarize the key strategic insights and architectural decisions from my indexed documents.",
                iconType = AeginIconType.VAULT,
                accentColor = Color(0xFFFF6B2C)
            ),
            StarterSpaceCardData(
                title = "Executive Briefing",
                subtitle = "Generate a structured daily digest and priority action plan",
                badge = "BRIEFING",
                prompt = "Create a concise executive briefing with top 3 priorities and action items based on my knowledge vault.",
                iconType = AeginIconType.SPARKLES,
                accentColor = Color(0xFF06B6D4)
            ),
            StarterSpaceCardData(
                title = "Risk & Privacy Audit",
                subtitle = "Check confidential notes & active workflows for exposure",
                badge = "GUARDRAIL",
                prompt = "Audit my indexed documents for sensitive entities, compliance risks, or confidential milestones.",
                iconType = AeginIconType.SHIELD,
                accentColor = Color(0xFF10B981)
            ),
            StarterSpaceCardData(
                title = "Roadmap Studio",
                subtitle = "Brainstorm technical milestones and next-step deliverables",
                badge = "PLANNER",
                prompt = "Help me draft a clear 3-phase technical roadmap and milestone checklist.",
                iconType = AeginIconType.BOLT,
                accentColor = Color(0xFF8B5CF6)
            )
        )
    }

    val isHeroState = messages.size <= 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .imePadding()
    ) {
        // ─────────────────────────────────────────────────────────────────────
        // 1. MUSE-INSPIRED HEADER + LAYER-1 STATUS PILL
        // ─────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Identity + Orb
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable { isStatusExpanded = !isStatusExpanded }
            ) {
                AeginOrbAvatar(
                    size = 38.dp,
                    isLocalMode = isLocalMode,
                    isThinking = isThinking,
                    showStatusDot = true
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Aegin",
                            color = theme.text,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isLocalMode) theme.primary.copy(alpha = 0.16f)
                                    else theme.cyan.copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isLocalMode) "SOVEREIGN" else "HYBRID",
                                color = if (isLocalMode) theme.primary else theme.cyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                    Text(
                        text = if (isThinking) "Thinking..." else "$activeThread • ${vaultStats.chunkCount} vectors",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Controls: Layer-1 Status Pill + Setup Wizard + Theme Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Layer 1 Essentials Status Pill (Tap to expand Layer 2 & 3)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(
                            width = 1.dp,
                            color = if (isStatusExpanded) theme.primary else theme.glassBorder,
                            shape = CircleShape
                        )
                        .clickable { isStatusExpanded = !isStatusExpanded }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLocalMode) theme.primary else theme.cyan)
                    )
                    Text(
                        text = if (isLocalMode) "On-Device" else cloudProviderName.split(" ").last(),
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStatusExpanded) "▴" else "▾",
                        color = theme.subText,
                        fontSize = 10.sp
                    )
                }

                // Quick Setup Wizard Pill
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { showWizard = true },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.BOLT,
                        tint = theme.primary,
                        size = 15.dp
                    )
                }

                // Theme Toggle Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable { onToggleTheme() },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = if (theme.isDark) AeginIconType.SUN else AeginIconType.MOON,
                        tint = if (theme.isDark) theme.warning else theme.accent,
                        size = 16.dp
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 2. THREE-LAYER STATUS INSPECTOR (EXPANDABLE DRAWER)
        // ─────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isStatusExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.card)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Layer 2: Active Process & Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AeginVectorIcon(type = AeginIconType.SPARKLES, tint = theme.primary, size = 14.dp)
                        Text(
                            text = "ACTIVE INTELLIGENCE LAYER",
                            color = theme.subText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = "Switch Mode ⇄",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { toggleQuickMode() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // On-Device Pill Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isLocalMode) theme.primary.copy(alpha = 0.14f)
                                else theme.inputBg
                            )
                            .border(
                                width = 1.dp,
                                color = if (isLocalMode) theme.primary else theme.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { if (!isLocalMode) toggleQuickMode() }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "📱 On-Device GGUF",
                            color = if (isLocalMode) theme.primary else theme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "100% Offline • Zero-Egress",
                            color = theme.subText,
                            fontSize = 10.sp
                        )
                    }

                    // Cloud AI Pill Card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (!isLocalMode) theme.cyan.copy(alpha = 0.14f)
                                else theme.inputBg
                            )
                            .border(
                                width = 1.dp,
                                color = if (!isLocalMode) theme.cyan else theme.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { if (isLocalMode) toggleQuickMode() }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "☁️ $cloudProviderName",
                            color = if (!isLocalMode) theme.cyan else theme.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Cloud API • Guarded",
                            color = theme.subText,
                            fontSize = 10.sp
                        )
                    }
                }

                // Layer 3: Technical Telemetry & Knowledge Vault Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.inputBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vault: $vaultFolder",
                            color = theme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeProcessNote,
                            color = theme.subText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.success.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${vaultStats.documentCount} docs • ${vaultStats.chunkCount} vec",
                                color = theme.success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.surfaceElevated)
                                .clickable {
                                    isStatusExpanded = false
                                    showWizard = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Wizard",
                                color = theme.text,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 3. CONVERSATION THREAD / SIDE-CHAT SWITCHER BAR
        // ─────────────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                threadNames.forEach { threadName ->
                    val isSelected = activeThread == threadName
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (isSelected) theme.surfaceElevated
                                else theme.card.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) theme.primary.copy(alpha = 0.5f) else theme.glassBorder,
                                shape = CircleShape
                            )
                            .clickable { activeThread = threadName }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AeginVectorIcon(
                            type = AeginIconType.THREAD,
                            tint = if (isSelected) theme.primary else theme.subText,
                            size = 12.dp
                        )
                        Text(
                            text = threadName,
                            color = if (isSelected) theme.text else theme.subText,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (!isHeroState) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable {
                            val resetMsg = ChatMessage(
                                id = "init-${Clock.System.now().toEpochMilliseconds()}",
                                sender = "agent",
                                text = "Started a fresh conversation in $activeThread.",
                                modeBadge = if (isLocalMode) "On-Device GGUF" else cloudProviderName
                            )
                            messages.clear()
                            messages.add(resetMsg)
                            persistCurrentThreadMessages()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "New",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 3.5. NON-BLOCKING LIVE BACKGROUND TASK SANDBOX BANNER
        // ─────────────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = inFlightTasks.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                inFlightTasks.forEach { bgTask ->
                    val isRunning = bgTask.status == "RUNNING"
                    val accent = if (isRunning) theme.cyan else theme.warning
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isRunning) {
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
                                Text(
                                    text = "${bgTask.title} • ${bgTask.sandboxId.ifBlank { bgTask.status }}",
                                    color = theme.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = bgTask.currentStep.ifBlank { "Executing in background sandbox..." },
                                    color = theme.subText,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isRunning) theme.warning.copy(alpha = 0.2f)
                                        else theme.success.copy(alpha = 0.2f)
                                    )
                                    .border(1.dp, if (isRunning) theme.warning else theme.success, CircleShape)
                                    .clickable {
                                        scope.launch {
                                            if (isRunning) {
                                                AeginBridge.pauseTask(bgTask.taskId)
                                            } else {
                                                AeginBridge.resumeTask(bgTask.taskId)
                                            }
                                            val refreshed = AeginBridge.getTasksDashboard()
                                            inFlightTasks.clear()
                                            inFlightTasks.addAll(
                                                refreshed.tasks.filter { it.status == "RUNNING" || it.status == "SUSPENDED" }
                                            )
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isRunning) "⏸ Suspend" else "▶ Resume",
                                    color = if (isRunning) theme.warning else theme.success,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(theme.danger.copy(alpha = 0.2f))
                                    .border(1.dp, theme.danger, CircleShape)
                                    .clickable {
                                        scope.launch {
                                            AeginBridge.killTask(bgTask.taskId)
                                            val refreshed = AeginBridge.getTasksDashboard()
                                            inFlightTasks.clear()
                                            inFlightTasks.addAll(
                                                refreshed.tasks.filter { it.status == "RUNNING" || it.status == "SUSPENDED" }
                                            )
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⏹ Kill",
                                    color = theme.danger,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 4. CONVERSATION STREAM + MUSE-INSPIRED HERO EMPTY STATE
        // ─────────────────────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isHeroState) {
                item(key = "muse_hero_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AeginOrbAvatar(
                            size = 64.dp,
                            isLocalMode = isLocalMode,
                            isThinking = isThinking,
                            showStatusDot = false
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "What shall we explore today?",
                            color = theme.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Private document intelligence & autonomous workflows, tailored for you.",
                            color = theme.subText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Compact Vault & Engine Context Pill
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, CircleShape)
                                .clickable { showWizard = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AeginVectorIcon(
                                type = AeginIconType.VAULT,
                                tint = theme.success,
                                size = 14.dp
                            )
                            Text(
                                text = vaultFolder,
                                color = theme.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(theme.success.copy(alpha = 0.16f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${vaultStats.chunkCount} vectors",
                                    color = theme.success,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Configure →",
                                color = theme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2x2 Grid of Starter Space Cards
                item(key = "muse_starter_grid") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUICK SPACES",
                                color = theme.subText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Explore All Spaces →",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToExplore() }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StarterSpaceCard(
                                card = starterCards[0],
                                modifier = Modifier.weight(1f),
                                onClick = { dispatchPrompt(starterCards[0].prompt) }
                            )
                            StarterSpaceCard(
                                card = starterCards[1],
                                modifier = Modifier.weight(1f),
                                onClick = { dispatchPrompt(starterCards[1].prompt) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StarterSpaceCard(
                                card = starterCards[2],
                                modifier = Modifier.weight(1f),
                                onClick = { dispatchPrompt(starterCards[2].prompt) }
                            )
                            StarterSpaceCard(
                                card = starterCards[3],
                                modifier = Modifier.weight(1f),
                                onClick = { dispatchPrompt(starterCards[3].prompt) }
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                val isSystem = msg.sender == "system"

                if (isUser) {
                    // User Message Bubble (Right-aligned, asymmetric Muse pill)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 22.dp,
                                            topEnd = 22.dp,
                                            bottomStart = 22.dp,
                                            bottomEnd = 6.dp
                                        )
                                    )
                                    .background(
                                        if (isLocalMode) theme.primaryGradient else theme.cloudGradient
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    // Assistant / System Message Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        AeginOrbAvatar(
                            size = 28.dp,
                            isLocalMode = isLocalMode,
                            isThinking = false,
                            showStatusDot = false
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 6.dp,
                                        topEnd = 20.dp,
                                        bottomStart = 20.dp,
                                        bottomEnd = 20.dp
                                    )
                                )
                                .background(
                                    if (isSystem) theme.danger.copy(alpha = 0.12f) else theme.card
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSystem) theme.danger.copy(alpha = 0.4f) else theme.glassBorder,
                                    shape = RoundedCornerShape(
                                        topStart = 6.dp,
                                        topEnd = 20.dp,
                                        bottomStart = 20.dp,
                                        bottomEnd = 20.dp
                                    )
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isSystem) "System Alert" else "Aegin",
                                        color = if (isSystem) theme.danger else theme.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    val badgeLabel = msg.modeBadge.ifBlank {
                                        if (isLocalMode) "On-Device GGUF" else cloudProviderName
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isLocalMode) theme.primary.copy(alpha = 0.14f)
                                                else theme.cyan.copy(alpha = 0.14f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            color = if (isLocalMode) theme.primary else theme.cyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = msg.text,
                                color = theme.text,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )

                            if (!msg.id.startsWith("init-") && !isThinking) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .clickable {
                                                dispatchPrompt("Elaborate further with concrete action steps.")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✦ Go Deeper",
                                            color = theme.subText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .clickable { onNavigateToExplore() }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "⚡ Automate in Spaces",
                                            color = theme.cyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isThinking) {
                item(key = "thinking_indicator") {
                    Row(
                        modifier = Modifier
                            .padding(start = 38.dp, top = 2.dp)
                            .clip(CircleShape)
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = if (isLocalMode) theme.primary else theme.cyan
                        )
                        Text(
                            text = if (isLocalMode) "Running local GGUF inference..." else "Querying $cloudProviderName...",
                            color = theme.subText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 5. QUICK SUGGESTION CHIPS + FLOATING PILL COMPOSER
        // ─────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Contextual Quick Suggestion Chips
            val quickChips = remember {
                listOf(
                    "✦ Summarize Vault" to "Summarize my indexed documents and highlight key entities.",
                    "⚡ Action Items" to "Extract the most urgent action items from my notes.",
                    "🔍 Search Roadmap" to "What architectural or roadmap milestones are in my vault?",
                    "🛡️ Privacy Check" to "Scan for any sensitive information in my current workspace."
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickChips.forEach { (chipLabel, chipPrompt) ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.card)
                            .border(1.dp, theme.glassBorder, CircleShape)
                            .clickable(enabled = !isThinking) { dispatchPrompt(chipPrompt) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = chipLabel,
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Floating Pill Composer Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(theme.card)
                    .border(
                        width = 1.dp,
                        color = if (prompt.isNotBlank()) {
                            if (isLocalMode) theme.primary.copy(alpha = 0.6f) else theme.cyan.copy(alpha = 0.6f)
                        } else {
                            theme.glassBorder
                        },
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Mode Toggle Badge inside composer
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLocalMode) theme.primary.copy(alpha = 0.16f)
                            else theme.cyan.copy(alpha = 0.16f)
                        )
                        .clickable { toggleQuickMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLocalMode) "📱" else "☁️",
                        fontSize = 15.sp
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = if (isLocalMode) "Ask Aegin (On-Device)..." else "Ask Aegin ($cloudProviderName)...",
                            color = theme.subText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { dispatchPrompt(prompt) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.text,
                        unfocusedTextColor = theme.text,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                val canSend = prompt.isNotBlank() && !isThinking
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                if (isLocalMode) theme.primaryGradient else theme.cloudGradient
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        theme.surfaceElevated,
                                        theme.surfaceElevated
                                    )
                                )
                            }
                        )
                        .clickable(enabled = canSend) { dispatchPrompt(prompt) },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.SEND,
                        tint = if (canSend) Color.White else theme.subText,
                        size = 18.dp
                    )
                }
            }
        }
    }

    SetupWizard(
        visible = showWizard,
        onComplete = {
            showWizard = false
            scope.launch {
                refreshVaultSummary()
                loadActiveMode()
            }
            messages.add(
                ChatMessage(
                    id = Clock.System.now().toEpochMilliseconds().toString(),
                    sender = "agent",
                    text = "Setup complete. Your Knowledge Vault ($vaultFolder) is indexed and ready.",
                    modeBadge = if (isLocalMode) "On-Device GGUF" else cloudProviderName
                )
            )
            persistCurrentThreadMessages()
        },
        onCancel = {
            showWizard = false
        }
    )
}

@Composable
private fun StarterSpaceCard(
    card: StarterSpaceCardData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(theme.card)
            .border(1.dp, theme.glassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(card.accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                AeginVectorIcon(
                    type = card.iconType,
                    tint = card.accentColor,
                    size = 16.dp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(card.accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = card.badge,
                    color = card.accentColor,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Text(
            text = card.title,
            color = theme.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = card.subtitle,
            color = theme.subText,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
