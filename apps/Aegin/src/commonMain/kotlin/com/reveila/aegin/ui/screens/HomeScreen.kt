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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.reveila.aegin.ui.PlatformBackHandler
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
    activeSpaceId: String = "main-space",
    activeSpaceName: String = "Main Space",
    onSelectSpace: (String, String) -> Unit = { _, _ -> },
    initialPrompt: String? = null,
    onConsumedInitialPrompt: () -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var prompt by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var vaultFolder by remember { mutableStateOf("Documents/Reveila") }
    var vaultStats by remember { mutableStateOf(VaultStats(1, 1, "READY")) }
    var activeSelection by remember { mutableStateOf("NONE") } // "NONE" | "AUTO" | "LOCAL" | "CLOUD"
    var isLocalReady by remember { mutableStateOf(false) }
    var isCloudReady by remember { mutableStateOf(false) }
    var isAutoReady by remember { mutableStateOf(false) }
    var cloudProviderName by remember { mutableStateOf("") }
    var isStatusExpanded by remember { mutableStateOf(false) }
    var activeProcessNote by remember { mutableStateOf("Idle • Checking configured AI models...") }

    val isAutoMode = activeSelection == "AUTO"
    val isLocalMode = activeSelection == "LOCAL"
    val isCloudMode = activeSelection == "CLOUD"
    val hasNoActiveModel = activeSelection == "NONE"

    // Non-blocking background task telemetry & status reporting
    val inFlightTasks = remember { mutableStateListOf<TaskItem>() }
    val seenTaskEvents = remember { mutableSetOf<String>() }
    var initializedTaskEvents by remember { mutableStateOf(false) }

    // Active persisted conversation Space & session key
    val normalizedSpaceId = remember(activeSpaceId) { AeginBridge.normalizeSpaceId(activeSpaceId) }
    val activeThread = remember(normalizedSpaceId) { "space-$normalizedSpaceId" }

    val threadMessagesMap = remember {
        mutableStateMapOf(
            "main-space" to listOf(
                ChatMessage(
                    id = "init-main",
                    sender = "agent",
                    text = "Please configure and select an AI mode to begin. Tap \"No AI Model\" at the top-left or open Studio -> Configuration.",
                    modeBadge = ""
                )
            )
        )
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun syncThreadMessages(spaceKey: String) {
        messages.clear()
        messages.addAll(threadMessagesMap[spaceKey] ?: emptyList())
    }

    fun buildMainGreetingMessage(spaceLabel: String = activeSpaceName): ChatMessage {
        return if (activeSelection == "NONE") {
            ChatMessage(
                id = "init-$normalizedSpaceId",
                sender = "agent",
                text = "Please configure and select an AI mode to begin. Tap \"No AI Model\" at the top-left or open Studio -> Configuration.",
                modeBadge = ""
            )
        } else {
            val activeBadge = when (activeSelection) {
                "AUTO" -> "Auto"
                "LOCAL" -> "Local"
                else -> cloudProviderName
            }
            val intro = if (normalizedSpaceId == "main-space") {
                "How can I help you?"
            } else {
                "Space: $spaceLabel — How can I help you?"
            }
            ChatMessage(
                id = "init-$normalizedSpaceId",
                sender = "agent",
                text = intro,
                modeBadge = activeBadge
            )
        }
    }

    fun formatPersistedDisplayText(role: String, rawContent: String): String {
        val trimmed = rawContent.trim()
        if (!role.equals("ASSISTANT", ignoreCase = true) || !trimmed.startsWith("{")) {
            return rawContent
        }
        return try {
            val (parsed, _) = AeginBridge.parseAgentReplyText(trimmed)
            parsed.ifBlank { rawContent }
        } catch (_: Exception) {
            rawContent
        }
    }

    fun refreshMainGreetingCard() {
        val updated = buildMainGreetingMessage()
        val currentList = threadMessagesMap[normalizedSpaceId] ?: emptyList()
        if (currentList.size <= 1 && (currentList.isEmpty() || currentList[0].id.startsWith("init-"))) {
            threadMessagesMap[normalizedSpaceId] = listOf(updated)
            syncThreadMessages(normalizedSpaceId)
        }
    }

    fun persistCurrentThreadMessages() {
        threadMessagesMap[normalizedSpaceId] = messages.toList()
    }

    suspend fun loadActiveMode() {
        try {
            val savedProvider = AeginBridge.getStorageItem("selected_llm_provider")
            val rawLlm = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
            val (providers, wProv, _) = AeginBridge.parseLlmSettingsJson(rawLlm)
            val aProv = AeginBridge.extractAssistantLlm(rawLlm)

            val localConfig = providers.find {
                it.name == "On-Device Model" || it.type.equals("Local", ignoreCase = true)
            }
            val localCheck = AeginBridge.checkLocalModelAvailability(localConfig?.model.orEmpty())
            val localBackendTested = try {
                AeginBridge.invoke("LlmProviderFactory", "isProviderTested", listOf("On-Device Model"))
                    .trim().equals("true", ignoreCase = true)
            } catch (_: Exception) {
                localConfig?.tested == true
            }
            val localAvailable = localCheck.existsLocally || (localConfig?.tested == true && localBackendTested)

            val externalProviders = providers.filter {
                it.name != "On-Device Model" &&
                    !it.type.equals("Local", ignoreCase = true) &&
                    it.name != "Disable" &&
                    it.name != "Custom"
            }

            val configuredExternals = mutableListOf<Pair<com.reveila.aegin.bridge.LlmProviderConfig, Boolean>>()
            for (p in externalProviders) {
                val isConf = try {
                    AeginBridge.invoke("LlmProviderFactory", "isProviderConfigured", listOf(p.name))
                        .trim().equals("true", ignoreCase = true)
                } catch (_: Exception) {
                    (p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")) ||
                        (p.type.equals("Self-Hosted", ignoreCase = true) && p.endpoint.isNotBlank())
                }
                if (isConf) {
                    val isTested = try {
                        AeginBridge.invoke("LlmProviderFactory", "isProviderTested", listOf(p.name))
                            .trim().equals("true", ignoreCase = true)
                    } catch (_: Exception) {
                        p.tested
                    }
                    configuredExternals.add(p to (isTested || p.tested))
                }
            }

            fun findConfiguredExternal(candidate: String?): String? {
                if (candidate.isNullOrBlank()) return null
                return configuredExternals.firstOrNull {
                    it.first.name.equals(candidate, ignoreCase = true)
                }?.first?.name
            }

            isLocalReady = localAvailable
            isCloudReady = configuredExternals.isNotEmpty()
            isAutoReady = localAvailable || configuredExternals.isNotEmpty()

            cloudProviderName = findConfiguredExternal(aProv)
                ?: findConfiguredExternal(wProv)
                ?: findConfiguredExternal(savedProvider)
                ?: configuredExternals.sortedByDescending { it.second }.firstOrNull()?.first?.name
                ?: ""

            val desiredTarget = aProv.takeIf { it.isNotBlank() && !it.equals("Disable", ignoreCase = true) }
                ?: wProv.takeIf { it.isNotBlank() && !it.equals("Disable", ignoreCase = true) }
                ?: ""

            val matchedExternal = findConfiguredExternal(desiredTarget)
            activeSelection = when {
                !isAutoReady -> "NONE"
                desiredTarget.equals("Auto", ignoreCase = true) -> "AUTO"
                desiredTarget.equals("On-Device Model", ignoreCase = true) ||
                    desiredTarget.contains("Local", ignoreCase = true) -> if (localAvailable) "LOCAL" else "NONE"
                matchedExternal != null -> {
                    cloudProviderName = matchedExternal
                    "CLOUD"
                }
                else -> "NONE"
            }

            activeProcessNote = when (activeSelection) {
                "AUTO" -> "Auto • Local-first smart router ready"
                "LOCAL" -> "Local • Embedded GGUF ready"
                "CLOUD" -> "External • $cloudProviderName ready"
                else -> "No AI model configured — open Studio Configuration"
            }
            refreshMainGreetingCard()
        } catch (_: Exception) {
            isLocalReady = false
            isCloudReady = false
            isAutoReady = false
            cloudProviderName = ""
            activeSelection = "NONE"
            refreshMainGreetingCard()
        }
    }

    fun selectActiveMode(targetMode: String) {
        scope.launch {
            val targetProvider: String
            val modeKey: String
            val modeLabel: String
            val modeBadgeLabel: String
            when {
                targetMode.equals("Auto", ignoreCase = true) -> {
                    if (!isAutoReady) {
                        isStatusExpanded = false
                        onNavigateToSettings()
                        return@launch
                    }
                    activeSelection = "AUTO"
                    targetProvider = "Auto"
                    modeKey = "auto"
                    modeLabel = "Auto (Local-First Smart Router)"
                    modeBadgeLabel = "Auto (Local-First)"
                }
                targetMode.equals("On-Device Model", ignoreCase = true) -> {
                    if (!isLocalReady) {
                        isStatusExpanded = false
                        onNavigateToSettings()
                        return@launch
                    }
                    activeSelection = "LOCAL"
                    targetProvider = "On-Device Model"
                    modeKey = "local"
                    modeLabel = "On-Device GGUF (Offline)"
                    modeBadgeLabel = "Local"
                }
                else -> {
                    if (!isCloudReady || cloudProviderName.isBlank()) {
                        isStatusExpanded = false
                        onNavigateToSettings()
                        return@launch
                    }
                    activeSelection = "CLOUD"
                    targetProvider = cloudProviderName
                    modeKey = "cloud"
                    modeLabel = "Cloud AI ($cloudProviderName)"
                    modeBadgeLabel = cloudProviderName
                }
            }

            isStatusExpanded = false
            activeProcessNote = "Switched Assistant mode to $modeLabel"

            AeginBridge.setStorageItem("selected_llm_mode", modeKey)
            AeginBridge.setStorageItem("selected_llm_provider", targetProvider)

            try {
                // Only update Assistant's own mode; preserve Studio's Worker & Governance AI Model settings intact.
                val rawLlm = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
                val (providers, wProv, gProv) = AeginBridge.parseLlmSettingsJson(rawLlm)
                val payload = AeginBridge.buildLlmSettingsJson(
                    providers = providers,
                    workerProvider = wProv,
                    governanceProvider = gProv,
                    assistantProvider = targetProvider
                )
                AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", payload))
            } catch (_: Exception) {
            }

            if (messages.size <= 1 && (messages.isEmpty() || messages[0].id.startsWith("init-"))) {
                refreshMainGreetingCard()
            } else {
                messages.add(
                    ChatMessage(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        sender = "agent",
                        text = when (modeKey) {
                            "auto" -> "Switched Assistant to Auto mode. Interactive chat uses local-first routing while background tasks follow Studio Worker/Governance settings."
                            "local" -> "Switched Assistant to Local mode. Interactive chat runs via the embedded on-device GGUF engine."
                            else -> "Switched Assistant to $cloudProviderName. Interactive chat routes through $cloudProviderName while Governance guardrails stay active."
                        },
                        modeBadge = modeBadgeLabel
                    )
                )
                persistCurrentThreadMessages()
            }
        }
    }

    fun toggleQuickMode() {
        if (!isAutoReady) {
            onNavigateToSettings()
            return
        }
        val availableModes = buildList {
            if (isAutoReady) add("Auto")
            if (isLocalReady) add("On-Device Model")
            if (isCloudReady && cloudProviderName.isNotBlank()) add(cloudProviderName)
        }
        if (availableModes.isEmpty()) {
            onNavigateToSettings()
            return
        }
        val currentTarget = when {
            isAutoMode -> "Auto"
            isLocalMode -> "On-Device Model"
            isCloudMode -> cloudProviderName
            else -> ""
        }
        val idx = availableModes.indexOfFirst { it.equals(currentTarget, ignoreCase = true) }
        val next = availableModes[(idx + 1) % availableModes.size]
        selectActiveMode(next)
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

        if (hasNoActiveModel) {
            isStatusExpanded = true
            messages.add(
                ChatMessage(
                    id = Clock.System.now().toEpochMilliseconds().toString(),
                    sender = "agent",
                    text = "No AI model is configured yet. Select a mode above or open Studio -> Configuration to configure an On-Device or external AI model.",
                    modeBadge = "Setup Required"
                )
            )
            persistCurrentThreadMessages()
            return
        }

        val currentBadge = when {
            isAutoMode -> "Auto"
            isLocalMode -> "Local"
            else -> cloudProviderName
        }
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
        activeProcessNote = when {
            isAutoMode -> "Auto router evaluating task category (local-first)..."
            isLocalMode -> "Retrieving Vault vectors & running local GGUF inference..."
            else -> "Routing prompt via $cloudProviderName..."
        }

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

            val currentAssistantMode = when {
                isAutoMode -> "Auto"
                isLocalMode -> "On-Device Model"
                else -> cloudProviderName
            }

            try {
                val reply = if (isLocalMode || isAutoMode) {
                    val streamedText = StringBuilder()
                    AeginBridge.streamPrompt(
                        prompt = trimmed,
                        sessionId = activeThread,
                        prevSummary = null,
                        assistantMode = currentAssistantMode,
                        spaceId = normalizedSpaceId,
                        spaceName = activeSpaceName
                    ).collect { piece ->
                        streamedText.append(piece)
                        updateBubble(streamedText.toString())
                    }
                    if (streamedText.isNotEmpty()) {
                        streamedText.toString()
                    } else {
                        val (ans, _) = AeginBridge.askAgentInSpace(
                            prompt = trimmed,
                            spaceId = normalizedSpaceId,
                            spaceName = activeSpaceName,
                            sessionId = activeThread,
                            prevSummary = null,
                            assistantMode = currentAssistantMode
                        )
                        ans
                    }
                } else {
                    val (ans, _) = AeginBridge.askAgentInSpace(
                        prompt = trimmed,
                        spaceId = normalizedSpaceId,
                        spaceName = activeSpaceName,
                        sessionId = activeThread,
                        prevSummary = null,
                        assistantMode = currentAssistantMode
                    )
                    ans
                }

                updateBubble(reply)
                activeProcessNote = "Completed via $currentBadge • ${vaultStats.chunkCount} vectors active"
            } catch (e: Exception) {
                val fallback = try {
                    val (ans, _) = AeginBridge.askAgentInSpace(
                        prompt = trimmed,
                        spaceId = normalizedSpaceId,
                        spaceName = activeSpaceName,
                        sessionId = activeThread,
                        prevSummary = null,
                        assistantMode = currentAssistantMode
                    )
                    ans
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

    var pendingStarterPrompt by remember { mutableStateOf<String?>(null) }
    var hydratedSpaceId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(normalizedSpaceId) {
        val cached = threadMessagesMap[normalizedSpaceId]
        if (cached != null) {
            syncThreadMessages(normalizedSpaceId)
        } else {
            val initMsg = buildMainGreetingMessage()
            threadMessagesMap[normalizedSpaceId] = listOf(initMsg)
            syncThreadMessages(normalizedSpaceId)
        }

        try {
            val history = AeginBridge.getSpaceHistory(normalizedSpaceId, 100)
            val hydrated = history
                .filter { it.role.equals("USER", ignoreCase = true) || it.role.equals("ASSISTANT", ignoreCase = true) }
                .mapIndexed { idx, entry ->
                    ChatMessage(
                        id = "db-$normalizedSpaceId-$idx-${entry.timestamp}",
                        sender = if (entry.role.equals("USER", ignoreCase = true)) "user" else "agent",
                        text = formatPersistedDisplayText(entry.role, entry.content),
                        modeBadge = activeSpaceName
                    )
                }
            if (hydrated.isNotEmpty() && !isThinking) {
                threadMessagesMap[normalizedSpaceId] = hydrated
                syncThreadMessages(normalizedSpaceId)
            }
        } catch (_: Exception) {
        } finally {
            hydratedSpaceId = normalizedSpaceId
        }
    }

    LaunchedEffect(activeSelection, cloudProviderName) {
        refreshMainGreetingCard()
    }

    LaunchedEffect(hydratedSpaceId, initialPrompt, pendingStarterPrompt) {
        if (hydratedSpaceId != normalizedSpaceId) return@LaunchedEffect
        val toRun = when {
            !initialPrompt.isNullOrBlank() -> {
                onConsumedInitialPrompt()
                initialPrompt
            }
            !pendingStarterPrompt.isNullOrBlank() -> {
                val next = pendingStarterPrompt
                pendingStarterPrompt = null
                next
            }
            else -> null
        }
        if (!toRun.isNullOrBlank()) {
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
        // 1. TOP HEADER: MODEL PILL (LEFT) • CENTERED ROBOT DOG • TOOLS (RIGHT)
        // ─────────────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isStatusExpanded) isStatusExpanded = false
                }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Very Left: AI Model Type Pill (Tap to expand selection card)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .widthIn(max = 136.dp)
                    .clip(CircleShape)
                    .background(theme.card)
                    .border(
                        width = 1.dp,
                        color = if (isStatusExpanded) theme.primary else theme.glassBorder,
                        shape = CircleShape
                    )
                    .clickable {
                        val opening = !isStatusExpanded
                        isStatusExpanded = opening
                        if (opening) {
                            scope.launch { loadActiveMode() }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isAutoMode -> theme.success
                                isLocalMode -> theme.primary
                                isCloudMode -> theme.cyan
                                else -> theme.warning
                            }
                        )
                )
                Text(
                    text = when {
                        isAutoMode -> "Auto"
                        isLocalMode -> "Local"
                        isCloudMode -> cloudProviderName.ifBlank { "Cloud AI" }
                        else -> "No AI Model"
                    },
                    color = if (hasNoActiveModel) theme.subText else theme.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = if (isStatusExpanded) "▴" else "▾",
                    color = theme.subText,
                    fontSize = 10.sp
                )
            }

            // Center Top: Single Animated Robot Dog Mascot
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable {
                        val opening = !isStatusExpanded
                        isStatusExpanded = opening
                        if (opening) {
                            scope.launch { loadActiveMode() }
                        }
                    }
            ) {
                AeginOrbAvatar(
                    size = 50.dp,
                    isLocalMode = isLocalMode || !isCloudMode,
                    isThinking = isThinking,
                    showStatusDot = true
                )
            }

            // Right Controls: Theme Toggle
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Theme Toggle Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable {
                            isStatusExpanded = false
                            onToggleTheme()
                        },
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

        // Body container allowing outside taps anywhere below the pill to dismiss the mode drawer
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

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
                                onClick = {
                                    val sid = AeginBridge.normalizeSpaceId(starterCards[0].title)
                                    pendingStarterPrompt = starterCards[0].prompt
                                    onSelectSpace(sid, starterCards[0].title)
                                }
                            )
                            StarterSpaceCard(
                                card = starterCards[1],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val sid = AeginBridge.normalizeSpaceId(starterCards[1].title)
                                    pendingStarterPrompt = starterCards[1].prompt
                                    onSelectSpace(sid, starterCards[1].title)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StarterSpaceCard(
                                card = starterCards[2],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val sid = AeginBridge.normalizeSpaceId(starterCards[2].title)
                                    pendingStarterPrompt = starterCards[2].prompt
                                    onSelectSpace(sid, starterCards[2].title)
                                }
                            )
                            StarterSpaceCard(
                                card = starterCards[3],
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val sid = AeginBridge.normalizeSpaceId(starterCards[3].title)
                                    pendingStarterPrompt = starterCards[3].prompt
                                    onSelectSpace(sid, starterCards[3].title)
                                }
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
                            showStatusDot = false,
                            animateIdle = false
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
                                    val badgeLabel = msg.modeBadge.trim()
                                    if (badgeLabel.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        isAutoMode -> theme.success.copy(alpha = 0.14f)
                                                        isLocalMode -> theme.primary.copy(alpha = 0.14f)
                                                        else -> theme.cyan.copy(alpha = 0.14f)
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                color = when {
                                                    isAutoMode -> theme.success
                                                    isLocalMode -> theme.primary
                                                    else -> theme.cyan
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
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
                            when {
                                isAutoMode -> theme.success.copy(alpha = 0.16f)
                                isLocalMode -> theme.primary.copy(alpha = 0.16f)
                                isCloudMode -> theme.cyan.copy(alpha = 0.16f)
                                else -> theme.warning.copy(alpha = 0.16f)
                            }
                        )
                        .clickable { toggleQuickMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isAutoMode -> "⚡"
                            isLocalMode -> "📱"
                            isCloudMode -> "☁️"
                            else -> "⚠️"
                        },
                        fontSize = 15.sp
                    )
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) isStatusExpanded = false },
                    placeholder = {
                        Text(
                            text = when {
                                isAutoMode -> "Ask Aegin (Auto)..."
                                isLocalMode -> "Ask Aegin (Local)..."
                                isCloudMode -> "Ask Aegin ($cloudProviderName)..."
                                else -> "Configure AI Model in Studio..."
                            },
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
        } // end Section 5 Column
        } // end inner content Column

            if (isStatusExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isStatusExpanded = false
                        }
                )
            }

            // Floating Mode Selection Card (closes when tapping outside or selecting a mode)
            androidx.compose.animation.AnimatedVisibility(
                visible = isStatusExpanded,
                modifier = Modifier.align(Alignment.TopCenter),
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IntelligenceLayerTile(
                            title = "⚡ Auto",
                            subtitle = if (isAutoReady) "Local-First Smart" else "Not configured",
                            accentColor = theme.success,
                            selected = isAutoMode && isAutoReady,
                            enabled = isAutoReady,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isAutoReady) {
                                    if (!isAutoMode) selectActiveMode("Auto")
                                    else isStatusExpanded = false
                                } else {
                                    isStatusExpanded = false
                                    onNavigateToSettings()
                                }
                            }
                        )

                        IntelligenceLayerTile(
                            title = "📱 Local",
                            subtitle = if (isLocalReady) "Offline GGUF" else "Not configured",
                            accentColor = theme.primary,
                            selected = isLocalMode && isLocalReady,
                            enabled = isLocalReady,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isLocalReady) {
                                    if (!isLocalMode) selectActiveMode("On-Device Model")
                                    else isStatusExpanded = false
                                } else {
                                    isStatusExpanded = false
                                    onNavigateToSettings()
                                }
                            }
                        )

                        IntelligenceLayerTile(
                            title = if (isCloudReady && cloudProviderName.isNotBlank()) "☁️ $cloudProviderName" else "☁️ Cloud AI",
                            subtitle = if (isCloudReady) "External LLM" else "Not configured",
                            accentColor = theme.cyan,
                            selected = isCloudMode && isCloudReady,
                            enabled = isCloudReady && cloudProviderName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isCloudReady && cloudProviderName.isNotBlank()) {
                                    if (!isCloudMode) selectActiveMode(cloudProviderName)
                                    else isStatusExpanded = false
                                } else {
                                    isStatusExpanded = false
                                    onNavigateToSettings()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    PlatformBackHandler(enabled = isStatusExpanded) {
        isStatusExpanded = false
    }
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

@Composable
private fun IntelligenceLayerTile(
    title: String,
    subtitle: String,
    accentColor: Color,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    val bgColor = when {
        !enabled -> theme.surfaceElevated.copy(alpha = 0.45f)
        selected -> accentColor.copy(alpha = 0.14f)
        else -> theme.inputBg
    }
    val borderColor = when {
        !enabled -> theme.border.copy(alpha = 0.4f)
        selected -> accentColor
        else -> theme.border
    }
    val titleColor = when {
        !enabled -> theme.subText.copy(alpha = 0.55f)
        selected -> accentColor
        else -> theme.text
    }
    val subColor = if (!enabled) theme.subText.copy(alpha = 0.5f) else theme.subText

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(
            text = title,
            color = titleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = subColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
