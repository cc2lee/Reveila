package com.reveila.aegin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.ChatMessage
import com.reveila.aegin.bridge.VaultStats
import com.reveila.aegin.ui.components.SetupWizard
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isEngineReady by remember { mutableStateOf(false) }
    var showSetupWizard by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val activeMessages = remember {
        mutableStateListOf(
            ChatMessage(
                role = "ASSISTANT",
                content = "Ready. Ask a question or search your documents."
            )
        )
    }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var isCloudMode by remember { mutableStateOf(false) }
    var activeLlmName by remember { mutableStateOf("On-Device Model") }
    var showConfigureCloudDialog by remember { mutableStateOf(false) }
    var vaultStats by remember { mutableStateOf(VaultStats(0, 0, "Documents/Reveila")) }

    fun refreshVaultStats() {
        scope.launch {
            try {
                vaultStats = AeginBridge.getVaultStats()
            } catch (_: Exception) {
            }
        }
    }

    fun syncSelectedLlmMode() {
        scope.launch {
            val mode = AeginBridge.getStorageItem("selected_llm_mode")
            val provider = AeginBridge.getStorageItem("selected_llm_provider")
            if (mode != null && mode != "local") {
                val configuredCloud = AeginBridge.getConfiguredCloudProvider()
                if (!configuredCloud.isNullOrBlank()) {
                    isCloudMode = true
                    activeLlmName = provider ?: configuredCloud
                } else {
                    isCloudMode = false
                    activeLlmName = "On-Device Model"
                    AeginBridge.setStorageItem("selected_llm_mode", "local")
                    AeginBridge.setStorageItem("selected_llm_provider", "On-Device Model")
                }
            } else {
                isCloudMode = false
                activeLlmName = provider ?: "On-Device Model"
            }
        }
    }

    // Engine Status Poll Loop & First-Launch Setup Wizard Detection
    LaunchedEffect(Unit) {
        val completed = AeginBridge.getStorageItem("reveila_setup_completed")
        if (completed != "true") {
            showSetupWizard = true
        }
        syncSelectedLlmMode()

        while (!isEngineReady) {
            try {
                val state = AeginBridge.getEngineStatus()
                if (state.initialized) {
                    isEngineReady = true
                    refreshVaultStats()
                    break
                }
            } catch (_: Exception) {
            }
            delay(800)
        }
    }

    // Auto-scroll chat to latest message
    LaunchedEffect(activeMessages.size, isProcessing) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.size - 1)
        }
    }

    fun toggleLlmMode() {
        scope.launch {
            if (!isCloudMode) {
                val configuredCloud = AeginBridge.getConfiguredCloudProvider()
                if (configuredCloud.isNullOrBlank()) {
                    showConfigureCloudDialog = true
                    return@launch
                }
                isCloudMode = true
                activeLlmName = configuredCloud
                val modeKey = when {
                    configuredCloud.contains("Gemini", ignoreCase = true) -> "gemini"
                    configuredCloud.contains("DeepSeek", ignoreCase = true) -> "deepseek"
                    configuredCloud.contains("Qwen", ignoreCase = true) -> "qwen"
                    else -> "openai"
                }
                AeginBridge.setStorageItem("selected_llm_mode", modeKey)
                AeginBridge.setStorageItem("selected_llm_provider", configuredCloud)

                try {
                    val payload = """{"ai.worker.llm":"$configuredCloud","ai.governance.llm":"$configuredCloud"}"""
                    AeginBridge.invoke("LlmProviderFactory", "setSettings", listOf("llm.json", payload))
                } catch (_: Exception) {
                }
            } else {
                isCloudMode = false
                activeLlmName = "On-Device Model"
                AeginBridge.setStorageItem("selected_llm_mode", "local")
                AeginBridge.setStorageItem("selected_llm_provider", "On-Device Model")

                try {
                    val payload = """{"ai.worker.llm":"On-Device Model","ai.governance.llm":"On-Device Model"}"""
                    AeginBridge.invoke("LlmProviderFactory", "setSettings", listOf("llm.json", payload))
                } catch (_: Exception) {
                }
            }
        }
    }

    fun handleSendPrompt() {
        val currentPrompt = promptText.trim()
        if (currentPrompt.isEmpty() || isProcessing) return

        promptText = ""
        isProcessing = true
        activeMessages.add(ChatMessage(role = "USER", content = currentPrompt))

        scope.launch {
            try {
                var prevSummary: String? = null
                if (activeSessionId == null && activeMessages.isNotEmpty() && activeMessages[0].role == "SYSTEM") {
                    prevSummary = activeMessages[0].content
                }
                val (answer, newSessionId) = AeginBridge.askAgent(currentPrompt, activeSessionId, prevSummary)
                if (!newSessionId.isNullOrBlank()) {
                    activeSessionId = newSessionId
                }
                activeMessages.add(ChatMessage(role = "ASSISTANT", content = answer))
            } catch (e: Exception) {
                activeMessages.add(
                    ChatMessage(
                        role = "SYSTEM",
                        content = "Communication Failure: ${e.message ?: "Engine invocation error"}"
                    )
                )
            } finally {
                isProcessing = false
                refreshVaultStats()
            }
        }
    }

    if (showSetupWizard) {
        SetupWizard(
            visible = true,
            onComplete = {
                showSetupWizard = false
                refreshVaultStats()
                syncSelectedLlmMode()
            },
            onCancel = {
                showSetupWizard = false
                scope.launch {
                    AeginBridge.setStorageItem("reveila_setup_completed", "true")
                }
            }
        )
        return
    }

    val vaultDisplayName = vaultStats.folder
        .replace('\\', '/')
        .trim('/')
        .ifBlank { "Documents/Reveila" }

    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aegin Assistant",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                },
                actions = {
                    if (isEngineReady) {
                        Row(
                            modifier = Modifier.padding(end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isCloudMode) theme.accent else theme.primary)
                                    .clickable { toggleLlmMode() }
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isCloudMode) "☁️ CLOUD" else "📱 ON-DEVICE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable { onNavigateToExplore() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "TASKS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable { onNavigateToSettings() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SETTINGS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable { onToggleTheme() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (theme.isDark) "☀️" else "🌙",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isEngineReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(44.dp))
                        Text(
                            text = "Starting engine...",
                            color = theme.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Vault & Model Status Sub-Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.headerBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF1E293B))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onNavigateToSettings() }
                                    .padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📁 Docs: ",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = vaultDisplayName,
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.widthIn(min = 6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.success.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${vaultStats.chunkCount} vectors",
                                        color = theme.success,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.primary.copy(alpha = 0.1f))
                                    .border(1.dp, theme.primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .clickable { showSetupWizard = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ Wizard",
                                    color = theme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF1E293B))
                        )
                    }

                    // Main Chat Stream
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(activeMessages) { _, msg ->
                            val isUser = msg.role == "USER"
                            val isSystem = msg.role == "SYSTEM"
                            val bubbleBg = if (isUser) theme.cardAlt else theme.card
                            val accentBarColor = when {
                                isUser -> if (theme.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
                                isSystem -> theme.danger
                                isCloudMode -> theme.accent
                                else -> theme.primary
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(if (isUser) 0.88f else 0.95f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bubbleBg)
                                        .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                ) {
                                    if (!isUser) {
                                        Box(
                                            modifier = Modifier
                                                .widthIn(min = 4.dp, max = 4.dp)
                                                .heightIn(min = 56.dp)
                                                .background(accentBarColor)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isUser -> "YOU"
                                                isSystem -> "SYSTEM"
                                                isCloudMode -> "REVEILA AGENT (☁️ Cloud: $activeLlmName)"
                                                else -> "REVEILA AGENT (📱 On-Device GGUF)"
                                            },
                                            color = when {
                                                isUser -> theme.accent
                                                isSystem -> theme.danger
                                                else -> theme.primary
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = msg.content,
                                            color = theme.text,
                                            fontSize = 14.sp,
                                            lineHeight = 22.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (isProcessing) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(theme.card)
                                        .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = if (isCloudMode) theme.accent else theme.primary
                                    )
                                    Text(
                                        text = if (isCloudMode) {
                                            "Querying cloud model with guardrails..."
                                        } else {
                                            "Executing on-device reasoning and vector search..."
                                        },
                                        color = theme.subText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Anchored Prompt Input Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(theme.border)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (theme.isDark) Color(0xFF090D16) else Color.White)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.card)
                                .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp, max = 120.dp),
                                enabled = !isProcessing,
                                placeholder = {
                                    Text(
                                        text = if (isCloudMode) "Ask Cloud AI ($activeLlmName)..." else "Ask On-Device Model (Offline)...",
                                        color = theme.subText,
                                        fontSize = 14.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = theme.text,
                                    unfocusedTextColor = theme.text,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )

                            val canSend = promptText.trim().isNotEmpty() && !isProcessing
                            val sendBtnColor = if (isCloudMode) theme.accent else theme.primary
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (canSend) sendBtnColor else sendBtnColor.copy(alpha = 0.4f))
                                    .clickable(enabled = canSend) { handleSendPrompt() }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SEND",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfigureCloudDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfigureCloudDialog = false
            },
            title = {
                Text(
                    text = "Cloud AI Setup Required",
                    color = theme.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Cloud AI connects to external commercial platforms (Google Gemini, OpenAI, DeepSeek, or Qwen) and requires your own API key / plan.\n\nWould you like to configure your API key now in Settings?",
                    color = theme.subText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfigureCloudDialog = false
                        onNavigateToSettings()
                    }
                ) {
                    Text(
                        text = "Configure in Settings",
                        color = theme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfigureCloudDialog = false
                    }
                ) {
                    Text(
                        text = "Stay On-Device",
                        color = theme.subText,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            containerColor = theme.card,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
