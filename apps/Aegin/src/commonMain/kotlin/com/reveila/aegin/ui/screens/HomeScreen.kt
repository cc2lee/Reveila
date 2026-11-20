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
import androidx.compose.runtime.collectAsState
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
import com.reveila.aegin.bridge.AeginDisplayMapper
import com.reveila.aegin.bridge.ChatToolAttachment
import com.reveila.aegin.bridge.KnowledgeFileItem
import com.reveila.aegin.bridge.SpaceThreadSummary
import com.reveila.aegin.bridge.TaskItem
import com.reveila.aegin.bridge.ToolHubSnapshot
import com.reveila.aegin.bridge.VaultStats
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.components.ActiveLargeToolWindow
import com.reveila.aegin.ui.components.FormattedChatText
import com.reveila.aegin.ui.components.LargeBrowserTakeoverDialog
import com.reveila.aegin.ui.components.LargeDownloadTakeoverDialog
import com.reveila.aegin.ui.components.LargeTerminalTakeoverDialog
import com.reveila.aegin.ui.components.LiveToolMiniCardsPanel
import com.reveila.aegin.ui.components.MiniBrowserCard
import com.reveila.aegin.ui.components.VaultScopeSelectorDialog
import com.reveila.aegin.ui.components.WaitingForUserBanner
import com.reveila.aegin.ui.mascot.AeginMascotMode
import com.reveila.aegin.ui.mascot.AeginRobotDogMascot
import com.reveila.aegin.ui.mascot.LocalAeginMascotController
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
    val timestampLabel: String = "Just now",
    val toolAttachment: ChatToolAttachment? = null
)

private data class QuickChipSpec(
    val label: String,
    val key: String,
    val icon: AeginIconType,
    val prompt: String
)

@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSpaces: () -> Unit,
    onNavigateToTasks: (draftPrompt: String?) -> Unit = {},
    onOpenSecurityPortal: () -> Unit = {},
    onToggleTheme: () -> Unit,
    activeSpaceId: String = "main-space",
    activeSpaceName: String = "Assistant",
    onSelectSpace: (String, String) -> Unit = { _, _ -> },
    initialPrompt: String? = null,
    onConsumedInitialPrompt: () -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val mascotController = LocalAeginMascotController.current
    val mascotState by mascotController.state.collectAsState()

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
    var isMenuExpanded by remember { mutableStateOf(false) }
    var activeProcessNote by remember { mutableStateOf("Idle • Checking configured AI models...") }
    val allPersistedSpaces = remember { mutableStateListOf<SpaceThreadSummary>() }
    var toolSnapshot by remember { mutableStateOf(ToolHubSnapshot()) }
    var activeLargeWindow by remember { mutableStateOf(ActiveLargeToolWindow.NONE) }
    var isAuthorizingTool by remember { mutableStateOf(false) }

    val isAutoMode = activeSelection == "AUTO"
    val isLocalMode = activeSelection == "LOCAL"
    val isCloudMode = activeSelection == "CLOUD"
    val hasNoActiveModel = activeSelection == "NONE"

    // Non-blocking background task telemetry & status reporting
    val inFlightTasks = remember { mutableStateListOf<TaskItem>() }
    val seenTaskEvents = remember { mutableSetOf<String>() }
    var initializedTaskEvents by remember { mutableStateOf(false) }

    var showSummarizeScopeDialog by remember { mutableStateOf(false) }
    var vaultScopeDialogMode by remember { mutableStateOf("SUMMARIZE") } // "SUMMARIZE" | "ANALYZE" | "PIN"
    var pinnedVaultScopePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var pinnedVaultScopeLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var knowledgeFilesList by remember { mutableStateOf<List<KnowledgeFileItem>>(emptyList()) }
    var isRefreshingScopeFiles by remember { mutableStateOf(false) }

    // Active persisted conversation Space & session key
    val normalizedSpaceId = remember(activeSpaceId) { AeginBridge.normalizeSpaceId(activeSpaceId) }
    val activeThread = remember(normalizedSpaceId) { "space-$normalizedSpaceId" }

    val threadMessagesMap = remember {
        mutableStateMapOf(
            "main-space" to listOf(
                ChatMessage(
                    id = "init-main",
                    sender = "agent",
                    text = "Please configure and select an AI mode to begin. Tap \"No AI Model\" at the top-left or open Menu → Settings.",
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
                text = "Please configure and select an AI mode to begin. Tap \"No AI Model\" at the top-left or open Menu → Settings.",
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

    fun parsePersistedAssistantMessage(role: String, rawContent: String): Pair<String, ChatToolAttachment?> {
        val trimmed = rawContent.trim()
        if (!role.equals("ASSISTANT", ignoreCase = true) || !trimmed.startsWith("{")) {
            return rawContent to null
        }
        return try {
            val parsed = AeginBridge.parseAgentReplyWithAttachment(trimmed)
            parsed.text.ifBlank { rawContent } to parsed.attachment
        } catch (_: Exception) {
            rawContent to null
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
                "AUTO" -> "Auto • Smart routing ready"
                "LOCAL" -> "On-Device • Private offline AI ready"
                "CLOUD" -> "Cloud • $cloudProviderName ready"
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
                    modeLabel = "Smart Auto"
                    modeBadgeLabel = "Auto"
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
                    modeLabel = "On-Device AI (Offline)"
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
                    modeLabel = "Cloud ($cloudProviderName)"
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
                            "auto" -> "Switched Assistant to Auto mode. Chats automatically choose the best AI for each request."
                            "local" -> "Switched Assistant to On-Device mode. Chats run privately on your device."
                            else -> "Switched Assistant to $cloudProviderName. Safety checks remain active."
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

    suspend fun syncSpaceHistoryFromDb(spaceKey: String, spaceLabel: String) {
        try {
            val history = AeginBridge.getSpaceHistory(spaceKey, 100)
            val hydrated = history.mapIndexedNotNull { idx, entry ->
                when {
                    AeginBridge.isConversationSummaryEntry(entry) -> ChatMessage(
                        id = "db-summary-$spaceKey-$idx-${entry.timestamp}",
                        sender = "summary",
                        text = AeginBridge.extractConversationSummaryText(entry.content),
                        modeBadge = "Earlier Summary"
                    )
                    entry.role.equals("USER", ignoreCase = true) -> {
                        if (entry.content.startsWith("Authorized tool execution finished with results:") ||
                            entry.content.startsWith("The AI has requested to call tools")
                        ) {
                            null
                        } else {
                            ChatMessage(
                                id = "db-$spaceKey-$idx-${entry.timestamp}",
                                sender = "user",
                                text = entry.content,
                                modeBadge = spaceLabel
                            )
                        }
                    }
                    entry.role.equals("ASSISTANT", ignoreCase = true) -> {
                        val (displayText, attachment) = parsePersistedAssistantMessage(entry.role, entry.content)
                        ChatMessage(
                            id = "db-$spaceKey-$idx-${entry.timestamp}",
                            sender = "agent",
                            text = displayText,
                            modeBadge = spaceLabel,
                            toolAttachment = attachment
                        )
                    }
                    else -> null
                }
            }
            if (hydrated.isNotEmpty() && !isThinking) {
                threadMessagesMap[spaceKey] = hydrated
                syncThreadMessages(spaceKey)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun refreshRecentSpaces() {
        try {
            val list = AeginBridge.listSpaces()
            allPersistedSpaces.clear()
            allPersistedSpaces.addAll(list)
        } catch (_: Exception) {
        }
    }

    fun dispatchPrompt(rawInput: String, vaultScopePaths: List<String> = emptyList()) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || isThinking) return

        if (hasNoActiveModel) {
            isStatusExpanded = true
            messages.add(
                ChatMessage(
                    id = Clock.System.now().toEpochMilliseconds().toString(),
                    sender = "agent",
                    text = "No AI model is configured yet. Select a mode above or open Menu → Settings to configure an On-Device or cloud AI model.",
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
            isAutoMode -> "Choosing the best AI for your request..."
            isLocalMode -> "Searching your Personal Knowledge Folder & thinking privately..."
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

            val browserBeforeMs = toolSnapshot.browser.updatedAtMs
            val updateBubble: (String, ChatToolAttachment?) -> Unit = { newText, attachment ->
                val idx = messages.indexOfFirst { it.id == agentMsgId }
                if (idx >= 0) {
                    val prevAttachment = messages[idx].toolAttachment
                    messages[idx] = ChatMessage(
                        id = agentMsgId,
                        sender = "agent",
                        text = newText,
                        modeBadge = currentBadge,
                        toolAttachment = attachment ?: prevAttachment
                    )
                    persistCurrentThreadMessages()
                }
            }

            val currentAssistantMode = when {
                isAutoMode -> "Auto"
                isLocalMode -> "On-Device Model"
                else -> cloudProviderName
            }

            var hadDispatchError = false
            try {
                val effectiveVaultScope = if (vaultScopePaths.isNotEmpty()) vaultScopePaths else pinnedVaultScopePaths
                AeginBridge.setVaultSearchScope(effectiveVaultScope)
                val reply = if (isLocalMode || isAutoMode) {
                    mascotController.onLocalSlmRouting()
                    val streamedText = StringBuilder()
                    val streamStartMs = Clock.System.now().toEpochMilliseconds()
                    var chunkCount = 0
                    AeginBridge.streamPrompt(
                        prompt = trimmed,
                        sessionId = activeThread,
                        prevSummary = null,
                        assistantMode = currentAssistantMode,
                        spaceId = normalizedSpaceId,
                        spaceName = activeSpaceName
                    ).collect { piece ->
                        chunkCount++
                        val elapsedSec = ((Clock.System.now().toEpochMilliseconds() - streamStartMs).coerceAtLeast(100L)) / 1000f
                        mascotController.onStreamingResponse(tokensPerSecond = chunkCount / elapsedSec)
                        streamedText.append(piece)
                        updateBubble(streamedText.toString(), null)
                    }
                    if (streamedText.isNotEmpty()) {
                        streamedText.toString()
                    } else {
                        mascotController.onCoordinatingWithModel(cloudProviderName.ifBlank { "AI Model" })
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
                    mascotController.onCoordinatingWithModel(cloudProviderName.ifBlank { "Cloud AI" })
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

                val postSnap = try {
                    AeginBridge.getToolActivitySnapshot().also { toolSnapshot = it }
                } catch (_: Exception) {
                    toolSnapshot
                }
                val liveBrowserAttachment = if (postSnap.browser.updatedAtMs > browserBeforeMs && postSnap.browser.url.isNotBlank()) {
                    ChatToolAttachment(
                        tool = "virtual_browser",
                        url = postSnap.browser.url,
                        title = postSnap.browser.title,
                        statusCode = postSnap.browser.statusCode,
                        lastAction = postSnap.browser.lastAction,
                        contentPreview = postSnap.browser.contentPreview,
                        rawHtml = postSnap.browser.rawHtml
                    )
                } else {
                    null
                }
                updateBubble(reply, liveBrowserAttachment)
                activeProcessNote = "Ready • ${vaultStats.chunkCount} indexed sections"
            } catch (e: Exception) {
                hadDispatchError = true
                mascotController.onError("Attention needed")
                val fallback = if (isLocalMode || isAutoMode) {
                    try {
                        val (ans, _) = AeginBridge.askAgentInSpace(
                            prompt = trimmed,
                            spaceId = normalizedSpaceId,
                            spaceName = activeSpaceName,
                            sessionId = activeThread,
                            prevSummary = null,
                            assistantMode = currentAssistantMode
                        )
                        hadDispatchError = false
                        ans
                    } catch (inner: Exception) {
                        "Error processing request: ${inner.message ?: "Engine unavailable"}"
                    }
                } else {
                    "Error processing request: ${e.message ?: "LLM request failed or timed out."}"
                }
                updateBubble(fallback, null)
                activeProcessNote = "Ready • Fallback handled"
            } finally {
                AeginBridge.setVaultSearchScope(emptyList())
                isThinking = false
                if (!hadDispatchError) {
                    mascotController.onIdle()
                }
                try {
                    toolSnapshot = AeginBridge.getToolActivitySnapshot()
                } catch (_: Exception) {
                }
                syncSpaceHistoryFromDb(normalizedSpaceId, activeSpaceName)
                refreshRecentSpaces()
                delay(50)
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
        }
    }

    fun refreshKnowledgeScopeList(forceRescan: Boolean) {
        scope.launch {
            isRefreshingScopeFiles = true
            try {
                if (forceRescan) {
                    AeginBridge.scanVault()
                }
                refreshVaultSummary()
                val docs = AeginBridge.listKnowledgeFiles()
                if (docs.isEmpty() && !forceRescan) {
                    AeginBridge.scanVault()
                    refreshVaultSummary()
                    knowledgeFilesList = AeginBridge.listKnowledgeFiles()
                } else {
                    knowledgeFilesList = docs
                }
            } catch (_: Exception) {
                knowledgeFilesList = emptyList()
            } finally {
                isRefreshingScopeFiles = false
            }
        }
    }

    fun openVaultScopeSelector(mode: String = "SUMMARIZE") {
        if (isThinking) return
        vaultScopeDialogMode = mode
        showSummarizeScopeDialog = true
        refreshKnowledgeScopeList(forceRescan = false)
    }

    fun dispatchKnowledgeShortcut(shortcutLabel: String, shortcutPrompt: String) {
        if (isThinking) return
        if (hasNoActiveModel) {
            dispatchPrompt(shortcutPrompt)
            return
        }
        scope.launch {
            refreshVaultSummary()
            if (vaultStats.documentCount <= 0) {
                messages.add(
                    ChatMessage(
                        id = Clock.System.now().toEpochMilliseconds().toString(),
                        sender = "user",
                        text = shortcutPrompt,
                        modeBadge = ""
                    )
                )
                messages.add(
                    ChatMessage(
                        id = (Clock.System.now().toEpochMilliseconds() + 1).toString(),
                        sender = "agent",
                        text = "No files are indexed in your Personal Knowledge Folder ($vaultFolder) yet, so \"$shortcutLabel\" has nothing to scan.\n\nAdd supported files (.pdf, .docx, .txt, .md, .log, .csv, .json, .xml, .yaml, .html) to that folder, choose a folder in Menu → Settings, or paste your text directly in this chat.",
                        modeBadge = "Personal Knowledge Folder"
                    )
                )
                persistCurrentThreadMessages()
                delay(50)
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            } else {
                val effectivePrompt = if (pinnedVaultScopePaths.isNotEmpty() && pinnedVaultScopeLabels.isNotEmpty()) {
                    "$shortcutPrompt (Focus strictly on: ${pinnedVaultScopeLabels.joinToString(", ")}.)"
                } else {
                    shortcutPrompt
                }
                dispatchPrompt(effectivePrompt, vaultScopePaths = pinnedVaultScopePaths)
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
        refreshRecentSpaces()
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
                            val friendlyEvtStatus = AeginDisplayMapper.taskStatus(evt.status)
                            val cardText = buildString {
                                appendLine("$statusIcon Background Task $friendlyEvtStatus: ${evt.title}")
                                if (evt.summary.isNotBlank()) {
                                    append(evt.summary)
                                }
                            }.trim()
                            messages.add(
                                ChatMessage(
                                    id = evt.eventId,
                                    sender = "agent",
                                    text = cardText,
                                    modeBadge = "Task • $friendlyEvtStatus",
                                    toolAttachment = evt.toolAttachment
                                )
                            )
                            persistCurrentThreadMessages()
                        }
                    }
                }
            } catch (_: Exception) {
            }
            try {
                toolSnapshot = AeginBridge.getToolActivitySnapshot()
            } catch (_: Exception) {
            }
            val fastToolTick = isThinking ||
                toolSnapshot.waitingForUserInput ||
                toolSnapshot.terminal.status == "RUNNING" ||
                toolSnapshot.downloads.items.any { it.status == "DOWNLOADING" }
            delay(
                when {
                    fastToolTick -> 750L
                    inFlightTasks.isNotEmpty() -> 1000L
                    else -> 2400L
                }
            )
        }
    }

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
            syncSpaceHistoryFromDb(normalizedSpaceId, activeSpaceName)
        } finally {
            hydratedSpaceId = normalizedSpaceId
            refreshRecentSpaces()
        }
    }

    LaunchedEffect(activeSelection, cloudProviderName) {
        refreshMainGreetingCard()
    }

    LaunchedEffect(hydratedSpaceId, initialPrompt) {
        if (hydratedSpaceId != normalizedSpaceId) return@LaunchedEffect
        val toRun = if (!initialPrompt.isNullOrBlank()) {
            onConsumedInitialPrompt()
            initialPrompt
        } else {
            null
        }
        if (!toRun.isNullOrBlank()) {
            dispatchPrompt(toRun)
        }
    }

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
                    if (isMenuExpanded) isMenuExpanded = false
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
                        isMenuExpanded = false
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
                        isCloudMode -> cloudProviderName.ifBlank { "Cloud" }
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
                        isMenuExpanded = false
                        val opening = !isStatusExpanded
                        isStatusExpanded = opening
                        if (opening) {
                            scope.launch { loadActiveMode() }
                        }
                    }
            ) {
                AeginRobotDogMascot(
                    state = mascotState,
                    mode = AeginMascotMode.SMALL_BADGE,
                    size = 54.dp,
                    showTelemetryEffects = true
                )
            }

            // Right Controls: Theme Toggle + Sandwich Menu
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
                            isMenuExpanded = false
                            onToggleTheme()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = if (theme.isDark) AeginIconType.SUN else AeginIconType.MOON,
                        tint = if (theme.isDark) theme.warning else theme.accent,
                        size = 18.dp,
                        strokeWidth = 1.65.dp
                    )
                }

                // Sandwich Menu Button (Spaces, Studio & Settings, Privacy & Safety)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isMenuExpanded) theme.cyan.copy(alpha = 0.16f) else theme.card)
                        .border(
                            width = 1.dp,
                            color = if (isMenuExpanded) theme.cyan else theme.glassBorder,
                            shape = CircleShape
                        )
                        .clickable {
                            isStatusExpanded = false
                            isMenuExpanded = !isMenuExpanded
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AeginVectorIcon(
                        type = AeginIconType.MENU,
                        tint = if (isMenuExpanded) theme.cyan else theme.text,
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
                            .clickable { onNavigateToTasks(null) }
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
                                    text = "${bgTask.title} • ${AeginDisplayMapper.taskStatus(bgTask.status)}",
                                    color = theme.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = AeginDisplayMapper.taskStep(bgTask.currentStep, isSuspended = !isRunning),
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
                                    text = if (isRunning) "⏸ Pause" else "▶ Resume",
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
                                    text = "⏹ Stop",
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
        // 3.6. HIGH-VISIBILITY WAITING FOR USER INPUT / AUTHORIZATION BANNER
        // ─────────────────────────────────────────────────────────────────────
        WaitingForUserBanner(
            snapshot = toolSnapshot,
            isBusy = isAuthorizingTool || isThinking,
            onAuthorizeDecision = { reqId, decision, user, pass, sKey, sVal, editedScript ->
                if (!isAuthorizingTool) {
                    isAuthorizingTool = true
                    scope.launch {
                        try {
                            val res = AeginBridge.resolvePendingToolAuthorization(
                                requestId = reqId,
                                decision = decision,
                                username = user,
                                password = pass,
                                secretKey = sKey,
                                secretValue = sVal,
                                editedScript = editedScript
                            )
                            toolSnapshot = AeginBridge.getToolActivitySnapshot()
                            if (res.answer.isNotBlank()) {
                                messages.add(
                                    ChatMessage(
                                        id = "auth-res-${Clock.System.now().toEpochMilliseconds()}",
                                        sender = "agent",
                                        text = res.answer,
                                        modeBadge = if (decision == "DENY") "Denied" else "Tool Result"
                                    )
                                )
                                persistCurrentThreadMessages()
                            }
                            syncSpaceHistoryFromDb(normalizedSpaceId, activeSpaceName)
                        } finally {
                            isAuthorizingTool = false
                        }
                    }
                }
            },
            onOpenBrowserWindow = { activeLargeWindow = ActiveLargeToolWindow.BROWSER },
            onOpenTerminalWindow = { activeLargeWindow = ActiveLargeToolWindow.TERMINAL }
        )

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
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                val isSystem = msg.sender == "system"
                val isSummary = msg.sender == "summary"

                if (isSummary) {
                    // Compacted Earlier Conversation Summary Banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.cardAlt.copy(alpha = 0.72f))
                            .border(1.dp, theme.glassBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AeginVectorIcon(
                                type = AeginIconType.SPARKLES,
                                tint = theme.cyan,
                                size = 13.dp
                            )
                            Text(
                                text = "Earlier Conversation Summary",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        FormattedChatText(
                            text = msg.text,
                            baseColor = theme.subText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            onOpenUrl = { clickedUrl ->
                                activeLargeWindow = ActiveLargeToolWindow.BROWSER
                                scope.launch {
                                    toolSnapshot = AeginBridge.browserUserAction("navigate", url = clickedUrl)
                                }
                            }
                        )
                    }
                } else if (isUser) {
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
                    // Full-width Assistant / System Message Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSystem) theme.danger.copy(alpha = 0.12f) else theme.card
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSystem) theme.danger.copy(alpha = 0.4f) else theme.glassBorder,
                                shape = RoundedCornerShape(18.dp)
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

                            FormattedChatText(
                                text = msg.text,
                                baseColor = theme.text,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                onOpenUrl = { clickedUrl ->
                                    activeLargeWindow = ActiveLargeToolWindow.BROWSER
                                    scope.launch {
                                        toolSnapshot = AeginBridge.browserUserAction("navigate", url = clickedUrl)
                                    }
                                }
                            )

                            val browserAttachment = msg.toolAttachment?.takeIf {
                                it.tool.equals("virtual_browser", ignoreCase = true) &&
                                    (it.url.isNotBlank() || it.rawHtml.isNotBlank())
                            }
                            if (browserAttachment != null) {
                                MiniBrowserCard(
                                    state = browserAttachment.toBrowserLiveState(),
                                    onClick = {
                                        activeLargeWindow = ActiveLargeToolWindow.BROWSER
                                        scope.launch {
                                            toolSnapshot = if (browserAttachment.rawHtml.isNotBlank()) {
                                                AeginBridge.browserUserAction(
                                                    action = "sync_webview",
                                                    url = browserAttachment.url,
                                                    rawHtml = browserAttachment.rawHtml
                                                )
                                            } else {
                                                AeginBridge.browserUserAction(
                                                    action = "navigate",
                                                    url = browserAttachment.url
                                                )
                                            }
                                        }
                                    }
                                )
                            }

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
                                            .clickable {
                                                onNavigateToTasks("Automate this recurring check:\n${msg.text.take(280)}")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "⚡ Automate as Task",
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

            if (toolSnapshot.browser.isVisible || toolSnapshot.terminal.isVisible || toolSnapshot.downloads.items.isNotEmpty() || toolSnapshot.downloads.isVisible) {
                val lastHasSameBrowserCard = messages.lastOrNull()?.toolAttachment?.let { att ->
                    att.tool.equals("virtual_browser", ignoreCase = true) &&
                        att.url.isNotBlank() &&
                        att.url.equals(toolSnapshot.browser.url, ignoreCase = true)
                } == true
                item(key = "live_tool_mini_cards") {
                    LiveToolMiniCardsPanel(
                        snapshot = toolSnapshot,
                        isLocalMode = isLocalMode,
                        suppressBrowserCard = lastHasSameBrowserCard,
                        onOpenWindow = { win -> activeLargeWindow = win }
                    )
                }
            }

            if (isThinking) {
                item(key = "thinking_indicator") {
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
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
                            text = if (isLocalMode) "Thinking privately on your device..." else "Querying $cloudProviderName...",
                            color = theme.subText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────────
        // 5. QUICK SUGGESTION CHIPS + FLOATING PILL COMPOSER + RECENT SPACES STRIP
        // ─────────────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 14.dp)
                .padding(top = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeQuickActionPillsBar(
                pillsEnabled = !isThinking && !hasNoActiveModel,
                pinnedFolderCount = pinnedVaultScopePaths.size,
                onOpenFolderPin = { openVaultScopeSelector("PIN") },
                onDispatchShortcut = { key, shortcutPrompt -> dispatchKnowledgeShortcut(key, shortcutPrompt) }
            )

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
                    when {
                        isAutoMode -> AeginVectorIcon(
                            type = AeginIconType.BOLT,
                            tint = theme.success,
                            size = 16.dp,
                            strokeWidth = 1.8.dp
                        )
                        isLocalMode -> AeginVectorIcon(
                            type = AeginIconType.DEVICE,
                            tint = theme.primary,
                            size = 16.dp,
                            strokeWidth = 1.8.dp
                        )
                        isCloudMode -> AeginVectorIcon(
                            type = AeginIconType.CLOUD,
                            tint = theme.cyan,
                            size = 16.dp,
                            strokeWidth = 1.8.dp
                        )
                        else -> Text(text = "⚠️", fontSize = 15.sp)
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        mascotController.onUserInputActive(it.isNotBlank())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged {
                            if (it.isFocused) {
                                isStatusExpanded = false
                                isMenuExpanded = false
                            }
                        },
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

            // Compact Horizontal Strip: 3 Most Recent Spaces + "•••" (All Spaces)
            val recentSpaces = remember(allPersistedSpaces.toList(), normalizedSpaceId, activeSpaceName) {
                AeginBridge.selectRecentSpacesForStrip(
                    persistedSpaces = allPersistedSpaces,
                    activeSpaceId = normalizedSpaceId,
                    activeSpaceName = activeSpaceName,
                    maxCount = 3
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                recentSpaces.forEach { spaceItem ->
                    val isActiveSpace = spaceItem.spaceId == normalizedSpaceId
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(
                                if (isActiveSpace) theme.cyan.copy(alpha = 0.16f)
                                else theme.card.copy(alpha = 0.72f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActiveSpace) theme.cyan.copy(alpha = 0.55f) else theme.glassBorder,
                                shape = CircleShape
                            )
                            .clickable(enabled = !isThinking) {
                                isStatusExpanded = false
                                isMenuExpanded = false
                                onSelectSpace(spaceItem.spaceId, spaceItem.spaceName)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isActiveSpace) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(theme.cyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = spaceItem.spaceName,
                            color = if (isActiveSpace) theme.text else theme.subText,
                            fontSize = 11.sp,
                            fontWeight = if (isActiveSpace) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // "•••" All Spaces Pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .clickable {
                            isStatusExpanded = false
                            isMenuExpanded = false
                            onNavigateToSpaces()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "•••",
                        color = theme.cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        } // end Section 5 Column
        } // end inner content Column

            if (isStatusExpanded || isMenuExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isStatusExpanded = false
                            isMenuExpanded = false
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
                            text = "Select AI Mode",
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
                            icon = AeginIconType.BOLT,
                            title = "Auto",
                            subtitle = if (isAutoReady) "Smart Routing" else "Not configured",
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
                            icon = AeginIconType.DEVICE,
                            title = "Local",
                            subtitle = if (isLocalReady) "Private & Offline" else "Not configured",
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
                            icon = AeginIconType.CLOUD,
                            title = if (isCloudReady && cloudProviderName.isNotBlank()) cloudProviderName else "Cloud",
                            subtitle = if (isCloudReady) "Cloud Service" else "Not configured",
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

            // Floating Top-Right Sandwich Menu Drawer
            androidx.compose.animation.AnimatedVisibility(
                visible = isMenuExpanded,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .widthIn(min = 225.dp, max = 265.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SandwichMenuItem(
                        iconType = AeginIconType.SPACES,
                        accentColor = theme.cyan,
                        title = "Spaces",
                        subtitle = "All chat threads & templates",
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToSpaces()
                        }
                    )
                    SandwichMenuItem(
                        iconType = AeginIconType.BOLT,
                        accentColor = theme.primary,
                        title = "Tasks",
                        subtitle = "Scheduled automations & approvals",
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToTasks(null)
                        }
                    )
                    SandwichMenuItem(
                        iconType = AeginIconType.SETTINGS,
                        accentColor = theme.violet,
                        title = "Settings",
                        subtitle = "AI models & Personal Knowledge Folder",
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToSettings()
                        }
                    )
                    SandwichMenuItem(
                        iconType = AeginIconType.SHIELD,
                        accentColor = theme.success,
                        title = "Privacy & Safety",
                        subtitle = "On-device protection & activity",
                        onClick = {
                            isMenuExpanded = false
                            onOpenSecurityPortal()
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🌐 Web" to ActiveLargeToolWindow.BROWSER,
                            ">_ Shell" to ActiveLargeToolWindow.TERMINAL,
                            "⬇️ Files" to ActiveLargeToolWindow.DOWNLOADS
                        ).forEach { (label, winTarget) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(theme.inputBg)
                                    .border(1.dp, theme.glassBorder, CircleShape)
                                    .clickable {
                                        isMenuExpanded = false
                                        activeLargeWindow = winTarget
                                        scope.launch { toolSnapshot = AeginBridge.getToolActivitySnapshot() }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    when (activeLargeWindow) {
        ActiveLargeToolWindow.BROWSER -> {
            LargeBrowserTakeoverDialog(
                state = toolSnapshot.browser,
                onTakeOver = {
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("takeover") }
                },
                onReturnControlWithCapture = { url, cookieHeader, rawHtml ->
                    scope.launch {
                        toolSnapshot = AeginBridge.browserUserAction(
                            action = "return_control",
                            url = url,
                            cookieHeader = cookieHeader,
                            rawHtml = rawHtml
                        )
                    }
                },
                onNavigateUrl = { url ->
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("navigate", url = url) }
                },
                onBack = {
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("back") }
                },
                onReload = {
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("reload") }
                },
                onClickElement = { target ->
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("click", target = target) }
                },
                onTypeElement = { target, text ->
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("type", target = target, text = text) }
                },
                onSubmitForm = { form ->
                    scope.launch { toolSnapshot = AeginBridge.browserUserAction("submit", form = form.orEmpty()) }
                },
                onClose = { activeLargeWindow = ActiveLargeToolWindow.NONE }
            )
        }

        ActiveLargeToolWindow.TERMINAL -> {
            LargeTerminalTakeoverDialog(
                state = toolSnapshot.terminal,
                onTakeOver = {
                    scope.launch { toolSnapshot = AeginBridge.terminalUserAction("takeover") }
                },
                onReturnControl = {
                    scope.launch { toolSnapshot = AeginBridge.terminalUserAction("return_control") }
                },
                onExecuteScript = { script, workingDir ->
                    scope.launch { toolSnapshot = AeginBridge.terminalUserAction("execute", script = script, workingDir = workingDir) }
                },
                onStop = {
                    scope.launch { toolSnapshot = AeginBridge.terminalUserAction("stop") }
                },
                onClearOutput = {
                    scope.launch { toolSnapshot = AeginBridge.terminalUserAction("clear") }
                },
                onClose = { activeLargeWindow = ActiveLargeToolWindow.NONE }
            )
        }

        ActiveLargeToolWindow.DOWNLOADS -> {
            LargeDownloadTakeoverDialog(
                state = toolSnapshot.downloads,
                onTakeOver = {
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("takeover") }
                },
                onReturnControl = {
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("return_control") }
                },
                onPauseDownload = { id ->
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("pause", downloadId = id) }
                },
                onResumeDownload = { id ->
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("resume", downloadId = id) }
                },
                onCancelDownload = { id ->
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("cancel", downloadId = id) }
                },
                onCleanupTemp = {
                    scope.launch { toolSnapshot = AeginBridge.downloadUserAction("cleanup_temp") }
                },
                onClose = { activeLargeWindow = ActiveLargeToolWindow.NONE }
            )
        }

        ActiveLargeToolWindow.NONE -> Unit
    }

    VaultScopeSelectorDialog(
        visible = showSummarizeScopeDialog,
        vaultFolder = vaultFolder,
        files = knowledgeFilesList,
        isRefreshing = isRefreshingScopeFiles,
        dialogTitle = when (vaultScopeDialogMode) {
            "ANALYZE" -> "Analyze Folder Relationships"
            "PIN" -> "Pin Folder Scope for Chat"
            else -> "Summarize Files"
        },
        confirmButtonLabel = when (vaultScopeDialogMode) {
            "ANALYZE" -> "🔗 Analyze"
            "PIN" -> "✓ Apply Filter"
            else -> "✦ Summarize"
        },
        initialSelectedScopePaths = if (vaultScopeDialogMode == "PIN") pinnedVaultScopePaths else emptyList(),
        onRefresh = { refreshKnowledgeScopeList(forceRescan = true) },
        onOpenSettings = onNavigateToSettings,
        onDismiss = { showSummarizeScopeDialog = false },
        onConfirmScope = { scopePaths, scopeLabels ->
            showSummarizeScopeDialog = false
            when (vaultScopeDialogMode) {
                "PIN" -> {
                    val isRootOnly = scopePaths.size == 1 && scopePaths.first() == "__ROOT__"
                    if (scopePaths.isEmpty() || isRootOnly) {
                        pinnedVaultScopePaths = emptyList()
                        pinnedVaultScopeLabels = emptyList()
                    } else {
                        pinnedVaultScopePaths = scopePaths
                        pinnedVaultScopeLabels = scopeLabels
                    }
                }
                "ANALYZE" -> {
                    val scopeDesc = if (scopePaths.isEmpty()) {
                        "all files in my Personal Knowledge Folder"
                    } else {
                        "all files in ${scopeLabels.joinToString(", ")}"
                    }
                    val targetPrompt = "Analyze relationships, cause-and-effect patterns, key drivers, and ranked totals/breakdowns across $scopeDesc. Cite specific filenames and exact figures/dates."
                    dispatchPrompt(targetPrompt, vaultScopePaths = scopePaths)
                }
                else -> {
                    val targetPrompt = if (scopePaths.isEmpty()) {
                        "Summarize the key insights and decisions from all files in my Personal Knowledge Folder."
                    } else {
                        "Summarize the key insights and decisions from the selected scope in my Personal Knowledge Folder (${scopeLabels.joinToString(", ")})."
                    }
                    dispatchPrompt(targetPrompt, vaultScopePaths = scopePaths)
                }
            }
        }
    )

    PlatformBackHandler(enabled = showSummarizeScopeDialog || isStatusExpanded || isMenuExpanded || activeLargeWindow != ActiveLargeToolWindow.NONE) {
        when {
            showSummarizeScopeDialog -> showSummarizeScopeDialog = false
            activeLargeWindow != ActiveLargeToolWindow.NONE -> activeLargeWindow = ActiveLargeToolWindow.NONE
            else -> {
                isStatusExpanded = false
                isMenuExpanded = false
            }
        }
    }
}

@Composable
private fun SandwichMenuItem(
    iconType: AeginIconType,
    accentColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val theme = LocalAeginColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            AeginVectorIcon(
                type = iconType,
                tint = accentColor,
                size = 16.dp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = theme.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = theme.subText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeQuickActionPillsBar(
    pillsEnabled: Boolean,
    pinnedFolderCount: Int,
    onOpenFolderPin: () -> Unit,
    onDispatchShortcut: (key: String, prompt: String) -> Unit
) {
    val theme = LocalAeginColors.current
    val quickChips = remember {
        listOf(
            QuickChipSpec("Summarize", "Summarize Files", AeginIconType.SPARKLES, "Summarize the key insights and decisions from the files in my Personal Knowledge Folder."),
            QuickChipSpec("Analyze", "Analyze Folder", AeginIconType.THREAD, "Analyze relationships, cause-and-effect patterns, key drivers, and ranked breakdowns across all files in scope. Cite specific filenames, dates, and figures."),
            QuickChipSpec("Actions", "Action Items", AeginIconType.BOLT, "Review the files in my Personal Knowledge Folder and list the most urgent action items."),
            QuickChipSpec("Milestones", "Find Milestones", AeginIconType.CHECK, "Search the files in my Personal Knowledge Folder for project plans, roadmaps, and key milestones."),
            QuickChipSpec("Privacy", "Privacy Check", AeginIconType.SHIELD, "Review the files in my Personal Knowledge Folder and flag any sensitive or confidential details.")
        )
    }
    val disabledAlpha = 0.42f
    val hasPinnedScope = pinnedFolderCount > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val folderTint = when {
            !pillsEnabled -> theme.subText.copy(alpha = disabledAlpha)
            hasPinnedScope -> theme.cyan
            else -> theme.subText
        }
        // Circular icon-only Folder scope button (matching the leading composer icon style)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = pillsEnabled, onClick = onOpenFolderPin),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (pillsEnabled && hasPinnedScope) theme.cyan.copy(alpha = 0.18f)
                        else theme.card.copy(alpha = if (pillsEnabled) 1f else 0.6f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (pillsEnabled && hasPinnedScope) theme.cyan
                        else theme.glassBorder.copy(alpha = if (pillsEnabled) 1f else 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                AeginVectorIcon(
                    type = AeginIconType.FOLDERS_STACK,
                    tint = folderTint,
                    size = 15.dp,
                    strokeWidth = 1.7.dp
                )
            }
            if (hasPinnedScope) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(if (pillsEnabled) theme.cyan else theme.subText.copy(alpha = disabledAlpha))
                        .padding(horizontal = 4.dp, vertical = 0.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$pinnedFolderCount",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 9.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickChips.forEach { chip ->
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.card.copy(alpha = if (pillsEnabled) 1f else 0.6f))
                        .border(
                            width = 1.dp,
                            color = theme.glassBorder.copy(alpha = if (pillsEnabled) 1f else 0.5f),
                            shape = CircleShape
                        )
                        .clickable(enabled = pillsEnabled) {
                            onDispatchShortcut(chip.key, chip.prompt)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AeginVectorIcon(
                        type = chip.icon,
                        tint = theme.subText.copy(alpha = if (pillsEnabled) 1f else disabledAlpha),
                        size = 12.dp,
                        strokeWidth = 1.6.dp
                    )
                    Text(
                        text = chip.label,
                        color = if (pillsEnabled) theme.text else theme.subText.copy(alpha = disabledAlpha),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun IntelligenceLayerTile(
    title: String,
    subtitle: String,
    accentColor: Color,
    selected: Boolean,
    enabled: Boolean,
    icon: AeginIconType? = null,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                AeginVectorIcon(
                    type = icon,
                    tint = titleColor,
                    size = 14.dp,
                    strokeWidth = 1.7.dp
                )
            }
            Text(
                text = title,
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = subtitle,
            color = subColor,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
