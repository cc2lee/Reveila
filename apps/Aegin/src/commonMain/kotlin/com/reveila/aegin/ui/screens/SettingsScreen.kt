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
import androidx.compose.material3.LinearProgressIndicator
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
import com.reveila.aegin.bridge.LocalModelCheckResult
import com.reveila.aegin.bridge.LocalModelDownloadState
import com.reveila.aegin.bridge.TaskItem
import androidx.compose.foundation.layout.windowInsetsPadding
import com.reveila.aegin.ui.PlatformBackHandler
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
    val testedMap = remember { mutableStateMapOf<String, Boolean>() }

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

    var workerProvider by remember { mutableStateOf("Auto") }
    var selectedModelName by remember { mutableStateOf("On-Device Model") }
    var governanceProvider by remember { mutableStateOf("Disable") }
    var assistantProvider by remember { mutableStateOf("") }

    var vaultFolder by remember { mutableStateOf("Documents/Reveila") }
    var showCustomVaultDialog by remember { mutableStateOf(false) }
    var vaultDocCount by remember { mutableStateOf(0) }
    var vaultChunkCount by remember { mutableStateOf(0) }
    var isVaultScanning by remember { mutableStateOf(false) }

    var isWorkerModalVisible by remember { mutableStateOf(false) }
    var isGovModalVisible by remember { mutableStateOf(false) }
    var isModelCatalogModalVisible by remember { mutableStateOf(false) }
    var isEditModalVisible by remember { mutableStateOf(false) }
    var isAddingNewProvider by remember { mutableStateOf(false) }
    var editData by remember { mutableStateOf(LlmProviderConfig(name = "")) }
    var editOriginalApiKey by remember { mutableStateOf("") }
    var isTestingEditConnection by remember { mutableStateOf(false) }
    var editTestFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var testingModelFromCard by remember { mutableStateOf<String?>(null) }

    var isRunning by remember { mutableStateOf(false) }
    var isSuspended by remember { mutableStateOf(false) }
    var storageMountPath by remember { mutableStateOf("/data/user/0/com.reveila.aegin/files") }
    var capabilities by remember { mutableStateOf(DeviceCapabilityReport()) }

    var alertTitle by remember { mutableStateOf<String?>(null) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var showConfirmResetDialog by remember { mutableStateOf(false) }

    val modelDownloadState by AeginBridge.observeLocalModelDownloadState().collectAsState()
    var localModelCheck by remember { mutableStateOf<LocalModelCheckResult?>(null) }
    var pendingModelDownloadPrompt by remember { mutableStateOf<LocalModelCheckResult?>(null) }

    fun showAlert(title: String, message: String) {
        alertTitle = title
        alertMessage = message
    }

    fun activeLocalModelName(): String {
        val modelName = providersList.find { it.name == "On-Device Model" }?.model.orEmpty()
        return modelName.ifBlank { capabilities.recommendedModel }
    }

    fun refreshLocalModelCheckAndPrompt(promptIfMissing: Boolean) {
        scope.launch {
            try {
                val res = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                localModelCheck = res
                val dlActive = modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING"
                if (promptIfMissing && !res.existsLocally && !dlActive) {
                    pendingModelDownloadPrompt = res
                }
            } catch (_: Exception) {
            }
        }
    }

    fun startLocalModelDownload(modelName: String) {
        scope.launch {
            val ok = try {
                AeginBridge.downloadLocalModel(modelName)
            } catch (e: Exception) {
                showAlert("Model Download Failed", e.message ?: "Unable to download $modelName.")
                false
            }
            try {
                localModelCheck = AeginBridge.checkLocalModelAvailability(modelName)
                capabilities = AeginBridge.getDeviceCapabilities()
            } catch (_: Exception) {
            }
            if (!ok) {
                val msg = AeginBridge.observeLocalModelDownloadState().value.message
                if (msg.isNotBlank()) {
                    showAlert("Model Download Failed", msg)
                }
            }
        }
    }

    suspend fun persistSettingsPreservingAssistant() {
        try {
            val configJson = AeginBridge.buildLlmSettingsJson(
                providers = providersList,
                workerProvider = workerProvider,
                governanceProvider = governanceProvider,
                assistantProvider = assistantProvider.takeIf { it.isNotBlank() }
            )
            AeginBridge.invoke("LlmProviderFactory", "saveSettings", listOf("llm.json", configJson))
        } catch (_: Exception) {
        }
    }

    suspend fun refreshConfiguredAndTestedStates() {
        for (p in providersList) {
            val isLocal = p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model"
            val isConf = if (isLocal) {
                (localModelCheck?.existsLocally == true) ||
                    ((p.endpoint.startsWith("http://") || p.endpoint.startsWith("https://")) && p.model.isNotBlank())
            } else {
                try {
                    AeginBridge.invoke("LlmProviderFactory", "isProviderConfigured", listOf(p.name))
                        .trim().equals("true", ignoreCase = true)
                } catch (_: Exception) {
                    if (p.type.equals("Self-Hosted", ignoreCase = true)) {
                        p.endpoint.isNotBlank() && p.model.isNotBlank()
                    } else {
                        p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:") && p.endpoint.isNotBlank()
                    }
                }
            }
            configuredMap[p.name] = isConf

            val backendTested = try {
                AeginBridge.invoke("LlmProviderFactory", "isProviderTested", listOf(p.name))
                    .trim().equals("true", ignoreCase = true)
            } catch (_: Exception) {
                p.tested
            }
            testedMap[p.name] = isConf && if (isLocal) {
                (backendTested || p.tested) && (localModelCheck?.existsLocally == true || p.endpoint.startsWith("http"))
            } else {
                backendTested
            }
        }
    }

    suspend fun runConnectionTestAndPersist(providerName: String, showFeedbackDialog: Boolean): Boolean {
        val target = providersList.find { it.name.equals(providerName, ignoreCase = true) } ?: return false
        testingModelFromCard = target.name
        return try {
            val result = AeginBridge.testProviderConnection(target)
            val idx = providersList.indexOfFirst { it.name.equals(target.name, ignoreCase = true) }
            if (idx >= 0) {
                providersList[idx] = providersList[idx].copy(
                    tested = result.ok,
                    testedFingerprint = if (result.ok) result.fingerprint else ""
                )
            }
            if (result.ok) {
                configuredMap[target.name] = true
                testedMap[target.name] = true
            } else {
                testedMap[target.name] = false
                if (workerProvider.equals(target.name, ignoreCase = true)) {
                    workerProvider = "Auto"
                }
                if (governanceProvider.equals(target.name, ignoreCase = true)) {
                    governanceProvider = "Disable"
                }
            }
            persistSettingsPreservingAssistant()
            refreshConfiguredAndTestedStates()
            if (showFeedbackDialog) {
                showAlert(
                    if (result.ok) "Connection Verified" else "Connection Test Failed",
                    result.message
                )
            }
            result.ok
        } finally {
            testingModelFromCard = null
        }
    }

    LaunchedEffect(modelDownloadState.status) {
        if (modelDownloadState.status == "COMPLETED" || modelDownloadState.status == "FAILED") {
            try {
                localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                capabilities = AeginBridge.getDeviceCapabilities()
                if (modelDownloadState.status == "COMPLETED" && localModelCheck?.existsLocally == true) {
                    runConnectionTestAndPersist("On-Device Model", showFeedbackDialog = false)
                } else {
                    refreshConfiguredAndTestedStates()
                }
            } catch (_: Exception) {
            }
        }
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
            providersList.addAll(parsed.filter { it.name != "Custom" })
            workerProvider = wProv
            governanceProvider = gProv
            assistantProvider = AeginBridge.extractAssistantLlm(res)
            selectedModelName = if (wProv != "Auto" && wProv != "Disable" && providersList.any { it.name == wProv }) {
                wProv
            } else {
                providersList.firstOrNull()?.name ?: "On-Device Model"
            }
        } catch (_: Exception) {
            val (defaults, wProv, gProv) = AeginBridge.parseLlmSettingsJson("{}", capabilities)
            providersList.clear()
            providersList.addAll(defaults.filter { it.name != "Custom" })
            workerProvider = wProv
            governanceProvider = gProv
            selectedModelName = providersList.firstOrNull()?.name ?: "On-Device Model"
        } finally {
            isLoadingProviders = false
            try {
                localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
            } catch (_: Exception) {
            }
            refreshConfiguredAndTestedStates()
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
        if (pName == "Auto") return true
        if (pName == "Disable" || pName == "Custom" || pName == "+ Add New Provider") return false
        val p = providersList.find { it.name == pName } ?: return false
        if (p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model") {
            return (localModelCheck?.existsLocally == true) ||
                ((p.endpoint.startsWith("http://") || p.endpoint.startsWith("https://")) && p.model.isNotBlank())
        }
        if (p.type.equals("Self-Hosted", ignoreCase = true)) {
            return p.endpoint.isNotBlank() && p.model.isNotBlank()
        }
        if (configuredMap.containsKey(pName)) return configuredMap[pName] == true
        return p.endpoint.isNotBlank() && p.model.isNotBlank() && p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")
    }

    fun isProviderTested(pName: String): Boolean {
        if (isLoadingProviders) return false
        if (pName == "Auto") return true
        if (!isProviderConfigured(pName)) return false
        return testedMap[pName] == true
    }

    fun eligibleCandidateNames(): List<String> =
        providersList.filter { it.name != "Disable" && it.name != "Custom" && isProviderTested(it.name) }.map { it.name }

    fun openAddNewProviderModal() {
        isAddingNewProvider = true
        editOriginalApiKey = ""
        editTestFeedback = null
        editData = LlmProviderConfig(
            name = "",
            type = "Self-Hosted",
            provider = "OpenAI",
            endpoint = "http://localhost:11434/v1/chat/completions",
            model = "llama3.2",
            modelOptions = emptyList(),
            temperature = 0.7,
            apiKey = "",
            intentOptions = AeginBridge.DEFAULT_LOCAL_INTENTS,
            tested = false,
            testedFingerprint = ""
        )
        isEditModalVisible = true
    }

    fun openEditModal(providerName: String) {
        if (providerName == "Disable" || providerName == "Auto" || providerName == "Custom") return
        val provider = providersList.find { it.name == providerName }
        if (provider != null) {
            isAddingNewProvider = false
            editOriginalApiKey = provider.apiKey
            val keyDisplay = if (provider.apiKey.startsWith("REF:")) "" else provider.apiKey
            val isLocal = provider.type.equals("Local", ignoreCase = true) || provider.name == "On-Device Model"
            val isSelfHosted = !isLocal && provider.type.equals("Self-Hosted", ignoreCase = true)
            val resolvedType = when {
                isLocal -> "Local"
                isSelfHosted -> "Self-Hosted"
                else -> "Cloud"
            }
            val initialIntents = provider.intentOptions.ifEmpty {
                if (isLocal || isSelfHosted) AeginBridge.DEFAULT_LOCAL_INTENTS else AeginBridge.DEFAULT_CLOUD_INTENTS
            }
            val currentlyTested = isProviderTested(provider.name)
            editTestFeedback = if (currentlyTested) {
                true to "Verified & ready for Worker / Governance."
            } else {
                null
            }
            editData = provider.copy(
                type = resolvedType,
                apiKey = if (isLocal) "" else keyDisplay,
                intentOptions = initialIntents,
                tested = currentlyTested,
                testedFingerprint = if (currentlyTested) provider.testedFingerprint else ""
            )
            isEditModalVisible = true
        }
    }

    fun effectiveDialogConfig(): LlmProviderConfig {
        val isLocal = editData.type.equals("Local", ignoreCase = true) || editData.name.trim() == "On-Device Model"
        val isSelfHosted = !isLocal && editData.type.equals("Self-Hosted", ignoreCase = true)
        val effectiveApiKey = when {
            isLocal -> ""
            editData.apiKey.isNotBlank() -> editData.apiKey.trim()
            editOriginalApiKey.startsWith("REF:") -> editOriginalApiKey
            else -> ""
        }
        return editData.copy(
            name = editData.name.trim(),
            type = when {
                isLocal -> "Local"
                isSelfHosted -> "Self-Hosted"
                else -> "Cloud"
            },
            provider = when {
                isLocal -> ""
                isSelfHosted -> "OpenAI"
                else -> editData.provider.ifBlank { editData.name.trim() }
            },
            model = editData.model.trim(),
            endpoint = editData.endpoint.trim(),
            apiKey = effectiveApiKey
        )
    }

    fun handleTestEditConnection() {
        val candidate = effectiveDialogConfig()
        if (candidate.name.isBlank()) {
            editTestFeedback = false to "Please enter a Provider Name before testing."
            return
        }
        scope.launch {
            isTestingEditConnection = true
            editTestFeedback = null
            try {
                if (candidate.type == "Local") {
                    localModelCheck = AeginBridge.checkLocalModelAvailability(candidate.model.ifBlank { capabilities.recommendedModel })
                }
                val result = AeginBridge.testProviderConnection(candidate)
                editTestFeedback = result.ok to result.message
                editData = editData.copy(
                    tested = result.ok,
                    testedFingerprint = if (result.ok) result.fingerprint else ""
                )
            } finally {
                isTestingEditConnection = false
            }
        }
    }

    fun handleSaveProviderEdit() {
        val candidate = effectiveDialogConfig()
        if (candidate.name.isBlank()) {
            showAlert("Validation Error", "Please enter a Provider Name before saving.")
            return
        }
        if (isAddingNewProvider && providersList.any { it.name.equals(candidate.name, ignoreCase = true) }) {
            showAlert("Duplicate Provider", "A provider named '${candidate.name}' already exists.")
            return
        }
        val mergedOptions = if (candidate.model.isNotBlank() && candidate.modelOptions.none { it == candidate.model }) {
            candidate.modelOptions + candidate.model
        } else {
            candidate.modelOptions
        }
        val savedEdit = candidate.copy(modelOptions = mergedOptions)
        val idx = providersList.indexOfFirst { it.name.equals(savedEdit.name, ignoreCase = true) }
        if (idx >= 0) {
            providersList[idx] = savedEdit
        } else {
            providersList.add(savedEdit)
        }
        selectedModelName = savedEdit.name
        isAddingNewProvider = false
        isEditModalVisible = false
        scope.launch {
            try {
                if (savedEdit.name == "On-Device Model") {
                    val res = AeginBridge.checkLocalModelAvailability(savedEdit.model.ifBlank { capabilities.recommendedModel })
                    localModelCheck = res
                    val dlActive = modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING"
                    if (!res.existsLocally && !dlActive && !savedEdit.endpoint.startsWith("http")) {
                        pendingModelDownloadPrompt = res
                    }
                }
                assistantProvider = workerProvider
                AeginBridge.setStorageItem("selected_llm_provider", workerProvider)
                AeginBridge.setStorageItem(
                    "selected_llm_mode",
                    when {
                        workerProvider.equals("Auto", ignoreCase = true) -> "auto"
                        workerProvider.equals("On-Device Model", ignoreCase = true) -> "local"
                        else -> "cloud"
                    }
                )
                persistSettingsPreservingAssistant()
                refreshConfiguredAndTestedStates()

                val isConf = configuredMap[savedEdit.name] == true
                if (!savedEdit.tested && isConf) {
                    val passed = runConnectionTestAndPersist(savedEdit.name, showFeedbackDialog = false)
                    if (!passed) {
                        showAlert(
                            "Verification Needed",
                            "'${savedEdit.name}' was saved, but connection verification failed. Run Test Connection before assigning it to Worker or Governance AI Model."
                        )
                    }
                } else if (!savedEdit.tested) {
                    if (workerProvider.equals(savedEdit.name, ignoreCase = true)) workerProvider = "Auto"
                    if (governanceProvider.equals(savedEdit.name, ignoreCase = true)) governanceProvider = "Disable"
                    persistSettingsPreservingAssistant()
                    refreshConfiguredAndTestedStates()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun handleSaveLLM() {
        scope.launch {
            isVaultScanning = true
            try {
                val eligible = eligibleCandidateNames().toSet()
                if (workerProvider != "Auto" && workerProvider !in eligible) {
                    showAlert(
                        "Untested Worker Model",
                        "'$workerProvider' must be configured and pass Test Connection before it can be assigned as the Worker AI Model."
                    )
                    return@launch
                }
                if (governanceProvider != "Disable" && governanceProvider != "Auto" && governanceProvider !in eligible) {
                    showAlert(
                        "Untested Governance Model",
                        "'$governanceProvider' must be configured and pass Test Connection before it can be assigned as the Governance AI Model."
                    )
                    return@launch
                }

                assistantProvider = workerProvider
                AeginBridge.setStorageItem("selected_llm_provider", workerProvider)
                AeginBridge.setStorageItem(
                    "selected_llm_mode",
                    when {
                        workerProvider.equals("Auto", ignoreCase = true) -> "auto"
                        workerProvider.equals("On-Device Model", ignoreCase = true) -> "local"
                        else -> "cloud"
                    }
                )
                persistSettingsPreservingAssistant()
                refreshConfiguredAndTestedStates()

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
                            isThinking = isVaultScanning ||
                                modelDownloadState.status == "DOWNLOADING" ||
                                modelDownloadState.status == "VERIFYING",
                            showStatusDot = true
                        )
                        Column {
                            Text(
                                text = "Design Your AI Studio",
                                color = theme.text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Worker: $workerProvider • $vaultDocCount docs ($vaultChunkCount vectors)",
                                color = theme.subText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            "General" -> "Configuration"
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
                val eligibleModels = providersList.filter { it.name != "Disable" && it.name != "Custom" }

                // 1. UNIFIED AI MODELS CATALOG & CONNECTION VERIFICATION
                UnifiedAiModelsCard(
                    models = eligibleModels,
                    selectedModelName = selectedModelName,
                    testingModelName = testingModelFromCard,
                    modelDownloadState = modelDownloadState,
                    localModelCheck = localModelCheck,
                    recommendedLocalModel = capabilities.recommendedModel,
                    isProviderConfigured = ::isProviderConfigured,
                    isProviderTested = ::isProviderTested,
                    onOpenSelectModal = { isModelCatalogModalVisible = true },
                    onOpenEditModal = ::openEditModal,
                    onTestConnection = { name ->
                        scope.launch { runConnectionTestAndPersist(name, showFeedbackDialog = true) }
                    },
                    onDownloadLocalModel = { refreshLocalModelCheckAndPrompt(promptIfMissing = true) },
                    onPauseLocalDownload = {
                        scope.launch {
                            AeginBridge.pauseLocalModelDownload(modelDownloadState.modelName)
                            localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                            refreshConfiguredAndTestedStates()
                        }
                    },
                    onOpenAddNewModal = ::openAddNewProviderModal
                )

                // 2. WORKER & GOVERNANCE AI MODEL ASSIGNMENTS
                AiAssignmentsCard(
                    workerProvider = workerProvider,
                    governanceProvider = governanceProvider,
                    isLoadingProviders = isLoadingProviders,
                    isWorkerVerified = isProviderTested(workerProvider),
                    isGovVerified = governanceProvider == "Disable" || isProviderTested(governanceProvider),
                    onOpenWorkerSelectModal = { isWorkerModalVisible = true },
                    onOpenGovSelectModal = { isGovModalVisible = true },
                    onSaveConfiguration = ::handleSaveLLM
                )

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
                        text = "Reference documents in this folder are indexed for AI inference. Saving a new document refreshes the embeddings database.",
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

    // Worker AI Model Selection Modal (Auto + Configured & Tested models only)
    if (isWorkerModalVisible) {
        val testedCandidates = eligibleCandidateNames()
        val workerItems = listOf("Auto") + testedCandidates
        ProviderListDialog(
            title = "Select Worker AI Model",
            items = workerItems,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            emptyHint = if (testedCandidates.isEmpty()) {
                "No configured & tested AI models yet. Select a model in AI Model Setup above, configure it, and run Test Connection to add candidates here."
            } else null,
            onSelect = { chosen ->
                workerProvider = chosen
                isWorkerModalVisible = false
            },
            onCancel = { isWorkerModalVisible = false }
        )
    }

    // Governance AI Model Selection Modal (Disable, Auto + Configured & Tested models only)
    if (isGovModalVisible) {
        val testedCandidates = eligibleCandidateNames()
        ProviderListDialog(
            title = "Select Governance AI Model",
            items = listOf("Disable", "Auto") + testedCandidates,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            emptyHint = if (testedCandidates.isEmpty()) {
                "No configured & tested AI models yet. Select a model in AI Model Setup above, configure it, and run Test Connection to add candidates here."
            } else null,
            onSelect = { chosen ->
                governanceProvider = chosen
                isGovModalVisible = false
            },
            onCancel = { isGovModalVisible = false }
        )
    }

    // Unified AI Models Dropdown Selection Modal
    if (isModelCatalogModalVisible) {
        val allModels = providersList
            .filter { it.name != "Disable" && it.name != "Custom" }
            .map { it.name } + listOf("+ Add New Provider")
        ProviderListDialog(
            title = "Select AI Model",
            items = allModels,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            onSelect = { chosen ->
                isModelCatalogModalVisible = false
                if (chosen == "+ Add New Provider") {
                    openAddNewProviderModal()
                } else {
                    selectedModelName = chosen
                    if (!isProviderConfigured(chosen)) {
                        openEditModal(chosen)
                    }
                }
            },
            onCancel = { isModelCatalogModalVisible = false }
        )
    }

    // Edit / Add Provider Modal
    if (isEditModalVisible) {
        EditProviderDialog(
            editData = editData,
            isAddingNewProvider = isAddingNewProvider,
            hasExistingSecretRef = !isAddingNewProvider && editOriginalApiKey.startsWith("REF:"),
            isTestingConnection = isTestingEditConnection,
            testFeedback = editTestFeedback,
            modelDownloadState = modelDownloadState,
            localModelCheck = localModelCheck,
            recommendedLocalModel = capabilities.recommendedModel,
            onEditDataChange = { updated ->
                val connectionChanged = updated.type != editData.type ||
                    updated.provider != editData.provider ||
                    updated.endpoint != editData.endpoint ||
                    updated.model != editData.model ||
                    updated.apiKey != editData.apiKey
                if (connectionChanged) {
                    editTestFeedback = null
                    editData = updated.copy(tested = false, testedFingerprint = "")
                } else {
                    editData = updated
                }
            },
            onTestConnection = ::handleTestEditConnection,
            onDownloadLocalModel = {
                val targetName = editData.model.ifBlank { capabilities.recommendedModel }
                scope.launch {
                    try {
                        val res = AeginBridge.checkLocalModelAvailability(targetName)
                        localModelCheck = res
                        val dlActive = modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING"
                        if (!res.existsLocally && !dlActive) {
                            pendingModelDownloadPrompt = res
                        }
                    } catch (_: Exception) {
                    }
                }
            },
            onPauseLocalDownload = {
                scope.launch {
                    AeginBridge.pauseLocalModelDownload(modelDownloadState.modelName)
                    localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                    refreshConfiguredAndTestedStates()
                }
            },
            onSave = ::handleSaveProviderEdit,
            onRequestDelete = { showConfirmDeleteDialog = true },
            onDismiss = {
                isAddingNewProvider = false
                isEditModalVisible = false
            }
        )
    }

    if (showConfirmDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete '${editData.name}' from llm.json?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedName = editData.name
                        providersList.removeAll { it.name.equals(deletedName, ignoreCase = true) }
                        configuredMap.remove(deletedName)
                        testedMap.remove(deletedName)
                        if (workerProvider.equals(deletedName, ignoreCase = true)) {
                            workerProvider = "Auto"
                        }
                        if (governanceProvider.equals(deletedName, ignoreCase = true)) {
                            governanceProvider = "Disable"
                        }
                        if (assistantProvider.equals(deletedName, ignoreCase = true)) {
                            assistantProvider = "Auto"
                        }
                        if (selectedModelName.equals(deletedName, ignoreCase = true)) {
                            selectedModelName = providersList
                                .firstOrNull { it.name != "Disable" && it.name != "Custom" }
                                ?.name ?: "On-Device Model"
                        }
                        showConfirmDeleteDialog = false
                        isAddingNewProvider = false
                        isEditModalVisible = false
                        scope.launch {
                            persistSettingsPreservingAssistant()
                            refreshConfiguredAndTestedStates()
                        }
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

    val pendingPrompt = pendingModelDownloadPrompt
    if (pendingPrompt != null) {
        val formattedSize = AeginBridge.formatByteSize(pendingPrompt.approxSizeBytes)
        val wifiNotice = if (!pendingPrompt.isWifiConnected || pendingPrompt.isMeteredNetwork) {
            "⚠️ You are not currently on an unmetered Wi-Fi network. GGUF model files are very large ($formattedSize) and can consume substantial mobile data. Please consider switching to Wi-Fi before downloading."
        } else {
            "ℹ️ GGUF model files can be very large ($formattedSize). Make sure you remain connected to Wi-Fi while downloading."
        }
        AlertDialog(
            onDismissRequest = { pendingModelDownloadPrompt = null },
            title = { Text("Download On-Device Model?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (pendingPrompt.partialBytes > 0L) {
                            "${pendingPrompt.modelName} has a partial download (${AeginBridge.formatByteSize(pendingPrompt.partialBytes)}) ready to resume."
                        } else {
                            "${pendingPrompt.modelName} was not found locally on this device."
                        },
                        fontSize = 13.sp
                    )
                    Text(
                        text = wifiNotice,
                        color = if (!pendingPrompt.isWifiConnected || pendingPrompt.isMeteredNetwork) Color(0xFFF59E0B) else theme.subText,
                        fontSize = 12.sp
                    )
                    if (pendingPrompt.downloadUrl.isNotBlank()) {
                        Text(
                            text = "Source: ${pendingPrompt.downloadUrl}",
                            color = theme.subText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pendingPrompt.downloadUrl.isNotBlank(),
                    onClick = {
                        val targetModel = pendingPrompt.modelName
                        pendingModelDownloadPrompt = null
                        startLocalModelDownload(targetModel)
                    }
                ) {
                    Text(if (pendingPrompt.partialBytes > 0L) "Resume Download" else "Download Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingModelDownloadPrompt = null }) {
                    Text("Later")
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

    PlatformBackHandler {
        when {
            pendingModelDownloadPrompt != null -> pendingModelDownloadPrompt = null
            alertTitle != null -> {
                alertTitle = null
                alertMessage = null
            }
            showConfirmDeleteDialog -> showConfirmDeleteDialog = false
            showConfirmResetDialog -> showConfirmResetDialog = false
            showCustomVaultDialog -> showCustomVaultDialog = false
            isEditModalVisible -> {
                isAddingNewProvider = false
                isEditModalVisible = false
            }
            isModelCatalogModalVisible -> isModelCatalogModalVisible = false
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
    isVerified: Boolean,
    onOpenSelectModal: () -> Unit,
    showEditButton: Boolean = false,
    onOpenEditModal: () -> Unit = {},
    onCustomNameChange: (String) -> Unit = {}
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = selectedProvider,
                            color = theme.text,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedProvider != "Disable" && selectedProvider != "Auto" && selectedProvider != "Custom") {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isLoading -> Color(0xFF94A3B8)
                                            isVerified -> Color(0xFF22C55E)
                                            else -> Color(0xFFF59E0B)
                                        }
                                    )
                            )
                        }
                    }
                    Text("▼", color = theme.subText, fontSize = 12.sp)
                }
            }

            if (showEditButton && selectedProvider != "Disable" && selectedProvider != "Auto" && selectedProvider != "Custom") {
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
        if (!isLoading && selectedProvider != "Disable" && selectedProvider != "Auto" && selectedProvider != "Custom" && !isVerified) {
            Text(
                text = "'$selectedProvider' is not verified yet — configure and pass Test Connection under AI Model Setup, or choose Auto.",
                color = Color(0xFFF59E0B),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ProviderListDialog(
    title: String,
    items: List<String>,
    isConfigured: (String) -> Boolean,
    isTested: (String) -> Boolean = isConfigured,
    emptyHint: String? = null,
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = theme.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            items.forEach { item ->
                val configured = isConfigured(item)
                val tested = isTested(item)
                val badgeText: String?
                val badgeColor: Color
                when {
                    item == "Auto" -> {
                        badgeText = "AUTO"
                        badgeColor = theme.cyan
                    }
                    item == "Disable" -> {
                        badgeText = "OFF"
                        badgeColor = theme.subText
                    }
                    item == "+ Add New Provider" -> {
                        badgeText = "NEW"
                        badgeColor = theme.cyan
                    }
                    item == "Custom" -> {
                        badgeText = null
                        badgeColor = theme.subText
                    }
                    tested -> {
                        badgeText = "TESTED"
                        badgeColor = theme.success
                    }
                    configured -> {
                        badgeText = "UNTESTED"
                        badgeColor = Color(0xFFF59E0B)
                    }
                    else -> {
                        badgeText = "SETUP"
                        badgeColor = theme.danger
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(item) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item, color = theme.text, fontSize = 14.sp)
                    if (badgeText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            }
            if (!emptyHint.isNullOrBlank()) {
                Text(
                    text = emptyHint,
                    color = theme.subText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

@Composable
private fun UnifiedAiModelsCard(
    models: List<LlmProviderConfig>,
    selectedModelName: String,
    testingModelName: String?,
    modelDownloadState: LocalModelDownloadState,
    localModelCheck: LocalModelCheckResult?,
    recommendedLocalModel: String,
    isProviderConfigured: (String) -> Boolean,
    isProviderTested: (String) -> Boolean,
    onOpenSelectModal: () -> Unit,
    onOpenEditModal: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onDownloadLocalModel: () -> Unit,
    onPauseLocalDownload: () -> Unit,
    onOpenAddNewModal: () -> Unit
) {
    val theme = LocalAeginColors.current
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "AI Model Setup",
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
            text = "Manage all local and external AI models from llm.json. Only models that are configured and pass Test Connection appear as Worker and Governance candidates.",
            color = theme.subText,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        val activeModel = models.find { it.name.equals(selectedModelName, ignoreCase = true) }
            ?: models.firstOrNull()
        val activeName = activeModel?.name.orEmpty()
        val isLocal = activeModel != null && (activeModel.type.equals("Local", ignoreCase = true) || activeModel.name == "On-Device Model")
        val isConfigured = activeModel != null && isProviderConfigured(activeModel.name)
        val isTested = activeModel != null && isProviderTested(activeModel.name)
        val isTestingThis = activeModel != null && testingModelName.equals(activeModel.name, ignoreCase = true)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (activeName.isNotBlank()) activeName else "Select AI Model",
                        color = theme.text,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeModel != null) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isTested -> Color(0xFF22C55E)
                                        isConfigured -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                )
                        )
                    }
                }
                Text("▼", color = theme.subText, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeModel != null) Color(0xFF0EA5E9) else theme.border)
                    .clickable(enabled = activeModel != null) {
                        if (activeModel != null) onOpenEditModal(activeModel.name)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Edit", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (activeModel != null) {
            val typeLabel = when {
                isLocal -> "On-Device GGUF"
                activeModel.type.equals("Self-Hosted", ignoreCase = true) -> "Self-Hosted"
                else -> "Cloud"
            }
            val displayModel = activeModel.model.ifBlank {
                if (isLocal) recommendedLocalModel else "default"
            }
            val statusLabel = when {
                isTested -> "Verified & Tested"
                isConfigured -> "Configured • Test Required"
                else -> "Setup Required"
            }
            val statusColor = when {
                isTested -> theme.success
                isConfigured -> Color(0xFFF59E0B)
                else -> theme.danger
            }

            Text(
                text = "$typeLabel • Model: $displayModel • $statusLabel",
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (activeModel.intentOptions.isNotEmpty()) {
                Text(
                    text = "Intents (${activeModel.intentOptions.size}): ${activeModel.intentOptions.joinToString(", ")}",
                    color = theme.subText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLocal && (modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.cyan.copy(alpha = 0.08f))
                        .border(1.dp, theme.cyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = modelDownloadState.message.ifBlank { "Downloading ${modelDownloadState.modelName}..." },
                            color = theme.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${AeginBridge.formatByteSize(modelDownloadState.downloadedBytes)} / ${AeginBridge.formatByteSize(modelDownloadState.totalBytes)}",
                            color = theme.cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (modelDownloadState.totalBytes > 0L) {
                        LinearProgressIndicator(
                            progress = { (modelDownloadState.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (modelDownloadState.status == "DOWNLOADING") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, theme.warning, RoundedCornerShape(6.dp))
                                    .clickable { onPauseLocalDownload() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("⏸ Pause", color = theme.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLocal &&
                localModelCheck?.existsLocally == false &&
                modelDownloadState.status != "DOWNLOADING" &&
                modelDownloadState.status != "VERIFYING"
            ) {
                val partial = localModelCheck.partialBytes
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.cyan)
                        .clickable { onDownloadLocalModel() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (partial > 0L) "▶️ Resume (${AeginBridge.formatByteSize(partial)})" else "⬇️ Download GGUF",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activeModel != null && isConfigured) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (isTested) theme.success else theme.cyan, RoundedCornerShape(8.dp))
                        .clickable(enabled = !isTestingThis) { onTestConnection(activeModel.name) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when {
                            isTestingThis -> "Testing..."
                            isTested -> "✓ Re-test"
                            else -> "🧪 Test Connection"
                        },
                        color = if (isTested) theme.success else theme.cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, theme.primary, RoundedCornerShape(8.dp))
                    .clickable { onOpenAddNewModal() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "➕ Add New Provider",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AiAssignmentsCard(
    workerProvider: String,
    governanceProvider: String,
    isLoadingProviders: Boolean,
    isWorkerVerified: Boolean,
    isGovVerified: Boolean,
    onOpenWorkerSelectModal: () -> Unit,
    onOpenGovSelectModal: () -> Unit,
    onSaveConfiguration: () -> Unit
) {
    val theme = LocalAeginColors.current
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Worker & Governance AI Models",
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProviderSelectorRow(
            label = "Worker AI Model",
            description = "Used for background tasks and default routing. Assistant tab toggles (Auto / Local / Cloud) do not overwrite this.",
            selectedProvider = workerProvider,
            isLoading = isLoadingProviders,
            isVerified = isWorkerVerified,
            onOpenSelectModal = onOpenWorkerSelectModal
        )

        ProviderSelectorRow(
            label = "Governance AI Model",
            description = "Guardrail scanner inspecting prompts and risks across tasks and chat. Only verified models appear as candidates.",
            selectedProvider = governanceProvider,
            isLoading = isLoadingProviders,
            isVerified = isGovVerified,
            onOpenSelectModal = onOpenGovSelectModal
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(theme.primaryGradient)
                .clickable { onSaveConfiguration() }
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
}

@Composable
private fun EditProviderDialog(
    editData: LlmProviderConfig,
    isAddingNewProvider: Boolean,
    hasExistingSecretRef: Boolean,
    isTestingConnection: Boolean,
    testFeedback: Pair<Boolean, String>?,
    modelDownloadState: LocalModelDownloadState,
    localModelCheck: LocalModelCheckResult?,
    recommendedLocalModel: String,
    onEditDataChange: (LlmProviderConfig) -> Unit,
    onTestConnection: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onPauseLocalDownload: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isDeviceModel = !isAddingNewProvider && editData.name == "On-Device Model"
    val isSelfHosted = !isDeviceModel && editData.type.equals("Self-Hosted", ignoreCase = true)
    val isLocalProvider = isDeviceModel

    Dialog(onDismissRequest = onDismiss) {
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
            Text(
                text = when {
                    isAddingNewProvider -> "➕ Add External / Self-Hosted LLM"
                    isDeviceModel -> "📱 On-Device Model"
                    isSelfHosted -> "🖥️ Configure ${editData.name}"
                    else -> "☁️ Configure ${editData.name}"
                },
                color = theme.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (isDeviceModel) {
                val isDlRunning = modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING"
                val isReadyLocally = localModelCheck?.existsLocally == true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.primary.copy(alpha = 0.12f))
                        .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🔒 100% Private & Offline (Embedded GGUF)",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Runs locally on your device. Download the GGUF file (or point Endpoint URL to a local llama-server) and run Test Connection to verify.",
                        color = theme.subText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    if (isDlRunning) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${modelDownloadState.status} ${modelDownloadState.percent}%",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, theme.warning, RoundedCornerShape(6.dp))
                                    .clickable { onPauseLocalDownload() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("⏸ Pause", color = theme.warning, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!isReadyLocally) {
                        val targetName = editData.model.ifBlank { recommendedLocalModel }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Model '$targetName' not found locally.",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.cyan)
                                    .clickable { onDownloadLocalModel() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                val partial = localModelCheck?.partialBytes ?: 0L
                                Text(
                                    text = if (partial > 0L) "▶️ Resume" else "⬇️ Download",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "✓ GGUF model present locally on device.",
                            color = theme.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                if (isAddingNewProvider) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PROVIDER NAME", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        OutlinedTextField(
                            value = editData.name,
                            onValueChange = { onEditDataChange(editData.copy(name = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. Ollama Local, vLLM Server, Anthropic", color = theme.subText, fontSize = 13.sp) }
                        )
                    }
                }

                Text("PROVIDER TYPE", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSelfHosted) Color(0xFF0F172A) else theme.inputBg)
                            .border(1.dp, if (!isSelfHosted) Color(0xFF0F172A) else theme.border, RoundedCornerShape(8.dp))
                            .clickable {
                                onEditDataChange(
                                    editData.copy(
                                        type = "Cloud",
                                        intentOptions = if (isAddingNewProvider) AeginBridge.DEFAULT_CLOUD_INTENTS else editData.intentOptions
                                    )
                                )
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("☁️ Cloud API", color = if (!isSelfHosted) Color.White else theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelfHosted) Color(0xFF0F172A) else theme.inputBg)
                            .border(1.dp, if (isSelfHosted) Color(0xFF0F172A) else theme.border, RoundedCornerShape(8.dp))
                            .clickable {
                                onEditDataChange(
                                    editData.copy(
                                        type = "Self-Hosted",
                                        provider = "OpenAI",
                                        intentOptions = if (isAddingNewProvider) AeginBridge.DEFAULT_LOCAL_INTENTS else editData.intentOptions
                                    )
                                )
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🖥️ Self-Hosted LLM", color = if (isSelfHosted) Color.White else theme.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

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
                        text = if (isSelfHosted) "🖥️ Self-Hosted OpenAI-Compatible Endpoint" else "⚡ Commercial Cloud LLM Service",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSelfHosted) {
                            "Connects to your local or LAN inference server (Ollama, vLLM, LM Studio, llama-server). API key is optional."
                        } else {
                            "Requests are sent over the internet to ${editData.name.ifBlank { "your provider" }}'s API endpoint. Requires an API key."
                        },
                        color = theme.subText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            if (!isLocalProvider) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isSelfHosted) "API KEY (OPTIONAL)" else "API KEY",
                        color = theme.text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    OutlinedTextField(
                        value = editData.apiKey,
                        onValueChange = { onEditDataChange(editData.copy(apiKey = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = when {
                                    hasExistingSecretRef -> "Stored securely (leave blank to keep existing key)"
                                    isSelfHosted -> "Optional bearer token"
                                    else -> "Paste your API key here"
                                },
                                color = theme.subText,
                                fontSize = 13.sp
                            )
                        },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    val keyHint = when {
                        isSelfHosted -> "Leave blank if your self-hosted server does not require authentication"
                        editData.name == "Google Gemini" -> "Get an API key from Google AI Studio (aistudio.google.com)"
                        editData.name == "OpenAI" -> "Get an API key from OpenAI Platform (platform.openai.com)"
                        editData.name == "DeepSeek" -> "Get an API key from DeepSeek Platform (platform.deepseek.com)"
                        editData.name == "Qwen" -> "Get an API key from Alibaba Cloud DashScope (dashscope.aliyun.com)"
                        else -> "Enter your commercial provider API key"
                    }
                    Text(
                        text = keyHint,
                        color = theme.subText,
                        fontSize = 10.sp
                    )
                }
            }

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
                        onValueChange = { onEditDataChange(editData.copy(model = it)) },
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
                                    onEditDataChange(editData.copy(model = next))
                                }
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Text("CYCLE", color = theme.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isDeviceModel) "INTERNAL ENGINE ENDPOINT" else "ENDPOINT URL",
                    color = theme.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                OutlinedTextField(
                    value = editData.endpoint,
                    onValueChange = { onEditDataChange(editData.copy(endpoint = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

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
                                onEditDataChange(editData.copy(intentOptions = updated))
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

            if (testFeedback != null) {
                val (ok, msg) = testFeedback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background((if (ok) theme.success else theme.danger).copy(alpha = 0.12f))
                        .border(1.dp, (if (ok) theme.success else theme.danger).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (ok) "✓ $msg" else "⚠️ $msg",
                        color = if (ok) theme.success else theme.danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .border(1.dp, theme.cyan, CircleShape)
                    .background(theme.cyan.copy(alpha = 0.1f))
                    .clickable(enabled = !isTestingConnection) { onTestConnection() }
                    .padding(11.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTestingConnection) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = theme.cyan)
                        Text("Testing Connection...", color = theme.cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Text("🧪 Test Connection", color = theme.cyan, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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
                        .background(theme.primaryGradient)
                        .clickable(enabled = !isTestingConnection) { onSave() }
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
                        .clickable(enabled = !isTestingConnection) { onDismiss() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (!isAddingNewProvider && !isDeviceModel) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, theme.danger, RoundedCornerShape(8.dp))
                        .clickable { onRequestDelete() }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑️ Delete Provider", color = theme.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
