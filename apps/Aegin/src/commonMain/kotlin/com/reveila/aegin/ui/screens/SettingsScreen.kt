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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.DeviceCapabilityReport
import com.reveila.aegin.bridge.LlmProviderConfig
import com.reveila.aegin.bridge.TaskItem
import androidx.compose.foundation.layout.windowInsetsPadding
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.components.SetupWizard
import com.reveila.aegin.ui.components.VaultFolderPickerDialog
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginOrbAvatar
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenSecurityPortal: () -> Unit
) {
    val theme = LocalAeginColors.current
    val scope = rememberCoroutineScope()

    val tabs = listOf("General", "Tasks", "Security", "Advanced")
    var activeTab by remember { mutableStateOf("General") }
    var isBiometricEnabled by remember { mutableStateOf(false) }
    var isLoadingProviders by remember { mutableStateOf(true) }
    val providersList = remember { mutableStateListOf<LlmProviderConfig>() }
    val configuredMap = remember { mutableStateMapOf<String, Boolean>() }

    // Studio Tasks Authoring & Sandbox Control State
    val studioTasks = remember { mutableStateListOf<TaskItem>() }
    var tasksFolderPath by remember { mutableStateOf("") }
    var editingStudioTaskId by remember { mutableStateOf<String?>(null) }
    var studioTaskTitle by remember { mutableStateOf("") }
    var studioTaskIntent by remember { mutableStateOf("auto") }
    var studioTaskExecutionTarget by remember { mutableStateOf("auto") }
    var studioTaskPrompt by remember { mutableStateOf("") }
    var studioTaskFreq by remember { mutableStateOf("DAILY") }
    var studioTaskTime by remember { mutableStateOf("08:00") }
    var studioTaskRequiresApproval by remember { mutableStateOf(false) }

    var workerProvider by remember { mutableStateOf("On-Device Model") }
    var selectedCloudProvider by remember { mutableStateOf("Google Gemini") }
    var governanceProvider by remember { mutableStateOf("Disable") }

    var vaultFolder by remember { mutableStateOf("Documents/Reveila") }
    var showCustomVaultDialog by remember { mutableStateOf(false) }
    var vaultDocCount by remember { mutableStateOf(0) }
    var vaultChunkCount by remember { mutableStateOf(0) }
    var isVaultScanning by remember { mutableStateOf(false) }

    var isWorkerModalVisible by remember { mutableStateOf(false) }
    var isGovModalVisible by remember { mutableStateOf(false) }
    var isEditModalVisible by remember { mutableStateOf(false) }
    var editData by remember { mutableStateOf(LlmProviderConfig(name = "")) }
    var showSetupWizardModal by remember { mutableStateOf(false) }

    var isRunning by remember { mutableStateOf(false) }
    var isSuspended by remember { mutableStateOf(false) }
    var storageMountPath by remember { mutableStateOf("/data/user/0/com.reveila.aegin/files") }
    var capabilities by remember { mutableStateOf(DeviceCapabilityReport()) }

    var alertTitle by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmResetDialog by remember { mutableStateOf(false) }

    fun showAlert(title: String, message: String) {
        alertTitle = title
        alertMessage = message
    }

    LaunchedEffect(Unit) {
        isBiometricEnabled = AeginBridge.getStorageItem("use_biometrics") == "true"

        try {
            val status = AeginBridge.getEngineStatus()
            isRunning = status.initialized
            storageMountPath = status.storageMountPath
        } catch (_: Exception) {
            isRunning = true
        }

        try {
            capabilities = AeginBridge.getDeviceCapabilities()
        } catch (_: Exception) {
        }

        try {
            val v = AeginBridge.getVaultFolder()
            if (v.folder.isNotBlank()) vaultFolder = v.folder
        } catch (_: Exception) {
        }

        try {
            val s = AeginBridge.getVaultStats()
            vaultDocCount = s.documentCount
            vaultChunkCount = s.chunkCount
        } catch (_: Exception) {
        }

        try {
            val res = AeginBridge.invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
            val (parsed, wProv, gProv) = AeginBridge.parseLlmSettingsJson(res, capabilities)
            providersList.clear()
            providersList.addAll(parsed)
            providersList.add(LlmProviderConfig(name = "Custom"))
            workerProvider = wProv
            if (wProv != "On-Device Model" && wProv != "Disable" && wProv.isNotBlank()) {
                selectedCloudProvider = wProv
            }
            governanceProvider = gProv
        } catch (_: Exception) {
            val (defaults, wProv, gProv) = AeginBridge.parseLlmSettingsJson("{}", capabilities)
            providersList.clear()
            providersList.addAll(defaults)
            providersList.add(LlmProviderConfig(name = "Custom"))
            workerProvider = wProv
            if (wProv != "On-Device Model" && wProv != "Disable" && wProv.isNotBlank()) {
                selectedCloudProvider = wProv
            }
            governanceProvider = gProv
        } finally {
            isLoadingProviders = false
            for (p in providersList) {
                val isConf = try {
                    AeginBridge.invoke("LlmProviderFactory", "isProviderConfigured", listOf(p.name)).trim().equals("true", ignoreCase = true)
                } catch (_: Exception) {
                    if (p.name == "On-Device Model") true
                    else p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")
                }
                configuredMap[p.name] = isConf
            }
        }

        while (true) {
            try {
                val status = AeginBridge.getEngineStatus()
                isRunning = status.initialized
                storageMountPath = status.storageMountPath
                if (isRunning) {
                    val suspStr = AeginBridge.invoke("OrchestrationService", "isSuspended", emptyList())
                    isSuspended = suspStr.trim().equals("true", ignoreCase = true)
                    val dashboard = AeginBridge.getTasksDashboard()
                    if (dashboard.tasksDir.isNotBlank()) {
                        tasksFolderPath = dashboard.tasksDir
                    }
                    studioTasks.clear()
                    studioTasks.addAll(dashboard.tasks)
                }
            } catch (_: Exception) {
            }
            val hasRunningTask = studioTasks.any { it.status == "RUNNING" || it.status == "SUSPENDED" }
            delay(if (hasRunningTask || activeTab == "Tasks") 1200L else 4000L)
        }
    }

    suspend fun refreshStudioTasks() {
        try {
            val dashboard = AeginBridge.getTasksDashboard()
            if (dashboard.tasksDir.isNotBlank()) {
                tasksFolderPath = dashboard.tasksDir
            }
            studioTasks.clear()
            studioTasks.addAll(dashboard.tasks)
        } catch (_: Exception) {
        }
    }

    fun isProviderConfigured(pName: String): Boolean {
        if (isLoadingProviders) return false
        if (pName == "Disable" || pName == "Custom") return false
        val p = providersList.find { it.name == pName } ?: return false
        if (p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model") return true
        if (configuredMap.containsKey(pName)) return configuredMap[pName] == true
        if (p.name.startsWith("Gemma") || p.name.contains("Ollama")) {
            return p.endpoint.isNotBlank()
        }
        if (p.name == "OpenAI" || p.name == "Google Gemini" || p.name == "DeepSeek" || p.name == "Qwen" || p.name == "Anthropic") {
            return p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")
        }
        return p.endpoint.isNotBlank() && p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")
    }

    fun openEditModal(providerName: String) {
        if (providerName == "Disable" || providerName == "Custom") return
        val provider = providersList.find { it.name == providerName }
        if (provider != null) {
            val keyDisplay = if (provider.apiKey.startsWith("REF:")) "" else provider.apiKey
            val isLocal = provider.type.equals("Local", ignoreCase = true) || provider.name == "On-Device Model"
            val initialIntents = provider.intentOptions.ifEmpty {
                if (isLocal) AeginBridge.DEFAULT_LOCAL_INTENTS else AeginBridge.DEFAULT_CLOUD_INTENTS
            }
            editData = provider.copy(
                type = if (isLocal) "Local" else "Cloud",
                apiKey = if (isLocal) "" else keyDisplay,
                intentOptions = initialIntents
            )
            isEditModalVisible = true
        }
    }

    fun handleSaveProviderEdit() {
        val idx = providersList.indexOfFirst { it.name == editData.name }
        if (idx >= 0) {
            providersList[idx] = editData.copy()
        } else {
            providersList.add(editData.copy())
        }
        isEditModalVisible = false
        scope.launch {
            try {
                val configJson = AeginBridge.buildLlmSettingsJson(
                    providers = providersList,
                    workerProvider = workerProvider,
                    governanceProvider = governanceProvider
                )
                AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", configJson))
            } catch (_: Exception) {
            }
        }
    }

    fun handleSaveLLM() {
        scope.launch {
            isVaultScanning = true
            try {
                val configJson = AeginBridge.buildLlmSettingsJson(
                    providers = providersList,
                    workerProvider = workerProvider,
                    governanceProvider = governanceProvider
                )
                AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", configJson))
                AeginBridge.setStorageItem("selected_llm_provider", workerProvider)
                AeginBridge.setStorageItem(
                    "selected_llm_mode",
                    when {
                        workerProvider == "On-Device Model" -> "local"
                        workerProvider == "Google Gemini" -> "gemini"
                        workerProvider == "DeepSeek" -> "deepseek"
                        workerProvider == "Qwen" -> "qwen"
                        providersList.find { it.name == workerProvider }?.type.equals("Local", ignoreCase = true) -> "local"
                        else -> "openai"
                    }
                )
                for (p in providersList) {
                    val isConf = try {
                        AeginBridge.invoke("LlmProviderFactory", "isProviderConfigured", listOf(p.name)).trim().equals("true", ignoreCase = true)
                    } catch (_: Exception) {
                        if (p.name == "On-Device Model") true
                        else p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")
                    }
                    configuredMap[p.name] = isConf
                }
                val res = AeginBridge.setVaultFolder(vaultFolder)
                val stats = AeginBridge.getVaultStats()
                vaultDocCount = stats.documentCount
                vaultChunkCount = stats.chunkCount
                showAlert(
                    "Success",
                    "Configuration saved and embeddings database refreshed for '$vaultFolder' (${res.filesScanned} docs, ${stats.chunkCount} vectors)."
                )
            } catch (e: Exception) {
                showAlert("Error", "Failed to save configuration: ${e.message}")
            } finally {
                isVaultScanning = false
            }
        }
    }

    fun handleSaveAndScanVault(targetFolder: String = vaultFolder) {
        scope.launch {
            isVaultScanning = true
            try {
                vaultFolder = targetFolder
                val res = AeginBridge.setVaultFolder(targetFolder)
                val stats = AeginBridge.getVaultStats()
                vaultDocCount = stats.documentCount
                vaultChunkCount = stats.chunkCount
                showAlert(
                    "Embeddings Refreshed",
                    "Indexed Reference Documents Folder '$targetFolder'. Documents processed: ${res.filesScanned}, Vector chunks: ${stats.chunkCount}"
                )
            } catch (e: Exception) {
                showAlert("Indexing Error", e.message ?: "Failed to index Reference Documents Folder.")
            } finally {
                isVaultScanning = false
            }
        }
    }

    fun handleToggleSuspend(value: Boolean) {
        scope.launch {
            try {
                AeginBridge.invoke("OrchestrationService", "toggleSuspend", listOf(value.toString()))
                isSuspended = value
                showAlert(
                    if (value) "System Suspended" else "System Resumed",
                    if (value) "All background AI tasks are paused." else "System restored to normal operation."
                )
            } catch (e: Exception) {
                showAlert("Error", "Failed to toggle suspend state: ${e.message}")
            }
        }
    }

    fun toggleBiometrics(value: Boolean) {
        scope.launch {
            if (value) {
                val ok = AeginBridge.authenticateBiometric()
                isBiometricEnabled = ok
                AeginBridge.setStorageItem("use_biometrics", ok.toString())
            } else {
                isBiometricEnabled = false
                AeginBridge.setStorageItem("use_biometrics", "false")
            }
        }
    }

    Scaffold(
        containerColor = theme.bg,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.bg)
            ) {
                // Studio Header Bar
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
                            isLocalMode = workerProvider == "On-Device Model",
                            isThinking = isVaultScanning,
                            showStatusDot = true
                        )
                        Column {
                            Text(
                                text = "Studio & Vault",
                                color = theme.text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${workerProvider} • $vaultDocCount docs ($vaultChunkCount vectors)",
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
                                .clip(CircleShape)
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, CircleShape)
                                .clickable { showSetupWizardModal = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "⚡ Wizard",
                                color = theme.primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Segmented Pill Tab Bar
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
                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        val tabLabel = when (tab) {
                            "General" -> "AI & Vault"
                            "Tasks" -> "Tasks"
                            "Security" -> "Privacy"
                            else -> "Diagnostics"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) theme.primaryGradient
                                    else androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    )
                                )
                                .clickable { activeTab = tab }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) Color.White else theme.subText,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeTab == "General") {
                val isDeviceActive = workerProvider == "On-Device Model"
                val onDeviceConfig = providersList.find { it.name == "On-Device Model" }
                val cloudProviders = providersList.filter { it.name != "On-Device Model" && it.name != "Disable" && it.name != "Custom" }

                // 1. ACTIVE REASONING ENGINE (Choice Card)
                Text(
                    text = "Active Reasoning Engine",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Choose how the assistant processes queries, analyzes documents, and executes tasks.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Option A: On-Device Model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDeviceActive) theme.primary.copy(alpha = 0.12f) else theme.inputBg)
                            .border(
                                1.5.dp,
                                if (isDeviceActive) theme.primary else theme.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                workerProvider = "On-Device Model"
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isDeviceActive) "🔘" else "⚪",
                            fontSize = 16.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📱 On-Device Model",
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.success.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "100% PRIVATE • OFFLINE",
                                        color = theme.success,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Embedded GGUF running locally on device hardware. Zero internet, no subscription, no API key needed.",
                                color = theme.subText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Option B: Cloud AI Services
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isDeviceActive) theme.accent.copy(alpha = 0.12f) else theme.inputBg)
                            .border(
                                1.5.dp,
                                if (!isDeviceActive) theme.accent else theme.border,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                val target = if (selectedCloudProvider != "On-Device Model" && selectedCloudProvider.isNotBlank()) {
                                    selectedCloudProvider
                                } else {
                                    cloudProviders.firstOrNull { isProviderConfigured(it.name) }?.name
                                        ?: cloudProviders.firstOrNull()?.name
                                        ?: "Google Gemini"
                                }
                                workerProvider = target
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (!isDeviceActive) "🔘" else "⚪",
                            fontSize = 16.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "☁️ Cloud AI Services",
                                    color = theme.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.accent.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "HIGH INTELLIGENCE • BYO KEY",
                                        color = theme.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Connects to commercial providers (Gemini, OpenAI, DeepSeek, Qwen). Requires internet and your personal API key.",
                                color = theme.subText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // If Cloud is active, show the Active Cloud Provider selector row
                    if (!isDeviceActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.inputBg)
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .clickable { isWorkerModalVisible = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ACTIVE CLOUD SERVICE",
                                    color = theme.subText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = workerProvider,
                                    color = theme.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isProviderConfigured(workerProvider)) Color(0xFF22C55E) else Color(0xFFEF4444)
                                        )
                                )
                                Text("CHANGE ▼", color = theme.cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. 📱 ON-DEVICE ENGINE (EMBEDDED GGUF) CARD
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📱 On-Device Engine (Embedded GGUF)",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "The embedded llama.cpp runtime executes local GGUF models directly on this phone. No account or API key is required.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.inputBg)
                            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE GGUF MODEL",
                                color = theme.subText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = onDeviceConfig?.model?.ifBlank { capabilities.recommendedModel } ?: capabilities.recommendedModel,
                                color = theme.text,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (onDeviceConfig != null && onDeviceConfig.modelOptions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.border)
                                    .clickable {
                                        val idx = onDeviceConfig.modelOptions.indexOf(onDeviceConfig.model)
                                        val next = onDeviceConfig.modelOptions[(idx + 1) % onDeviceConfig.modelOptions.size]
                                        val updated = onDeviceConfig.copy(model = next)
                                        val pIdx = providersList.indexOfFirst { it.name == "On-Device Model" }
                                        if (pIdx >= 0) providersList[pIdx] = updated
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("CYCLE", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (onDeviceConfig != null && onDeviceConfig.intentOptions.isNotEmpty()) {
                        Text(
                            text = "Intents (${onDeviceConfig.intentOptions.size}): ${onDeviceConfig.intentOptions.joinToString(", ")}",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, theme.primary, RoundedCornerShape(8.dp))
                                .clickable { openEditModal("On-Device Model") }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "⚙️ Configure On-Device Settings",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. ☁️ CLOUD PROVIDERS (BYO API KEY) CARD
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "☁️ Cloud Providers (BYO API Key)",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add your personal API key for each commercial service you wish to use.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    cloudProviders.forEach { cp ->
                        val isConf = isProviderConfigured(cp.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.inputBg)
                                .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                                .clickable { openEditModal(cp.name) }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cp.name,
                                        color = theme.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isConf) Color(0xFF22C55E) else Color(0xFF94A3B8))
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isConf) "Model: ${cp.model.ifBlank { "default" }}" else "API Key required",
                                    color = if (isConf) theme.subText else Color(0xFFF59E0B),
                                    fontSize = 12.sp
                                )
                                if (cp.intentOptions.isNotEmpty()) {
                                    Text(
                                        text = "Intents: ${cp.intentOptions.joinToString(", ")}",
                                        color = theme.subText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isConf) theme.border else Color(0xFF0EA5E9))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (isConf) "Edit Key" else "Set Key",
                                    color = if (isConf) theme.text else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 4. GOVERNANCE & PRIVACY GUARDRAIL CARD
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Governance & Privacy Guardrail",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderSelectorRow(
                        label = "Governance Provider",
                        description = "Optional pre-processor to scan prompts for security risks and privacy leaks before sending to cloud.",
                        selectedProvider = governanceProvider,
                        isLoading = isLoadingProviders,
                        isConfigured = isProviderConfigured(governanceProvider),
                        onOpenSelectModal = { isGovModalVisible = true },
                        onOpenEditModal = { openEditModal(governanceProvider) },
                        onCustomNameChange = { governanceProvider = it }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(theme.primaryGradient)
                            .clickable { handleSaveLLM() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save AI Configuration",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reference Documents Folder",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Reference documents in this folder are indexed for AI inference. Saving a new path refreshes the embeddings database.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = vaultFolder,
                        onValueChange = { vaultFolder = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Folder Path (e.g. Documents/Reveila)", color = theme.subText) },
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isDefaultSelected = vaultFolder.equals("Documents/Reveila", ignoreCase = true) ||
                            vaultFolder.equals("Documents/Reveila Vault", ignoreCase = true) ||
                            vaultFolder.equals("Documents/ReveilaVault", ignoreCase = true)
                        val isCustomSelected = !isDefaultSelected

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (isDefaultSelected) theme.primary.copy(alpha = 0.15f) else theme.inputBg)
                                .border(
                                    1.dp,
                                    if (isDefaultSelected) theme.primary else theme.border,
                                    CircleShape
                                )
                                .clickable {
                                    vaultFolder = "Documents/Reveila"
                                    scope.launch {
                                        AeginBridge.ensureVaultDirectory("Documents/Reveila")
                                    }
                                    handleSaveAndScanVault("Documents/Reveila")
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Default",
                                color = if (isDefaultSelected) theme.primary else theme.subText,
                                fontWeight = if (isDefaultSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (isCustomSelected) theme.primary.copy(alpha = 0.15f) else theme.inputBg)
                                .border(
                                    1.dp,
                                    if (isCustomSelected) theme.primary else theme.border,
                                    CircleShape
                                )
                                .clickable {
                                    showCustomVaultDialog = true
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Custom",
                                color = if (isCustomSelected) theme.primary else theme.subText,
                                fontWeight = if (isCustomSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.inputBg)
                                .padding(12.dp)
                        ) {
                            Text("DOCUMENTS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = theme.subText)
                            Text(
                                text = "$vaultDocCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.success
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.inputBg)
                                .padding(12.dp)
                        ) {
                            Text("VECTOR CHUNKS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = theme.subText)
                            Text(
                                text = "$vaultChunkCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = theme.accent
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
                                .clip(CircleShape)
                                .background(theme.primaryGradient)
                                .clickable(enabled = !isVaultScanning) { handleSaveAndScanVault() }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVaultScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "Save & Re-Index",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(theme.inputBg)
                                .border(1.dp, theme.border, CircleShape)
                                .clickable {
                                    showSetupWizardModal = true
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Launch Wizard",
                                color = theme.text,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (activeTab == "Tasks") {
                val freqOptions = listOf("ONCE", "EVERY 15M", "HOURLY", "DAILY", "WEEKLY")
                val targetOptions = listOf(
                    "auto" to "AUTO (Smart Preprocessor)",
                    "local" to "LOCAL ONLY (Offline)",
                    "cloud" to "CLOUD (Force Cloud)"
                )
                val inProgressCount = studioTasks.count { it.status == "RUNNING" || it.status == "SUSPENDED" }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (editingStudioTaskId == null) "Create Task Definition" else "Edit Task ($editingStudioTaskId.json)",
                            color = theme.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Saved in ${tasksFolderPath.ifBlank { "$storageMountPath/tasks" }}",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (editingStudioTaskId != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.inputBg)
                                .border(1.dp, theme.border, CircleShape)
                                .clickable {
                                    editingStudioTaskId = null
                                    studioTaskTitle = ""
                                    studioTaskPrompt = ""
                                    studioTaskFreq = "DAILY"
                                    studioTaskTime = "08:00"
                                    studioTaskRequiresApproval = false
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("Cancel Edit", color = theme.subText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = studioTaskTitle,
                        onValueChange = { studioTaskTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Task Title", color = theme.subText, fontSize = 12.sp) },
                        placeholder = { Text("e.g., Daily Security Briefing", color = theme.subText, fontSize = 13.sp) },
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
                        value = studioTaskPrompt,
                        onValueChange = { studioTaskPrompt = it },
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        label = { Text("Task Prompt / Instructions", color = theme.subText, fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                "Describe the action or analysis for Reveila Core Engine to execute...",
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

                    Text(
                        text = "EXECUTION TARGET OVERRIDE (DEFAULT: AUTO)",
                        color = theme.subText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        targetOptions.forEach { (targetKey, targetLabel) ->
                            val selected = studioTaskExecutionTarget.equals(targetKey, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) theme.cyan.copy(alpha = 0.2f) else theme.inputBg)
                                    .border(1.dp, if (selected) theme.cyan else theme.border, RoundedCornerShape(8.dp))
                                    .clickable { studioTaskExecutionTarget = targetKey }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = targetLabel,
                                    color = if (selected) theme.cyan else theme.subText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text(
                        text = "RECURRENCE & SCHEDULE TIME",
                        color = theme.subText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        freqOptions.forEach { opt ->
                            val selected = studioTaskFreq.equals(opt, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) theme.primary else theme.inputBg)
                                    .border(1.dp, if (selected) theme.primary else theme.border, RoundedCornerShape(8.dp))
                                    .clickable { studioTaskFreq = opt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    color = if (selected) Color.White else theme.subText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = studioTaskTime,
                            onValueChange = { studioTaskTime = it },
                            modifier = Modifier.weight(0.45f),
                            singleLine = true,
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.inputBg)
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
                                checked = studioTaskRequiresApproval,
                                onCheckedChange = { studioTaskRequiresApproval = it }
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
                                .clip(CircleShape)
                                .background(theme.primaryGradient)
                                .clickable {
                                    val title = studioTaskTitle.trim().ifBlank { "Studio Task" }
                                    val prompt = studioTaskPrompt.trim()
                                    if (prompt.isBlank()) {
                                        showAlert("Missing Prompt", "Please enter instructions for the task.")
                                        return@clickable
                                    }
                                    scope.launch {
                                        try {
                                            if (editingStudioTaskId == null) {
                                                AeginBridge.createTask(
                                                    title = title,
                                                    prompt = prompt,
                                                    frequency = studioTaskFreq.lowercase(),
                                                    scheduleTime = studioTaskTime.ifBlank { "08:00" },
                                                    requiresApproval = studioTaskRequiresApproval,
                                                    intent = studioTaskIntent,
                                                    executionTarget = studioTaskExecutionTarget
                                                )
                                                showAlert("Task Created", "Saved '$title' to tasks folder.")
                                            } else {
                                                AeginBridge.updateTask(
                                                    taskId = editingStudioTaskId!!,
                                                    title = title,
                                                    prompt = prompt,
                                                    frequency = studioTaskFreq.lowercase(),
                                                    scheduleTime = studioTaskTime.ifBlank { "08:00" },
                                                    enabled = true,
                                                    intent = studioTaskIntent,
                                                    requiresApproval = studioTaskRequiresApproval,
                                                    executionTarget = studioTaskExecutionTarget
                                                )
                                                showAlert("Task Updated", "Updated '$title' in tasks folder.")
                                            }
                                            editingStudioTaskId = null
                                            studioTaskTitle = ""
                                            studioTaskIntent = "auto"
                                            studioTaskExecutionTarget = "auto"
                                            studioTaskPrompt = ""
                                            refreshStudioTasks()
                                        } catch (e: Exception) {
                                            showAlert("Error", "Failed to save task: ${e.message}")
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (editingStudioTaskId == null) "💾 Save to Tasks Folder" else "💾 Update Task File",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }

                        if (editingStudioTaskId == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(theme.cyan.copy(alpha = 0.18f))
                                    .border(1.dp, theme.cyan, CircleShape)
                                    .clickable {
                                        val title = studioTaskTitle.trim().ifBlank { "Studio Task" }
                                        val prompt = studioTaskPrompt.trim()
                                        if (prompt.isBlank()) {
                                            showAlert("Missing Prompt", "Please enter instructions for the task.")
                                            return@clickable
                                        }
                                        scope.launch {
                                            try {
                                                val createdRaw = AeginBridge.createTask(
                                                    title = title,
                                                    prompt = prompt,
                                                    frequency = studioTaskFreq.lowercase(),
                                                    scheduleTime = studioTaskTime.ifBlank { "08:00" },
                                                    requiresApproval = false,
                                                    intent = studioTaskIntent,
                                                    executionTarget = studioTaskExecutionTarget
                                                )
                                                studioTaskTitle = ""
                                                studioTaskIntent = "auto"
                                                studioTaskExecutionTarget = "auto"
                                                studioTaskPrompt = ""
                                                refreshStudioTasks()
                                                val createdTask = studioTasks.firstOrNull { it.title == title }
                                                if (createdTask != null) {
                                                    AeginBridge.runTaskNow(createdTask.taskId)
                                                    refreshStudioTasks()
                                                } else if (createdRaw.contains("taskId")) {
                                                    refreshStudioTasks()
                                                }
                                            } catch (e: Exception) {
                                                showAlert("Error", "Failed to launch task: ${e.message}")
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚡ Save & Run Sandbox",
                                    color = theme.cyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Active Sandboxes & Tasks Folder Registry
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Registered Tasks (${studioTasks.size}) • $inProgressCount in sandbox",
                        color = theme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (inProgressCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(theme.danger)
                                .clickable {
                                    scope.launch {
                                        AeginBridge.setGlobalKillSwitch(true)
                                        refreshStudioTasks()
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⏹ Kill All Sandboxes",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                if (studioTasks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(theme.card)
                            .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "No task definitions found in tasks folder.",
                            color = theme.subText,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    studioTasks.forEach { task ->
                        val isRunningTask = task.status == "RUNNING"
                        val isSuspendedTask = task.status == "SUSPENDED"
                        val isInProgress = isRunningTask || isSuspendedTask
                        val badgeColor = when (task.status) {
                            "RUNNING" -> theme.cyan
                            "SUSPENDED", "WAITING_APPROVAL" -> theme.warning
                            "ACTIVE" -> theme.primary
                            "COMPLETED" -> theme.success
                            "KILLED", "FAILED" -> theme.danger
                            else -> theme.subText
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.card)
                                .border(
                                    width = 1.dp,
                                    color = when {
                                        isRunningTask -> theme.cyan.copy(alpha = 0.7f)
                                        isSuspendedTask -> theme.warning.copy(alpha = 0.7f)
                                        else -> theme.border
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = task.title,
                                        color = theme.text,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${task.fileName.ifBlank { "${task.taskId}.json" }} • target=${task.executionTarget} • ${task.frequency.uppercase()} @ ${task.scheduleTime}",
                                        color = theme.subText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val isCloudRoute = task.routeTarget.equals("CLOUD", ignoreCase = true)
                                    val routeColor = if (isCloudRoute) theme.cyan else theme.success
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(routeColor.copy(alpha = 0.16f))
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
                                            .background(badgeColor.copy(alpha = 0.16f))
                                            .padding(horizontal = 9.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = task.status,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Text(
                                text = task.prompt,
                                color = theme.subText,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (task.routingReason.isNotBlank()) {
                                Text(
                                    text = task.routingReason,
                                    color = theme.subText,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isInProgress) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isRunningTask) theme.cyan.copy(alpha = 0.12f)
                                            else theme.warning.copy(alpha = 0.12f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isRunningTask) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = theme.cyan
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (task.sandboxId.isNotBlank()) {
                                            Text(
                                                text = "SANDBOX • ${task.sandboxId}",
                                                color = if (isRunningTask) theme.cyan else theme.warning,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                        Text(
                                            text = task.currentStep.ifBlank { "Running in isolated sandbox..." },
                                            color = theme.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Per-task Sandbox & Lifecycle Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isInProgress) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (isRunningTask) theme.warning.copy(alpha = 0.18f)
                                                else theme.success.copy(alpha = 0.18f)
                                            )
                                            .border(
                                                1.dp,
                                                if (isRunningTask) theme.warning else theme.success,
                                                CircleShape
                                            )
                                            .clickable {
                                                scope.launch {
                                                    if (isRunningTask) {
                                                        AeginBridge.pauseTask(task.taskId)
                                                    } else {
                                                        AeginBridge.resumeTask(task.taskId)
                                                    }
                                                    refreshStudioTasks()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isRunningTask) "⏸ Suspend" else "▶ Resume",
                                            color = if (isRunningTask) theme.warning else theme.success,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.danger.copy(alpha = 0.18f))
                                            .border(1.dp, theme.danger, CircleShape)
                                            .clickable {
                                                scope.launch {
                                                    AeginBridge.killTask(task.taskId)
                                                    refreshStudioTasks()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "⏹ Kill",
                                            color = theme.danger,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.primaryGradient)
                                            .clickable {
                                                scope.launch {
                                                    AeginBridge.runTaskNow(task.taskId)
                                                    refreshStudioTasks()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "▶ Run in Sandbox",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .border(1.dp, theme.border, CircleShape)
                                            .clickable {
                                                editingStudioTaskId = task.taskId
                                                studioTaskTitle = task.title
                                                studioTaskIntent = task.intent.ifBlank { "auto" }
                                                studioTaskExecutionTarget = task.executionTarget.ifBlank { "auto" }
                                                studioTaskPrompt = task.prompt
                                                studioTaskFreq = task.frequency.uppercase()
                                                studioTaskTime = task.scheduleTime
                                                studioTaskRequiresApproval = task.requiresApproval
                                            }
                                            .padding(horizontal = 11.dp, vertical = 6.dp)
                                    ) {
                                        Text("Edit", color = theme.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .border(1.dp, theme.border, CircleShape)
                                            .clickable {
                                                scope.launch {
                                                    if (task.status == "ACTIVE") {
                                                        AeginBridge.pauseTask(task.taskId)
                                                    } else {
                                                        AeginBridge.resumeTask(task.taskId)
                                                    }
                                                    refreshStudioTasks()
                                                }
                                            }
                                            .padding(horizontal = 11.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (task.status == "ACTIVE") "Pause" else "Resume",
                                            color = if (task.status == "ACTIVE") theme.warning else theme.success,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(theme.inputBg)
                                            .clickable {
                                                scope.launch {
                                                    AeginBridge.deleteTask(task.taskId)
                                                    refreshStudioTasks()
                                                }
                                            }
                                            .padding(horizontal = 11.dp, vertical = 6.dp)
                                    ) {
                                        Text("Delete", color = theme.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeTab == "Security") {
                Text(
                    text = "Security",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.warning, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Suspend Execution",
                            color = theme.warning,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pause background AI tasks and indexing.",
                            color = theme.subText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Switch(
                        checked = isSuspended,
                        onCheckedChange = { handleToggleSuspend(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.warning
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Biometric Lock",
                            color = theme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Require biometric authentication on startup.",
                            color = theme.subText,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { toggleBiometrics(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.cyan
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Security Portal",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Inspect authorization prompts and sandbox permissions.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.accent, RoundedCornerShape(8.dp))
                            .clickable { onOpenSecurityPortal() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OPEN SECURITY PORTAL",
                            color = theme.accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (activeTab == "Advanced") {
                Text(
                    text = "Advanced Diagnostics",
                    color = theme.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "On-Device GGUF Hardware Preflight & Staging",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tier: ${capabilities.hardwareTier} • Recommended: ${capabilities.recommendedModel}",
                        color = if (capabilities.canRunLocalGguf) theme.success else theme.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "RAM: ${capabilities.totalRamGb} GB total (${capabilities.availableRamGb} GB free) | Disk: ${capabilities.freeDiskGb} GB free",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "ABI: ${capabilities.cpuAbi} | DotProd: ${capabilities.supportsSimdDotProd} | FP16: ${capabilities.supportsFp16} | Vulkan 1.1+: ${capabilities.supportsVulkanCompute}",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "GGUF Staging Directory (No-Backup mmap):",
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = capabilities.modelsStagingPath.ifBlank { "$storageMountPath/models" },
                        color = theme.subText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "System Storage Mount Path",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = storageMountPath,
                        color = theme.subText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.border, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Reference Documents Index Sync",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Force-trigger an explicit re-indexing pass across the active Reference Documents Folder to refresh vector embeddings.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(theme.cloudGradient)
                            .clickable {
                                scope.launch {
                                    try {
                                        AeginBridge.invoke("OrchestrationService", "triggerVaultScan", emptyList())
                                        AeginBridge.scanVault()
                                        val stats = AeginBridge.getVaultStats()
                                        vaultDocCount = stats.documentCount
                                        vaultChunkCount = stats.chunkCount
                                        showAlert("Index Sync", "Reference Documents scan and embedding refresh completed successfully.")
                                    } catch (e: Exception) {
                                        showAlert("Scan Failed", e.message ?: "Error triggering scan")
                                    }
                                }
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Force Re-Scan Now",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.card)
                        .border(1.dp, theme.danger, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Reset Application Environment",
                        color = theme.danger,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wipes all local state, storage parameters, and keys. This operation cannot be rolled back.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.danger)
                            .clickable { showConfirmResetDialog = true }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "RESET EVERYTHING",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
            }
        }
    }

    // Cloud Provider Selection Modal
    if (isWorkerModalVisible) {
        val cloudNames = providersList.filter { it.name != "On-Device Model" && it.name != "Disable" }.map { it.name }
        ProviderListDialog(
            title = "Select Cloud AI Service",
            items = cloudNames,
            onSelect = {
                workerProvider = it
                selectedCloudProvider = it
                isWorkerModalVisible = false
            },
            onCancel = { isWorkerModalVisible = false }
        )
    }

    // Governance Provider Selection Modal
    if (isGovModalVisible) {
        ProviderListDialog(
            title = "Select Governance Provider",
            items = listOf("Disable") + providersList.map { it.name },
            onSelect = {
                governanceProvider = it
                isGovModalVisible = false
            },
            onCancel = { isGovModalVisible = false }
        )
    }

    // Edit Provider Modal
    if (isEditModalVisible) {
        val isDeviceModel = editData.name == "On-Device Model"
        val isCustom = editData.name == "Custom"
        val isCloudService = !isDeviceModel && !isCustom
        val isLocalProvider = if (isDeviceModel) true else if (isCloudService) false else editData.type.equals("Local", ignoreCase = true)

        Dialog(onDismissRequest = { isEditModalVisible = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.card)
                    .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Title
                Text(
                    text = if (isDeviceModel) "📱 On-Device Model" else if (isCustom) "🛠️ Custom Provider" else "☁️ Configure ${editData.name}",
                    color = theme.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                // Educational / Status Banner
                if (isDeviceModel) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.primary.copy(alpha = 0.12f))
                            .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "🔒 100% Private & Offline (Embedded GGUF)",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Runs directly on your device using the local llama engine. No internet connection, account subscription, or API key is required.",
                            color = theme.subText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else if (isCloudService) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.accent.copy(alpha = 0.12f))
                            .border(1.dp, theme.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "⚡ Commercial Cloud LLM Service",
                            color = theme.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Requests are sent over the internet to ${editData.name}'s servers. Requires an active account and your personal API key.",
                            color = theme.subText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    // Custom Provider: Show selector
                    Text("PROVIDER TYPE", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isCustomLocal = editData.type.equals("Local", ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isCustomLocal) Color(0xFF0F172A) else theme.inputBg)
                                .border(1.dp, if (!isCustomLocal) Color(0xFF0F172A) else theme.border, RoundedCornerShape(8.dp))
                                .clickable { editData = editData.copy(type = "Cloud") }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("☁️ Cloud API", color = if (!isCustomLocal) Color.White else theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCustomLocal) Color(0xFF0F172A) else theme.inputBg)
                                .border(1.dp, if (isCustomLocal) Color(0xFF0F172A) else theme.border, RoundedCornerShape(8.dp))
                                .clickable { editData = editData.copy(type = "Local", apiKey = "") }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💻 Local / Self-Hosted", color = if (isCustomLocal) Color.White else theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // If Cloud, show API Key with helper hint
                if (!isLocalProvider) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("API KEY", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        OutlinedTextField(
                            value = editData.apiKey,
                            onValueChange = { editData = editData.copy(apiKey = it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Paste your API key here", color = theme.subText, fontSize = 13.sp) },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        val keyHint = when (editData.name) {
                            "Google Gemini" -> "Get an API key from Google AI Studio (aistudio.google.com)"
                            "OpenAI" -> "Get an API key from OpenAI Platform (platform.openai.com)"
                            "DeepSeek" -> "Get an API key from DeepSeek Platform (platform.deepseek.com)"
                            "Qwen" -> "Get an API key from Alibaba Cloud DashScope (dashscope.aliyun.com)"
                            else -> "Enter your commercial provider API key"
                        }
                        Text(
                            text = keyHint,
                            color = theme.subText,
                            fontSize = 10.sp
                        )
                    }
                }

                // Model field with cycle button
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isDeviceModel) "EMBEDDED GGUF MODEL" else "MODEL NAME",
                        color = theme.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editData.model,
                            onValueChange = { editData = editData.copy(model = it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (editData.modelOptions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.border)
                                    .clickable {
                                        val idx = editData.modelOptions.indexOf(editData.model)
                                        val next = editData.modelOptions[(idx + 1) % editData.modelOptions.size]
                                        editData = editData.copy(model = next)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 12.dp)
                            ) {
                                Text("CYCLE", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Endpoint (Advanced)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isDeviceModel) "INTERNAL ENGINE ENDPOINT" else "ENDPOINT URL",
                        color = theme.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    OutlinedTextField(
                        value = editData.endpoint,
                        onValueChange = { editData = editData.copy(endpoint = it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Multi-Select 6 Predefined Task Intents ("Intent.Options" in llm.json)
                val intentMetadata = listOf(
                    "LOCAL_TEXT_TRANSFORM" to "Summarize, rewrite, or format local text",
                    "LOCAL_STRUCTURED_EXTRACT" to "Extract entities, tags, or classify fields",
                    "LOCAL_DEVICE_ACTION" to "Single-step local action or vault query",
                    "CLOUD_LIVE_RETRIEVAL" to "Latest news, web search, weather, live prices",
                    "CLOUD_DEEP_REASONING" to "Multi-step planning, coding, deep analysis",
                    "CLOUD_MULTI_STEP_AGENT" to "Autonomous multi-turn tool orchestration"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SUPPORTED TASK INTENTS (Intent.Options)",
                        color = theme.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Select one or more of the 6 predefined intents this LLM handles. On-Device Model has higher preference when both match.",
                        color = theme.subText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    intentMetadata.forEach { (intentKey, intentDesc) ->
                        val isSelected = editData.intentOptions.any { it.equals(intentKey, ignoreCase = true) }
                        val isCloudIntent = intentKey.startsWith("CLOUD_")
                        val accentColor = if (isCloudIntent) theme.cyan else theme.success

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) accentColor.copy(alpha = 0.14f)
                                    else theme.inputBg
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) accentColor else theme.border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val updated = if (isSelected) {
                                        editData.intentOptions.filterNot { it.equals(intentKey, ignoreCase = true) }
                                    } else {
                                        editData.intentOptions + intentKey
                                    }
                                    editData = editData.copy(intentOptions = updated)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = intentKey,
                                    color = if (isSelected) accentColor else theme.text,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = intentDesc,
                                    color = theme.subText,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (isSelected) "✓" else "+",
                                color = if (isSelected) accentColor else theme.subText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Save / Cancel Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(theme.primaryGradient)
                            .clickable { handleSaveProviderEdit() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .border(1.dp, theme.border, CircleShape)
                            .clickable { isEditModalVisible = false }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (isCustom) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.danger, RoundedCornerShape(8.dp))
                            .clickable { showConfirmDeleteDialog = true }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Delete Custom Provider", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete ${editData.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        providersList.removeAll { it.name == editData.name }
                        if (workerProvider == editData.name) {
                            workerProvider = providersList.firstOrNull()?.name ?: "On-Device Model"
                        }
                        if (governanceProvider == editData.name) {
                            governanceProvider = "Disable"
                        }
                        showConfirmDeleteDialog = false
                        isEditModalVisible = false
                    }
                ) {
                    Text("Delete", color = theme.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmResetDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmResetDialog = false },
            title = { Text("Confirm Destructive Reset") },
            text = {
                Text("Are you absolutely sure you want to reset the environment? All configuration parameters will clear.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmResetDialog = false
                        scope.launch {
                            try {
                                AeginBridge.invoke("OrchestrationService", "resetApplication", emptyList())
                                AeginBridge.invoke("KnowledgeVault", "clear", emptyList())
                                AeginBridge.clearStorage()
                                vaultDocCount = 0
                                vaultChunkCount = 0
                                showAlert(
                                    "Reset Complete",
                                    "Application reset complete. Configuration parameters have been cleared."
                                )
                            } catch (e: Exception) {
                                showAlert("Reset Warning", e.message ?: "Reset executed.")
                            }
                        }
                    }
                ) {
                    Text("Reset Platform", color = theme.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (alertTitle != null && alertMessage != null) {
        AlertDialog(
            onDismissRequest = {
                alertTitle = null
                alertMessage = null
            },
            title = { Text(alertTitle ?: "") },
            text = { Text(alertMessage ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertTitle = null
                        alertMessage = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    VaultFolderPickerDialog(
        visible = showCustomVaultDialog,
        initialFolderPath = vaultFolder,
        onDismiss = { showCustomVaultDialog = false },
        onFolderSelected = { chosenPath ->
            vaultFolder = chosenPath
            showCustomVaultDialog = false
            handleSaveAndScanVault(chosenPath)
        }
    )

    if (showSetupWizardModal) {
        SetupWizard(
            visible = true,
            onComplete = {
                showSetupWizardModal = false
                scope.launch {
                    try {
                        val v = AeginBridge.getVaultFolder()
                        if (v.folder.isNotBlank()) vaultFolder = v.folder
                        val s = AeginBridge.getVaultStats()
                        vaultDocCount = s.documentCount
                        vaultChunkCount = s.chunkCount
                    } catch (_: Exception) {
                    }
                }
            },
            onCancel = {
                showSetupWizardModal = false
            }
        )
    }

    PlatformBackHandler(enabled = !showSetupWizardModal) {
        when {
            alertTitle != null -> {
                alertTitle = null
                alertMessage = null
            }
            showConfirmDeleteDialog -> showConfirmDeleteDialog = false
            showConfirmResetDialog -> showConfirmResetDialog = false
            showCustomVaultDialog -> showCustomVaultDialog = false
            isEditModalVisible -> isEditModalVisible = false
            isWorkerModalVisible -> isWorkerModalVisible = false
            isGovModalVisible -> isGovModalVisible = false
            else -> onClose()
        }
    }
}

@Composable
private fun ProviderSelectorRow(
    label: String,
    description: String,
    selectedProvider: String,
    isLoading: Boolean,
    isConfigured: Boolean,
    onOpenSelectModal: () -> Unit,
    onOpenEditModal: () -> Unit,
    onCustomNameChange: (String) -> Unit
) {
    val theme = LocalAeginColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            color = theme.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = description,
            color = theme.subText,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedProvider == "Custom") {
                OutlinedTextField(
                    value = "",
                    onValueChange = onCustomNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Enter custom provider name") },
                    trailingIcon = {
                        Text(
                            text = "▼",
                            modifier = Modifier.clickable { onOpenSelectModal() }.padding(8.dp),
                            color = theme.subText
                        )
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.inputBg)
                        .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                        .clickable { onOpenSelectModal() }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedProvider, color = theme.text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("▼", color = theme.subText, fontSize = 12.sp)
                }
            }

            if (selectedProvider != "Disable" && selectedProvider != "Custom") {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isLoading -> Color(0xFF94A3B8)
                                isConfigured -> Color(0xFF22C55E)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0EA5E9))
                        .clickable { onOpenEditModal() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProviderListDialog(
    title: String,
    items: List<String>,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit
) {
    val theme = LocalAeginColors.current
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.card)
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = theme.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(item) }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(item, color = theme.text, fontSize = 14.sp)
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                    .clickable { onCancel() }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
