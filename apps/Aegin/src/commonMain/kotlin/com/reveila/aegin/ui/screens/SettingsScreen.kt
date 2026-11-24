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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reveila.aegin.bridge.AeginBridge
import com.reveila.aegin.bridge.ModelTierCatalog
import com.reveila.aegin.bridge.AeginDisplayMapper
import com.reveila.aegin.bridge.DeviceCapabilityReport
import com.reveila.aegin.bridge.LlmProviderConfig
import com.reveila.aegin.bridge.LocalModelCheckResult
import com.reveila.aegin.bridge.LocalModelDownloadState
import com.reveila.aegin.bridge.LocalModelManifestEntry
import androidx.compose.foundation.layout.windowInsetsPadding
import com.reveila.aegin.ui.PlatformBackHandler
import com.reveila.aegin.ui.components.VaultFolderPickerDialog
import com.reveila.aegin.ui.theme.AeginBrandPreset
import com.reveila.aegin.ui.theme.AeginIconType
import com.reveila.aegin.ui.theme.AeginPillButton
import com.reveila.aegin.ui.theme.AeginPillButtonVariant
import com.reveila.aegin.ui.theme.AeginVectorIcon
import com.reveila.aegin.ui.theme.LocalAeginColors
import com.reveila.aegin.ui.theme.LocalAeginTheme
import com.reveila.aegin.ui.theme.pillMinWidthAtLeastHeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenSecurityPortal: () -> Unit,
    availableThemePresets: List<AeginBrandPreset> = emptyList(),
    selectedThemePresetId: String = "",
    isDarkMode: Boolean = true,
    onSelectThemePreset: (String) -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {}
) {
    val theme = LocalAeginColors.current
    val resolvedTheme = LocalAeginTheme.current
    val scope = rememberCoroutineScope()

    val tabs = listOf("General", "Security", "Advanced")
    var activeTab by remember { mutableStateOf("General") }
    var isBiometricEnabled by remember { mutableStateOf(false) }
    var isLoadingProviders by remember { mutableStateOf(true) }
    val providersList = remember { mutableStateListOf<LlmProviderConfig>() }
    val configuredMap = remember { mutableStateMapOf<String, Boolean>() }
    val testedMap = remember { mutableStateMapOf<String, Boolean>() }

    var workerProvider by remember { mutableStateOf("Auto") }
    var selectedModelName by remember { mutableStateOf("On-Device Model") }
    var governanceProvider by remember { mutableStateOf("Disable") }
    var assistantProvider by remember { mutableStateOf("") }

    var vaultFolder by remember { mutableStateOf("Documents/Reveila") }
    var showCustomVaultDialog by remember { mutableStateOf(false) }
    var vaultDocCount by remember { mutableStateOf(0) }
    var vaultChunkCount by remember { mutableStateOf(0) }
    var isVaultScanning by remember { mutableStateOf(false) }
    var browserSearchUrlDraft by remember { mutableStateOf("") }
    var browserSearchPresets by remember { mutableStateOf(com.reveila.aegin.bridge.BrowserSearchConfig.defaultPresets()) }
    var isSavingBrowserSearch by remember { mutableStateOf(false) }

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
    var localModelCatalog by remember { mutableStateOf<List<LocalModelManifestEntry>>(emptyList()) }
    var localModelCheck by remember { mutableStateOf<LocalModelCheckResult?>(null) }
    var pendingModelDownloadPrompt by remember { mutableStateOf<LocalModelCheckResult?>(null) }
    var pairedStatus by remember { mutableStateOf(com.reveila.aegin.bridge.PairedClusterStatus()) }
    var pairingInputDraft by remember { mutableStateOf("") }
    var desktopRemoteUrlDraft by remember { mutableStateOf("") }
    var isPairingBusy by remember { mutableStateOf(false) }
    var isInstallingLocalRuntime by remember { mutableStateOf(false) }
    var detectedLocalApps by remember { mutableStateOf<List<com.reveila.aegin.bridge.DetectedLocalLlmApp>>(emptyList()) }
    var alertChannels by remember { mutableStateOf(AeginBridge.defaultAlertChannels()) }
    var appEdition by remember { mutableStateOf("individual") }
    var governancePolicy by remember { mutableStateOf(com.reveila.aegin.bridge.GovernancePolicyConfig()) }
    var isSavingGovernancePolicy by remember { mutableStateOf(false) }
    var editingAlertChannel by remember { mutableStateOf<com.reveila.aegin.bridge.AlertChannelConfig?>(null) }
    var isAddingCustomChannel by remember { mutableStateOf(false) }
    var testingAlertChannelId by remember { mutableStateOf<String?>(null) }
    var isSavingAlertChannel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            appEdition = AeginBridge.getAppEdition()
            alertChannels = AeginBridge.getAlertChannels()
            governancePolicy = AeginBridge.getGovernancePolicy()
        } catch (_: Exception) {
        }
    }

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

    fun startLocalModelDownload(modelName: String) {
        scope.launch {
            val trimmed = modelName.trim()
            if (trimmed.isNotBlank()) {
                val localIdx = providersList.indexOfFirst { it.name == "On-Device Model" }
                if (localIdx >= 0) {
                    val cur = providersList[localIdx]
                    val mergedOpts = if (cur.modelOptions.any { it.equals(trimmed, ignoreCase = true) }) {
                        cur.modelOptions
                    } else {
                        cur.modelOptions + trimmed
                    }
                    providersList[localIdx] = cur.copy(
                        model = trimmed,
                        modelOptions = mergedOpts,
                        tested = false,
                        testedFingerprint = ""
                    )
                }
                if (isEditModalVisible && editData.name == "On-Device Model") {
                    editData = editData.copy(model = trimmed, tested = false, testedFingerprint = "")
                }
                persistSettingsPreservingAssistant()
            }
            val ok = try {
                AeginBridge.downloadLocalModel(modelName)
            } catch (e: Exception) {
                showAlert("Model download failed", e.message ?: "Unable to download $modelName.")
                false
            }
            try {
                localModelCheck = AeginBridge.checkLocalModelAvailability(modelName)
                capabilities = AeginBridge.getDeviceCapabilities()
            } catch (_: Exception) {
            }
            if (!ok) {
                val dlState = AeginBridge.observeLocalModelDownloadState().value
                if (dlState.status == "FAILED" && dlState.message.isNotBlank()) {
                    showAlert("Model download failed", dlState.message)
                }
            }
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
                    if (result.ok) "Connection verified" else "Connection test failed",
                    result.message
                )
            }
            result.ok
        } finally {
            testingModelFromCard = null
        }
    }

    LaunchedEffect(modelDownloadState.status) {
        if (modelDownloadState.status == "COMPLETED" ||
            modelDownloadState.status == "FAILED" ||
            modelDownloadState.status == "PAUSED" ||
            modelDownloadState.status == "CANCELLED"
        ) {
            try {
                val targetName = modelDownloadState.modelName.ifBlank { activeLocalModelName() }
                if (modelDownloadState.status == "COMPLETED" && targetName.isNotBlank()) {
                    val localIdx = providersList.indexOfFirst { it.name == "On-Device Model" }
                    if (localIdx >= 0 && !providersList[localIdx].model.equals(targetName, ignoreCase = true)) {
                        providersList[localIdx] = providersList[localIdx].copy(model = targetName)
                    }
                    if (isEditModalVisible && editData.name == "On-Device Model" &&
                        !editData.model.equals(targetName, ignoreCase = true)
                    ) {
                        editData = editData.copy(model = targetName)
                    }
                }
                localModelCheck = AeginBridge.checkLocalModelAvailability(targetName)
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

    LaunchedEffect(isEditModalVisible, editData.name, editData.model) {
        if (isEditModalVisible && editData.name == "On-Device Model") {
            try {
                val target = editData.model.ifBlank { capabilities.recommendedModel }
                localModelCheck = AeginBridge.checkLocalModelAvailability(target)
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
            localModelCatalog = AeginBridge.getLocalModelCatalog()
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
            val browserCfg = AeginBridge.getBrowserConfig()
            browserSearchUrlDraft = browserCfg.defaultSearchUrl
            if (browserCfg.presets.isNotEmpty()) {
                browserSearchPresets = browserCfg.presets
            }
        } catch (_: Exception) {
        }

        try {
            pairedStatus = AeginBridge.getPairedClusterStatus()
            desktopRemoteUrlDraft = pairedStatus.remoteUrl
        } catch (_: Exception) {
        }

        try {
            detectedLocalApps = AeginBridge.detectLocalDesktopLlmApps()
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
                providersList.firstOrNull { it.name == "On-Device Model" }?.name
                    ?: providersList.firstOrNull()?.name
                    ?: "On-Device Model"
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
            val localIdx = providersList.indexOfFirst { it.name.equals("On-Device Model", ignoreCase = true) }
            if (localIdx >= 0 && capabilities.recommendedModel.endsWith(".gguf", ignoreCase = true)) {
                val curLocal = providersList[localIdx]
                val staged = capabilities.stagedModels
                val curDownloaded = staged.any { it.equals(curLocal.model, ignoreCase = true) }
                if (!curDownloaded &&
                    (curLocal.model.isBlank() ||
                        curLocal.model.equals("gemma-2-2b-it-Q4_K_M.gguf", ignoreCase = true)) &&
                    !curLocal.model.equals(capabilities.recommendedModel, ignoreCase = true)
                ) {
                    providersList[localIdx] = curLocal.copy(model = capabilities.recommendedModel)
                }
            }
            try {
                localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
            } catch (_: Exception) {
            }
            refreshConfiguredAndTestedStates()
            val localFile = activeLocalModelName()
            val localSafetyReady = (localModelCheck?.existsLocally == true) &&
                (localModelCatalog.firstOrNull {
                    it.effectiveFilename().equals(localFile, ignoreCase = true)
                }?.safetyReviewCapable ?: ModelTierCatalog.isSafetyReviewCapableModel(localFile))
            val hasPrivateSelfHosted = providersList.any { p ->
                p.type.equals("Self-Hosted", ignoreCase = true) &&
                    p.endpoint.isNotBlank() &&
                    p.model.isNotBlank() &&
                    !isPublicCloudEndpoint(p.endpoint)
            }
            if ((governanceProvider.equals("Auto", ignoreCase = true) && !localSafetyReady && !hasPrivateSelfHosted) ||
                (governanceProvider.equals("On-Device Model", ignoreCase = true) && !localSafetyReady)
            ) {
                governanceProvider = "Disable"
                persistSettingsPreservingAssistant()
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
                }
            } catch (_: Exception) {
            }
            delay(4000L)
        }
    }

    fun isProviderConfigured(pName: String): Boolean {
        if (isLoadingProviders) return false
        if (pName == "Auto") return true
        if (pName == "Disable" || pName == "Custom" || pName == "+ New") return false
        val p = providersList.find { it.name == pName } ?: return false
        if (p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model") {
            return capabilities.canRunLocalGguf && (localModelCheck?.existsLocally == true)
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
            showAlert("Validation error", "Please enter a provider name before saving.")
            return
        }
        if (isAddingNewProvider && providersList.any { it.name.equals(candidate.name, ignoreCase = true) }) {
            showAlert("Duplicate provider", "A provider named '${candidate.name}' already exists.")
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
                            "Verification needed",
                            "'${savedEdit.name}' was saved, but connection verification failed. Run Test connection before assigning it to Worker or Governance AI Model."
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
                        "Untested worker model",
                        "'$workerProvider' must be configured and pass Test connection before it can be assigned as the Worker AI Model."
                    )
                    return@launch
                }
                if (governanceProvider != "Disable" && governanceProvider != "Auto" && governanceProvider !in eligible) {
                    showAlert(
                        "Untested governance model",
                        "'$governanceProvider' must be configured and pass Test connection before it can be assigned as the Governance AI Model."
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
                    "Setup complete",
                    "Configuration saved and Personal Knowledge Folder indexed for '$vaultFolder' (${res.filesScanned} files, ${stats.chunkCount} sections)."
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
                    "Files indexed",
                    "Updated Personal Knowledge Folder '$targetFolder'. Files indexed: ${res.filesScanned}, searchable sections: ${stats.chunkCount}."
                )
            } catch (e: Exception) {
                showAlert("Indexing error", e.message ?: "Failed to index Personal Knowledge Folder.")
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
                    if (value) "System suspended" else "System resumed",
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
        contentWindowInsets = WindowInsets.safeDrawing
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
                // Studio Header Bar + Segmented Pill Tab Bar (tinted header pane for light/dark contrast)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.headerBg)
                ) {
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
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(theme.card)
                                .border(1.dp, theme.glassBorder, CircleShape)
                                .clickable { onClose() },
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
                                text = "Design Your AI Studio",
                                color = theme.text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Background AI: ${AeginDisplayMapper.providerDisplayName(workerProvider)} • $vaultDocCount files ($vaultChunkCount sections)",
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
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 10.dp)
                        .clip(CircleShape)
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, CircleShape)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEach { tab ->
                        val isSelected = activeTab == tab
                        val tabLabel = when (tab) {
                            "General" -> "Settings"
                            "Security" -> "Privacy"
                            else -> "Diagnostics"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) resolvedTheme.pillButton.primaryEnabledBrush
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
                                color = if (isSelected) resolvedTheme.pillButton.primaryEnabledText else theme.subText,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                                    )
                                )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(theme.border)
                )
                } // end Studio Header + Tab Bar Column

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

                // 0. APPEARANCE & CORPORATE BRAND THEME (app_theme.json)
                if (availableThemePresets.isNotEmpty()) {
                    AppearanceThemeCard(
                        presets = availableThemePresets,
                        selectedPresetId = selectedThemePresetId,
                        isDarkMode = isDarkMode,
                        onSelectPreset = onSelectThemePreset,
                        onToggleDarkMode = onToggleDarkMode
                    )
                }

                // 0b. DESKTOP COMPANION PAIRING (Desktop Lead / Pair Desktop)
                DesktopCompanionPairingCard(
                    pairedStatus = pairedStatus,
                    desktopRemoteUrlDraft = desktopRemoteUrlDraft,
                    onDesktopRemoteUrlChange = { desktopRemoteUrlDraft = it },
                    pairingInputDraft = pairingInputDraft,
                    onPairingInputChange = { pairingInputDraft = it },
                    isPairingBusy = isPairingBusy,
                    onEnableDesktopLead = {
                        scope.launch {
                            isPairingBusy = true
                            try {
                                pairedStatus = AeginBridge.enableDesktopLeadMode(
                                    port = pairedStatus.port,
                                    remoteUrl = desktopRemoteUrlDraft
                                )
                            } catch (e: Exception) {
                                showAlert("Lead Mode Error", e.message ?: "Failed to start Desktop Lead.")
                            } finally {
                                isPairingBusy = false
                            }
                        }
                    },
                    onDisablePairedMode = {
                        scope.launch {
                            isPairingBusy = true
                            try {
                                pairedStatus = AeginBridge.disablePairedClusterMode()
                            } finally {
                                isPairingBusy = false
                            }
                        }
                    },
                    onScanWifi = {
                        scope.launch {
                            isPairingBusy = true
                            try {
                                val found = AeginBridge.discoverLanDesktopLeads()
                                if (found.isNotEmpty()) {
                                    pairingInputDraft = found.first()
                                    showAlert("Desktop Found", "Discovered Aegin Desktop on Wi-Fi: ${found.first()}")
                                } else {
                                    showAlert("Not Found", "No Aegin Desktop Lead found on Wi-Fi. Ensure Desktop Lead Mode is enabled on the same network.")
                                }
                            } finally {
                                isPairingBusy = false
                            }
                        }
                    },
                    onPairDesktop = {
                        scope.launch {
                            isPairingBusy = true
                            try {
                                pairedStatus = AeginBridge.pairWithDesktop(pairingInputDraft)
                                showAlert("Paired Mode", pairedStatus.statusMessage)
                            } catch (e: Exception) {
                                showAlert("Pairing Failed", e.message ?: "Unable to pair with Desktop.")
                            } finally {
                                isPairingBusy = false
                            }
                        }
                    }
                )

                // 0c. PROACTIVE ALERTS & CHANNELS (Slack / Microsoft Teams / Email [+ Custom in Individual edition])
                ProactiveAlertChannelsCard(
                    channels = alertChannels,
                    appEdition = appEdition,
                    testingChannelId = testingAlertChannelId,
                    onToggleEnabled = { ch, enabled ->
                        val updated = alertChannels.map {
                            if (it.id.equals(ch.id, ignoreCase = true)) it.copy(enabled = enabled) else it
                        }
                        alertChannels = updated
                        scope.launch {
                            val (ok, msg) = AeginBridge.saveAlertChannels(updated)
                            if (ok) {
                                alertChannels = AeginBridge.getAlertChannels()
                            } else {
                                showAlert("Save Failed", msg)
                            }
                        }
                    },
                    onConfigure = { ch ->
                        isAddingCustomChannel = false
                        editingAlertChannel = ch
                    },
                    onAddCustomChannel = {
                        isAddingCustomChannel = true
                        editingAlertChannel = com.reveila.aegin.bridge.AlertChannelConfig(
                            id = "custom-${alertChannels.count { it.isCustomChannel() } + 1}",
                            type = "CUSTOM_WEBHOOK",
                            enabled = true,
                            endpoint = "",
                            minSeverity = "SUCCESS"
                        )
                    },
                    onTest = { ch ->
                        scope.launch {
                            testingAlertChannelId = ch.id
                            try {
                                val (ok, msg) = AeginBridge.sendTestChannelAlert(ch.id)
                                showAlert(if (ok) "Test Alert Sent" else "Test Alert Failed", msg)
                            } finally {
                                testingAlertChannelId = null
                            }
                        }
                    }
                )

                editingAlertChannel?.let { channelToEdit ->
                    AlertChannelConfigDialog(
                        initial = channelToEdit,
                        isNewCustom = isAddingCustomChannel,
                        canDelete = !appEdition.equals("enterprise", ignoreCase = true) && channelToEdit.isCustomChannel() && !isAddingCustomChannel,
                        isSaving = isSavingAlertChannel,
                        onDismiss = {
                            editingAlertChannel = null
                            isAddingCustomChannel = false
                        },
                        onDelete = { toRemove ->
                            scope.launch {
                                isSavingAlertChannel = true
                                try {
                                    val updatedList = alertChannels.filterNot { it.id.equals(toRemove.id, ignoreCase = true) }
                                    val (ok, msg) = AeginBridge.saveAlertChannels(updatedList)
                                    if (ok) {
                                        alertChannels = AeginBridge.getAlertChannels()
                                        editingAlertChannel = null
                                        isAddingCustomChannel = false
                                    } else {
                                        showAlert("Remove Failed", msg)
                                    }
                                } finally {
                                    isSavingAlertChannel = false
                                }
                            }
                        },
                        onSave = { updatedChannel ->
                            scope.launch {
                                isSavingAlertChannel = true
                                try {
                                    val exists = alertChannels.any { it.id.equals(updatedChannel.id, ignoreCase = true) }
                                    val updatedList = if (exists) {
                                        alertChannels.map {
                                            if (it.id.equals(updatedChannel.id, ignoreCase = true)) updatedChannel else it
                                        }
                                    } else {
                                        alertChannels + updatedChannel
                                    }
                                    val (ok, msg) = AeginBridge.saveAlertChannels(updatedList)
                                    if (ok) {
                                        alertChannels = AeginBridge.getAlertChannels()
                                        editingAlertChannel = null
                                        isAddingCustomChannel = false
                                        showAlert("Channel Saved", "${updatedChannel.displayName()} credentials stored securely.")
                                    } else {
                                        showAlert("Save Failed", msg)
                                    }
                                } finally {
                                    isSavingAlertChannel = false
                                }
                            }
                        }
                    )
                }

                // 1. UNIFIED AI SETUP (Model Catalog + Worker & Governance Assignments)
                AiSetupCard(
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
                    onCancelLocalDownload = {
                        scope.launch {
                            val target = modelDownloadState.modelName.ifBlank { activeLocalModelName() }
                            AeginBridge.cancelLocalModelDownload(target)
                            localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                            capabilities = AeginBridge.getDeviceCapabilities()
                            refreshConfiguredAndTestedStates()
                        }
                    },
                    onOpenAddNewModal = ::openAddNewProviderModal,
                    workerProvider = workerProvider,
                    governanceProvider = governanceProvider,
                    isLoadingProviders = isLoadingProviders,
                    isWorkerVerified = isProviderTested(workerProvider),
                    isGovVerified = governanceProvider == "Disable" || isProviderTested(governanceProvider),
                    onOpenWorkerSelectModal = { isWorkerModalVisible = true },
                    onOpenGovSelectModal = { isGovModalVisible = true },
                    onSaveConfiguration = ::handleSaveLLM,
                    capabilities = capabilities,
                    localModelCatalog = localModelCatalog,
                    isInstallingRuntime = isInstallingLocalRuntime,
                    onInstallLocalRuntime = {
                        scope.launch {
                            isInstallingLocalRuntime = true
                            try {
                                val (ok, msg) = AeginBridge.installLocalRuntime()
                                capabilities = AeginBridge.getDeviceCapabilities()
                                localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                                refreshConfiguredAndTestedStates()
                                showAlert(if (ok) "Runtime Installed" else "Install Failed", msg)
                            } catch (e: Exception) {
                                showAlert("Install Failed", e.message ?: "Unable to install local AI runtime.")
                            } finally {
                                isInstallingLocalRuntime = false
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal Knowledge Folder",
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
                        text = "This folder organizes your personal knowledge. Add documents to this folder to use them in AI inference. Supported formats: PDF (.pdf), Word (.docx), plain text (.txt, .md, .log), and structured text (.csv, .json, .xml, .yaml, .html).",
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
                            Text("INDEXED SECTIONS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = theme.subText)
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
                                .clip(CircleShape)
                                .background(theme.primaryGradient)
                                .clickable(enabled = !isVaultScanning) { handleSaveAndScanVault() }
                                .pillMinWidthAtLeastHeight()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                                    text = "Save & re-index",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Web Search & Virtual Browser Default URL Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(theme.card)
                        .border(1.dp, theme.glassBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🌐 Web Search & Virtual Browser",
                        color = theme.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose the default search engine or news feed URL used by the Virtual Browser. Individual tasks (like News Brief) can override this with their own saved URL.",
                        color = theme.subText,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        browserSearchPresets.forEach { preset ->
                            val selected = browserSearchUrlDraft.trim() == preset.url
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) theme.cyan.copy(alpha = 0.2f) else theme.inputBg)
                                    .border(1.dp, if (selected) theme.cyan else theme.border, CircleShape)
                                    .clickable { browserSearchUrlDraft = preset.url }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (selected) theme.cyan else theme.text,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = browserSearchUrlDraft,
                        onValueChange = { browserSearchUrlDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        placeholder = {
                            Text(
                                "Leave blank for Smart Auto, or enter a URL / template with {query}",
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
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(theme.cloudGradient)
                            .clickable(enabled = !isSavingBrowserSearch) {
                                scope.launch {
                                    isSavingBrowserSearch = true
                                    try {
                                        val saved = AeginBridge.saveBrowserConfig(browserSearchUrlDraft.trim())
                                        browserSearchUrlDraft = saved.defaultSearchUrl
                                        showAlert(
                                            "Browser Search Updated",
                                            if (saved.defaultSearchUrl.isBlank()) {
                                                "Virtual Browser set to Smart Auto (News RSS + DuckDuckGo fallback)."
                                            } else {
                                                "Default Virtual Browser search URL saved:\n${saved.defaultSearchUrl}"
                                            }
                                        )
                                    } catch (e: Exception) {
                                        showAlert("Save Error", e.message ?: "Could not save browser search URL.")
                                    } finally {
                                        isSavingBrowserSearch = false
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSavingBrowserSearch) "Saving..." else "Save Search URL",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
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

                GovernancePolicyEditorCard(
                    policy = governancePolicy,
                    appEdition = appEdition,
                    isSaving = isSavingGovernancePolicy,
                    onSavePolicy = { updated ->
                        scope.launch {
                            isSavingGovernancePolicy = true
                            try {
                                val (ok, msg) = AeginBridge.saveGovernancePolicy(updated)
                                if (ok) {
                                    governancePolicy = AeginBridge.getGovernancePolicy()
                                    showAlert("Policy Saved", msg)
                                } else {
                                    showAlert("Save Failed", msg)
                                }
                            } finally {
                                isSavingGovernancePolicy = false
                            }
                        }
                    },
                    onResetDefaults = {
                        scope.launch {
                            isSavingGovernancePolicy = true
                            try {
                                val (ok, msg) = AeginBridge.resetGovernancePolicy()
                                if (ok) {
                                    governancePolicy = AeginBridge.getGovernancePolicy()
                                    showAlert("Policy Reset", msg)
                                } else {
                                    showAlert("Reset Failed", msg)
                                }
                            } finally {
                                isSavingGovernancePolicy = false
                            }
                        }
                    }
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
                        text = "Privacy & Safety Portal",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Review on-device privacy protections, approvals, and recent safety activity.",
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
                            text = "Open Privacy & Safety Portal",
                            color = theme.accent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (activeTab == "Advanced") {
                Text(
                    text = "Diagnostics & Device Health",
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
                        text = "On-Device AI Readiness",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device Tier: ${AeginDisplayMapper.hardwareTier(capabilities.hardwareTier)} • Recommended: ${AeginDisplayMapper.modelDisplayName(capabilities.recommendedModel)}",
                        color = if (capabilities.canRunLocalGguf) theme.success else theme.warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Memory: ${capabilities.totalRamGb} GB total (${capabilities.availableRamGb} GB free) • Storage: ${capabilities.freeDiskGb} GB free",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                    val gpuSummary = when {
                        capabilities.hasDiscreteGpu && capabilities.gpuName.isNotBlank() ->
                            "${capabilities.gpuName} (${capabilities.gpuVramGb} GB VRAM)"
                        capabilities.hasUnifiedMemoryGpu ->
                            "${capabilities.gpuName.ifBlank { "Unified GPU" }} (${capabilities.gpuVramGb} GB)"
                        capabilities.supportsVulkanCompute -> "Vulkan Available"
                        else -> "CPU-Only"
                    }
                    Text(
                        text = "Processor: ${capabilities.cpuAbi}${if (capabilities.cpuCores > 0) " (${capabilities.cpuCores} cores)" else ""} • Fast Math: ${if (capabilities.supportsSimdDotProd) "Yes" else "No"} • GPU: $gpuSummary",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                    if (capabilities.downgradedReason.isNotBlank()) {
                        Text(
                            text = "Auto-Adjustment: ${capabilities.downgradedReason}",
                            color = theme.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Text(
                        text = "Offline Model Storage Folder:",
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
                        text = "Engine & Companion Health",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Edition: ${appEdition.uppercase()} • Companion Mode: ${pairedStatus.mode} • ${if (pairedStatus.isServerRunning || pairedStatus.leaderReachable) "Connected" else "Standalone / Stand-by"}",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "System Storage Mount Path:",
                        color = theme.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = storageMountPath,
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Personal Knowledge Index Sync",
                        color = theme.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Re-scan your Personal Knowledge Folder so Aegin picks up new or edited files.",
                        color = theme.subText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Box(
                        modifier = Modifier
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
                                        showAlert("Index sync", "Personal Knowledge Folder scan completed successfully.")
                                    } catch (e: Exception) {
                                        showAlert("Scan failed", e.message ?: "Error triggering scan")
                                    }
                                }
                            }
                            .pillMinWidthAtLeastHeight()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Force Re-Scan Now",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
            title = "Select Task Execution AI",
            items = workerItems,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            emptyHint = if (testedCandidates.isEmpty()) {
                "No verified AI models yet. Select a model in AI Setup above, configure it, and run Test connection."
            } else null,
            onSelect = { chosen ->
                workerProvider = chosen
                isWorkerModalVisible = false
            },
            onCancel = { isWorkerModalVisible = false }
        )
    }

    // Governance AI Model Selection Modal (Disable, optional Auto when private safety model is available + Capable Verified models)
    if (isGovModalVisible) {
        val localModelFile = providersList.find { it.name == "On-Device Model" }?.model
            ?.ifBlank { capabilities.recommendedModel }.orEmpty()
        val localCanAudit = (localModelCheck?.existsLocally == true) &&
            (localModelCatalog.firstOrNull {
                it.effectiveFilename().equals(localModelFile, ignoreCase = true)
            }?.safetyReviewCapable ?: ModelTierCatalog.isSafetyReviewCapableModel(localModelFile))
        val hasPrivateSelfHosted = providersList.any { p ->
            p.type.equals("Self-Hosted", ignoreCase = true) &&
                isProviderConfigured(p.name) &&
                !isPublicCloudEndpoint(p.endpoint)
        }

        val testedCandidates = eligibleCandidateNames().filter { candidate ->
            if (candidate.equals("On-Device Model", ignoreCase = true)) localCanAudit else true
        }
        val govChoices = buildList {
            add("Disable")
            if (localCanAudit || hasPrivateSelfHosted) {
                add("Auto")
            }
            addAll(testedCandidates)
        }
        ProviderListDialog(
            title = "Select Safety Guard AI",
            items = govChoices,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            emptyHint = if (testedCandidates.isEmpty()) {
                "Embedded local model is too small or not downloaded for reliable AI Safety Review. Connect a Self-Hosted private-network model (or a 7B+ desktop model) and run Test connection, or keep Safety Review set to Off."
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
            .map { it.name } + listOf("+ New")
        ProviderListDialog(
            title = "Select AI model",
            items = allModels,
            isConfigured = { isProviderConfigured(it) },
            isTested = { isProviderTested(it) },
            onSelect = { chosen ->
                isModelCatalogModalVisible = false
                if (chosen == "+ New") {
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
            localModelCatalog = localModelCatalog,
            stagedGgufNames = capabilities.stagedModels.map { it.substringBefore(" (").trim() },
            localModelCheck = localModelCheck,
            recommendedLocalModel = capabilities.recommendedModel,
            capabilities = capabilities,
            isInstallingRuntime = isInstallingLocalRuntime,
            detectedLocalApps = detectedLocalApps,
            onConnectDetectedApp = { app ->
                val defaultModel = app.models.firstOrNull()?.takeIf { it.isNotBlank() } ?: "llama3.2"
                val newCfg = LlmProviderConfig(
                    name = app.appName,
                    type = "Self-Hosted",
                    provider = "OpenAI",
                    endpoint = app.endpoint,
                    model = defaultModel,
                    modelOptions = app.models.ifEmpty { listOf(defaultModel) },
                    temperature = 0.7,
                    apiKey = "",
                    intentOptions = AeginBridge.DEFAULT_LOCAL_INTENTS + listOf("CLOUD_DEEP_REASONING"),
                    tested = false,
                    testedFingerprint = ""
                )
                val existingIdx = providersList.indexOfFirst { it.name.equals(app.appName, ignoreCase = true) }
                if (existingIdx >= 0) {
                    providersList[existingIdx] = newCfg
                } else {
                    providersList.add(newCfg)
                }
                selectedModelName = app.appName
                isEditModalVisible = false
                scope.launch {
                    persistSettingsPreservingAssistant()
                    refreshConfiguredAndTestedStates()
                    runConnectionTestAndPersist(app.appName, showFeedbackDialog = true)
                }
            },
            onInstallLocalRuntime = {
                scope.launch {
                    isInstallingLocalRuntime = true
                    try {
                        val (ok, msg) = AeginBridge.installLocalRuntime()
                        capabilities = AeginBridge.getDeviceCapabilities()
                        localModelCheck = AeginBridge.checkLocalModelAvailability(
                            editData.model.ifBlank { capabilities.recommendedModel }
                        )
                        refreshConfiguredAndTestedStates()
                        showAlert(if (ok) "Runtime Installed" else "Install Failed", msg)
                    } catch (e: Exception) {
                        showAlert("Install Failed", e.message ?: "Unable to install local AI runtime.")
                    } finally {
                        isInstallingLocalRuntime = false
                    }
                }
            },
            onEditDataChange = { updated ->
                val modelChanged = updated.model != editData.model
                val connectionChanged = updated.type != editData.type ||
                    updated.provider != editData.provider ||
                    updated.endpoint != editData.endpoint ||
                    modelChanged ||
                    updated.apiKey != editData.apiKey
                if (connectionChanged) {
                    editTestFeedback = null
                    editData = updated.copy(tested = false, testedFingerprint = "")
                } else {
                    editData = updated
                }
                if (modelChanged && updated.name.equals("On-Device Model", ignoreCase = true)) {
                    scope.launch {
                        try {
                            capabilities = AeginBridge.getDeviceCapabilities()
                            localModelCheck = AeginBridge.checkLocalModelAvailability(
                                updated.model.ifBlank { capabilities.recommendedModel }
                            )
                        } catch (_: Exception) {
                        }
                    }
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
            onCancelLocalDownload = {
                scope.launch {
                    val target = modelDownloadState.modelName.ifBlank {
                        editData.model.ifBlank { capabilities.recommendedModel }
                    }
                    AeginBridge.cancelLocalModelDownload(target)
                    localModelCheck = AeginBridge.checkLocalModelAvailability(activeLocalModelName())
                    capabilities = AeginBridge.getDeviceCapabilities()
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
            title = { Text("Remove AI Model?") },
            text = { Text("Are you sure you want to remove '${AeginDisplayMapper.providerDisplayName(editData.name)}'?") },
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
            title = { Text("Confirm destructive reset") },
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
                                    "Reset complete",
                                    "Application reset complete. Configuration parameters have been cleared."
                                )
                            } catch (e: Exception) {
                                showAlert("Reset warning", e.message ?: "Reset executed.")
                            }
                        }
                    }
                ) {
                    Text("Reset platform", color = theme.danger)
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
        val remainingBytes = (pendingPrompt.approxSizeBytes - pendingPrompt.partialBytes).coerceAtLeast(0L)
        val hasInsufficientStorage = pendingPrompt.availableStorageBytes in 0 until remainingBytes
        val isStorageTight = !hasInsufficientStorage &&
            pendingPrompt.availableStorageBytes >= 0L &&
            pendingPrompt.availableStorageBytes < (remainingBytes + 1_500_000_000L)
        val wifiNotice = if (!pendingPrompt.isWifiConnected || pendingPrompt.isMeteredNetwork) {
            "⚠️ You are not currently on an unmetered Wi-Fi network. Offline AI model files are very large ($formattedSize) and can use substantial mobile data. Consider switching to Wi-Fi before downloading."
        } else {
            "ℹ️ Offline AI model files can be very large ($formattedSize). Stay connected to Wi-Fi while downloading."
        }
        AlertDialog(
            onDismissRequest = { pendingModelDownloadPrompt = null },
            title = { Text("Download Offline AI Model?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val friendlyModelName = AeginDisplayMapper.modelDisplayName(pendingPrompt.modelName)
                    val hasExistingOtherGguf = capabilities.stagedModels.any { staged ->
                        val fname = staged.substringBefore(" (").trim()
                        fname.endsWith(".gguf", ignoreCase = true) && !fname.equals(pendingPrompt.modelName, ignoreCase = true)
                    }
                    Text(
                        text = buildString {
                            if (pendingPrompt.partialBytes > 0L) {
                                append("$friendlyModelName has a partial download (${AeginBridge.formatByteSize(pendingPrompt.partialBytes)}) ready to resume (${AeginBridge.formatByteSize(remainingBytes)} remaining).")
                            } else {
                                append("$friendlyModelName is not downloaded on this device yet.")
                            }
                            if (hasExistingOtherGguf) {
                                append(" Downloading it will replace the existing offline GGUF model on this device.")
                            }
                        },
                        fontSize = 13.sp
                    )
                    if (pendingPrompt.availableStorageBytes >= 0L) {
                        Text(
                            text = if (hasInsufficientStorage) {
                                "⚠️ Insufficient storage: needs ${AeginBridge.formatByteSize(remainingBytes)} more, but only ${AeginBridge.formatByteSize(pendingPrompt.availableStorageBytes)} is free."
                            } else if (isStorageTight) {
                                "⚠️ Free storage: ${AeginBridge.formatByteSize(pendingPrompt.availableStorageBytes)} (tight for offline AI; ${AeginBridge.formatByteSize(remainingBytes + 1_500_000_000L)} recommended)."
                            } else {
                                "Free device storage: ${AeginBridge.formatByteSize(pendingPrompt.availableStorageBytes)}"
                            },
                            color = when {
                                hasInsufficientStorage -> theme.danger
                                isStorageTight -> Color(0xFFF59E0B)
                                else -> theme.subText
                            },
                            fontSize = 12.sp
                        )
                    }
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
                    enabled = pendingPrompt.downloadUrl.isNotBlank() && !hasInsufficientStorage,
                    onClick = {
                        val targetModel = pendingPrompt.modelName
                        pendingModelDownloadPrompt = null
                        startLocalModelDownload(targetModel)
                    }
                ) {
                    Text(if (pendingPrompt.partialBytes > 0L) "Resume download" else "Download now")
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
                            text = AeginDisplayMapper.providerDisplayName(selectedProvider),
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
                text = "'$selectedProvider' is not verified yet — configure and pass Test connection under AI Setup, or choose Auto.",
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
                        badgeText = "Auto"
                        badgeColor = theme.cyan
                    }
                    item == "Disable" -> {
                        badgeText = "Off"
                        badgeColor = theme.subText
                    }
                    item == "+ New" -> {
                        badgeText = "New"
                        badgeColor = theme.cyan
                    }
                    item == "Custom" -> {
                        badgeText = null
                        badgeColor = theme.subText
                    }
                    tested -> {
                        badgeText = "Verified"
                        badgeColor = theme.success
                    }
                    configured -> {
                        badgeText = "Needs Test"
                        badgeColor = Color(0xFFF59E0B)
                    }
                    else -> {
                        badgeText = "Setup Needed"
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
                    Text(
                        text = if (item == "+ New") "+ Add New AI" else AeginDisplayMapper.providerDisplayName(item),
                        color = theme.text,
                        fontSize = 14.sp
                    )
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
private fun AiSetupCard(
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
    onCancelLocalDownload: () -> Unit,
    onOpenAddNewModal: () -> Unit,
    workerProvider: String,
    governanceProvider: String,
    isLoadingProviders: Boolean,
    isWorkerVerified: Boolean,
    isGovVerified: Boolean,
    onOpenWorkerSelectModal: () -> Unit,
    onOpenGovSelectModal: () -> Unit,
    onSaveConfiguration: () -> Unit,
    capabilities: DeviceCapabilityReport = DeviceCapabilityReport(),
    localModelCatalog: List<LocalModelManifestEntry> = emptyList(),
    isInstallingRuntime: Boolean = false,
    onInstallLocalRuntime: () -> Unit = {}
) {
    val theme = LocalAeginColors.current
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Configure AI Providers",
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
            text = "Choose an AI provider to edit its properties.",
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
                        text = if (activeName.isNotBlank()) AeginDisplayMapper.providerDisplayName(activeName) else "Select AI Model",
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
            val statusText = when {
                isTested -> "Ready to use"
                isConfigured -> "Test required"
                else -> "Setup required"
            }
            val statusColor = when {
                isTested -> theme.success
                isConfigured -> Color(0xFFF59E0B)
                else -> theme.danger
            }

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (isLocal && activeModel.model.isNotBlank()) {
                Text(
                    text = "GGUF Profile: ${AeginDisplayMapper.modelDisplayName(activeModel.model)}",
                    color = theme.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (activeModel.intentOptions.isNotEmpty()) {
                val friendlyCapabilities = activeModel.intentOptions.joinToString(", ") {
                    AeginBridge.formatIntentLabel(it)
                }
                Text(
                    text = "Capabilities: $friendlyCapabilities",
                    color = theme.subText,
                    fontSize = 11.sp,
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (modelDownloadState.status == "DOWNLOADING") {
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, theme.danger, RoundedCornerShape(6.dp))
                                .clickable { onCancelLocalDownload() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("✕ Cancel", color = theme.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (isLocal && modelDownloadState.status == "FAILED" && modelDownloadState.message.isNotBlank()) {
                Text(
                    text = modelDownloadState.message,
                    color = theme.danger,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else if (isLocal && modelDownloadState.status == "PAUSED" && modelDownloadState.message.isNotBlank()) {
                Text(
                    text = modelDownloadState.message,
                    color = theme.warning,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            if (isLocal) {
                val activeLocalFilename = activeModel.model.ifBlank { recommendedLocalModel }
                val catalogMatch = localModelCatalog.firstOrNull {
                    it.effectiveFilename().equals(activeLocalFilename, ignoreCase = true)
                }
                val cautionText = catalogMatch?.resolvedCautionMessage()
                    ?: ModelTierCatalog.cautionMessageForModel(activeLocalFilename)
                if (!capabilities.canRunLocalGguf) {
                    Text(
                        text = "⚠️ ${capabilities.statusSummary}",
                        color = theme.warning,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                } else if (cautionText.isNotBlank()) {
                    Text(
                        text = "⚠️ $cautionText",
                        color = theme.warning,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLocal && !capabilities.canRunLocalGguf &&
                capabilities.statusSummary.contains("llama-server", ignoreCase = true)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isInstallingRuntime) theme.border else theme.primary)
                        .clickable(enabled = !isInstallingRuntime) { onInstallLocalRuntime() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isInstallingRuntime) "Installing Runtime..." else "⬇️ Install Runtime",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (isLocal &&
                capabilities.canRunLocalGguf &&
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
                        text = if (partial > 0L) "▶️ Resume (${AeginBridge.formatByteSize(partial)})" else "⬇️ Download Offline AI",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (partial > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, theme.danger, RoundedCornerShape(8.dp))
                            .clickable { onCancelLocalDownload() }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "✕ Cancel",
                            color = theme.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                            else -> "🧪 Test connection"
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
                    text = "➕ New",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Assign Autonomous AI Providers",
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
            label = "Task Execution AI",
            description = "Default model for automated tasks and smart routing.",
            selectedProvider = workerProvider,
            isLoading = isLoadingProviders,
            isVerified = isWorkerVerified,
            onOpenSelectModal = onOpenWorkerSelectModal
        )

        ProviderSelectorRow(
            label = "Safety Guard AI",
            description = "Reviews requests and tasks for safety and policy risks (prefers private on-device or self-hosted models).",
            selectedProvider = governanceProvider,
            isLoading = isLoadingProviders,
            isVerified = isGovVerified,
            onOpenSelectModal = onOpenGovSelectModal
        )

        val localProviderCfg = models.find { it.name.equals("On-Device Model", ignoreCase = true) }
        val localFile = localProviderCfg?.model?.ifBlank { recommendedLocalModel }.orEmpty()
        val localSafetyCapable = localModelCatalog.firstOrNull {
            it.effectiveFilename().equals(localFile, ignoreCase = true)
        }?.safetyReviewCapable ?: ModelTierCatalog.isSafetyReviewCapableModel(localFile)
        val hasSelfHosted = models.any { it.type.equals("Self-Hosted", ignoreCase = true) && isProviderConfigured(it.name) }
        val selectedGovCfg = models.find { it.name.equals(governanceProvider, ignoreCase = true) }
        val govNotice: Pair<String, Color>? = when {
            governanceProvider.equals("On-Device Model", ignoreCase = true) && !localSafetyCapable ->
                "⚠️ Embedded model (${AeginDisplayMapper.modelDisplayName(localFile)}) is not powerful enough for reliable AI Safety Review. Deterministic policy guardrails remain active; set to 'Disable' or connect a Self-Hosted private-network model." to theme.warning
            selectedGovCfg != null && selectedGovCfg.type.equals("Cloud", ignoreCase = true) ->
                "ℹ️ Privacy notice: Using cloud provider '${selectedGovCfg.name}' for Safety Review sends prompts off-device. For full privacy, use a Self-Hosted model on your private network or a 7B+ desktop local model." to theme.subText
            (governanceProvider.equals("Disable", ignoreCase = true) ||
                (governanceProvider.equals("Auto", ignoreCase = true) && !localSafetyCapable && !hasSelfHosted)) ->
                "🛡️ Deterministic policy guardrails remain active on-device. Connect a Self-Hosted private-network model or a 7B+ desktop local model to enable private AI Safety Review." to theme.subText
            else -> null
        }
        if (govNotice != null) {
            Text(
                text = govNotice.first,
                color = govNotice.second,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(theme.primaryGradient)
                .clickable { onSaveConfiguration() }
                .pillMinWidthAtLeastHeight()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save AI configuration",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isPublicCloudEndpoint(endpoint: String): Boolean {
    val lower = endpoint.trim().lowercase()
    if (lower.isEmpty()) return false
    return lower.contains("api.openai.com") ||
        lower.contains("generativelanguage.googleapis.com") ||
        lower.contains("api.deepseek.com") ||
        lower.contains("dashscope.aliyuncs.com") ||
        lower.contains("api.anthropic.com") ||
        lower.contains("api.groq.com") ||
        lower.contains("api.mistral.ai") ||
        lower.contains("openrouter.ai")
}

private fun mergeLocalGgufChoices(
    catalog: List<LocalModelManifestEntry>,
    providerOptions: List<String>,
    currentModel: String
): List<LocalModelManifestEntry> {
    val result = mutableListOf<LocalModelManifestEntry>()
    val seen = mutableSetOf<String>()
    for (entry in catalog) {
        val fname = entry.effectiveFilename().trim()
        if (fname.isNotEmpty() && seen.add(fname.lowercase())) {
            result.add(entry.copy(filename = fname))
        }
    }
    // When platform-filtered manifest catalog is available, avoid appending off-target models
    // (e.g. Android-only 0.5B/1.5B entries) unless the user has explicitly selected one.
    val extras = if (result.isNotEmpty()) listOf(currentModel) else (providerOptions + listOf(currentModel))
    for (raw in extras) {
        val fname = raw.trim()
        if (fname.endsWith(".gguf", ignoreCase = true) && seen.add(fname.lowercase())) {
            result.add(
                LocalModelManifestEntry(
                    modelId = fname.removeSuffix(".gguf"),
                    filename = fname
                )
            )
        }
    }
    return result
}

@Composable
private fun EditProviderDialog(
    editData: LlmProviderConfig,
    isAddingNewProvider: Boolean,
    hasExistingSecretRef: Boolean,
    isTestingConnection: Boolean,
    testFeedback: Pair<Boolean, String>?,
    modelDownloadState: LocalModelDownloadState,
    localModelCatalog: List<LocalModelManifestEntry>,
    stagedGgufNames: List<String>,
    localModelCheck: LocalModelCheckResult?,
    recommendedLocalModel: String,
    capabilities: DeviceCapabilityReport = DeviceCapabilityReport(),
    isInstallingRuntime: Boolean = false,
    detectedLocalApps: List<com.reveila.aegin.bridge.DetectedLocalLlmApp> = emptyList(),
    onConnectDetectedApp: (com.reveila.aegin.bridge.DetectedLocalLlmApp) -> Unit = {},
    onInstallLocalRuntime: () -> Unit = {},
    onEditDataChange: (LlmProviderConfig) -> Unit,
    onTestConnection: () -> Unit,
    onDownloadLocalModel: () -> Unit,
    onPauseLocalDownload: () -> Unit,
    onCancelLocalDownload: () -> Unit,
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
                    isAddingNewProvider -> "➕ Add Cloud or Custom AI"
                    isDeviceModel -> "📱 On-Device Model"
                    else -> "☁️ Configure ${editData.name}"
                },
                color = theme.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (isDeviceModel) {
                val isDlRunning = modelDownloadState.status == "DOWNLOADING" || modelDownloadState.status == "VERIFYING"
                val isReadyLocally = localModelCheck?.existsLocally == true &&
                    localModelCheck.modelName.equals(editData.model.ifBlank { recommendedLocalModel }, ignoreCase = true)
                val targetName = editData.model.ifBlank { recommendedLocalModel }
                val hasOtherStaged = stagedGgufNames.any { !it.equals(targetName, ignoreCase = true) }
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
                        text = "🔒 On-Device Local AI",
                        color = theme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Runs offline directly on this device for full privacy, unlike cloud providers that send requests off-device.",
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
                                text = "${AeginDisplayMapper.downloadStatus(modelDownloadState.status)} ${modelDownloadState.percent}%",
                                color = theme.cyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (modelDownloadState.status == "DOWNLOADING") {
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .border(1.dp, theme.danger, RoundedCornerShape(6.dp))
                                        .clickable { onCancelLocalDownload() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("✕ Cancel", color = theme.danger, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (!capabilities.canRunLocalGguf) {
                        Text(
                            text = "⚠️ ${capabilities.statusSummary}",
                            color = theme.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        if (capabilities.statusSummary.contains("llama-server", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isInstallingRuntime) theme.border else theme.primary)
                                    .clickable(enabled = !isInstallingRuntime) { onInstallLocalRuntime() }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isInstallingRuntime) "Installing Local AI Runtime..." else "⬇️ Install Local AI Runtime (llama-server)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (!isReadyLocally) {
                        val partial = localModelCheck?.partialBytes ?: 0L
                        val friendlyTarget = AeginDisplayMapper.modelDisplayName(targetName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    partial > 0L -> "Partial '$friendlyTarget' (${AeginBridge.formatByteSize(partial)})"
                                    hasOtherStaged -> "$friendlyTarget not downloaded yet (replaces existing model)."
                                    else -> "$friendlyTarget not downloaded locally."
                                },
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.cyan)
                                        .clickable { onDownloadLocalModel() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (partial > 0L) "▶️ Resume" else "⬇️ Download",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (partial > 0L) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, theme.danger, RoundedCornerShape(6.dp))
                                            .clickable { onCancelLocalDownload() }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "✕ Cancel",
                                            color = theme.danger,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "✓ Offline AI model and runtime are ready on this device.",
                            color = theme.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val selectedEntry = localModelCatalog.firstOrNull {
                        it.effectiveFilename().equals(targetName, ignoreCase = true)
                    }
                    val cautionMsg = selectedEntry?.resolvedCautionMessage()
                        ?: ModelTierCatalog.cautionMessageForModel(targetName)
                    if (capabilities.canRunLocalGguf && cautionMsg.isNotBlank()) {
                        Text(
                            text = "⚠️ $cautionMsg",
                            color = theme.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (detectedLocalApps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.success.copy(alpha = 0.10f))
                            .border(1.dp, theme.success.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "✨ Already Running a Local AI App?",
                            color = theme.success,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Connect directly without downloading duplicate model files:",
                            color = theme.subText,
                            fontSize = 10.sp
                        )
                        detectedLocalApps.forEach { app ->
                            val modelHint = if (app.models.isNotEmpty()) {
                                "${app.models.first()} (${app.models.size} available)"
                            } else {
                                "Ready on localhost"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        color = theme.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = modelHint,
                                        color = theme.subText,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.success)
                                        .clickable { onConnectDetectedApp(app) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Connect", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                            placeholder = { Text("e.g. Anthropic, OpenRouter, Ollama Server", color = theme.subText, fontSize = 13.sp) }
                        )
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
                        text = "☁️ Cloud / Off-Device AI",
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSelfHosted) {
                            "Runs off-device via an external endpoint instead of the on-device local model. API key is optional."
                        } else {
                            "Runs off-device via ${editData.name.ifBlank { "this cloud service" }} instead of the on-device local model. API key required."
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isDeviceModel) "OFFLINE AI MODEL (GGUF)" else "MODEL NAME",
                    color = theme.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                if (isDeviceModel) {
                    var isModelDropdownExpanded by remember { mutableStateOf(false) }
                    val ggufChoices = remember(localModelCatalog, editData.modelOptions, editData.model) {
                        mergeLocalGgufChoices(localModelCatalog, editData.modelOptions, editData.model)
                    }
                    val currentFilename = editData.model.ifBlank { recommendedLocalModel }
                    val currentEntry = ggufChoices.firstOrNull {
                        it.effectiveFilename().equals(currentFilename, ignoreCase = true)
                    }
                    val currentTitle = currentEntry?.displayName?.ifBlank { null }
                        ?: AeginDisplayMapper.modelDisplayName(currentFilename)
                    val currentSize = currentEntry?.approxSizeBytes?.takeIf { it > 0L }
                        ?.let { " • ${AeginBridge.formatByteSize(it)}" } ?: ""
                    val currentRam = currentEntry?.minRamGb?.takeIf { it > 0.0 }
                        ?.let { " • ${it.toInt()} GB+ RAM" } ?: ""

                    // Compact Dropdown Trigger
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(theme.inputBg)
                            .border(
                                width = 1.dp,
                                color = if (isModelDropdownExpanded) theme.primary else theme.border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { isModelDropdownExpanded = !isModelDropdownExpanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                color = theme.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$currentFilename$currentSize$currentRam",
                                color = theme.subText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isModelDropdownExpanded) "▴" else "▾",
                            color = theme.subText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Expandable Dropdown Options
                    if (isModelDropdownExpanded && ggufChoices.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.bg)
                                .border(1.dp, theme.border, RoundedCornerShape(10.dp))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ggufChoices.forEach { entry ->
                                val fname = entry.effectiveFilename()
                                val isSelected = currentFilename.equals(fname, ignoreCase = true)
                                val isStaged = stagedGgufNames.any { it.equals(fname, ignoreCase = true) } ||
                                    (localModelCheck?.existsLocally == true &&
                                        localModelCheck.modelName.equals(fname, ignoreCase = true))
                                val sizeText = if (entry.approxSizeBytes > 0L) {
                                    " • ${AeginBridge.formatByteSize(entry.approxSizeBytes)}"
                                } else {
                                    ""
                                }
                                val ramCompatible = entry.isCompatibleWithRam(capabilities.totalRamGb)
                                val ramHint = if (entry.minRamGb > 0.0) " • ${entry.minRamGb.toInt()} GB+ RAM" else ""
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                !ramCompatible -> theme.inputBg.copy(alpha = 0.4f)
                                                isSelected -> theme.primary.copy(alpha = 0.14f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = ramCompatible) {
                                            isModelDropdownExpanded = false
                                            onEditDataChange(editData.copy(model = fname))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = entry.displayName.ifBlank { AeginDisplayMapper.modelDisplayName(fname) },
                                            color = when {
                                                !ramCompatible -> theme.subText
                                                isSelected -> theme.primary
                                                else -> theme.text
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$fname$sizeText$ramHint",
                                            color = theme.subText,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val badgeLabel = when {
                                        !ramCompatible -> "Needs ${entry.minRamGb.toInt()} GB"
                                        isStaged -> "Downloaded"
                                        isSelected -> "✓"
                                        else -> ""
                                    }
                                    if (badgeLabel.isNotEmpty()) {
                                        val badgeColor = when {
                                            !ramCompatible -> theme.warning
                                            isStaged -> theme.success
                                            else -> theme.primary
                                        }
                                        Text(
                                            text = badgeLabel,
                                            color = badgeColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
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
            }

            if (!isDeviceModel) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ENDPOINT URL",
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isDeviceModel) "SUPPORTED ON-DEVICE CAPABILITIES" else "CAPABILITIES",
                    color = theme.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (isDeviceModel) {
                        "Automatically managed for offline on-device models. Cloud-only tasks (Live Web Info, Deep Analysis, Multi-Step Workflow) automatically route to a Cloud or Self-Hosted AI."
                    } else {
                        "Choose what this model can handle. On-Device AI is preferred when available."
                    },
                    color = theme.subText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                val visibleIntents = if (isDeviceModel) {
                    AeginBridge.DEFAULT_LOCAL_INTENTS
                } else {
                    AeginBridge.PREDEFINED_TASK_INTENTS
                }

                visibleIntents.forEach { intentKey ->
                    val isSelected = isDeviceModel || editData.intentOptions.any { it.equals(intentKey, ignoreCase = true) }
                    val isCloudIntent = intentKey.startsWith("CLOUD_")
                    val accentColor = if (isCloudIntent) theme.cyan else theme.success
                    val friendlyLabel = AeginDisplayMapper.taskCategory(intentKey)
                    val intentDesc = AeginDisplayMapper.taskCategoryDescription(intentKey)

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
                            .then(
                                if (!isDeviceModel) {
                                    Modifier.clickable {
                                        val updated = if (isSelected) {
                                            editData.intentOptions.filterNot { it.equals(intentKey, ignoreCase = true) }
                                        } else {
                                            editData.intentOptions + intentKey
                                        }
                                        onEditDataChange(editData.copy(intentOptions = updated))
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = friendlyLabel,
                                color = if (isSelected) accentColor else theme.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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

            val isSelectedLocalReady = localModelCheck?.existsLocally == true &&
                localModelCheck.modelName.equals(editData.model.ifBlank { recommendedLocalModel }, ignoreCase = true)
            val canTestConnection = !isTestingConnection &&
                (!isDeviceModel || (capabilities.canRunLocalGguf && isSelectedLocalReady))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .border(1.dp, if (canTestConnection) theme.cyan else theme.border, CircleShape)
                    .background(if (canTestConnection) theme.cyan.copy(alpha = 0.1f) else theme.inputBg)
                    .clickable(enabled = canTestConnection) { onTestConnection() }
                    .pillMinWidthAtLeastHeight()
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTestingConnection) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = theme.cyan)
                        Text("Testing connection...", color = theme.cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        text = "🧪 Test connection",
                        color = if (canTestConnection) theme.cyan else theme.subText,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.primaryGradient)
                        .clickable(enabled = !isTestingConnection) { onSave() }
                        .pillMinWidthAtLeastHeight()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, theme.border, CircleShape)
                        .clickable(enabled = !isTestingConnection) { onDismiss() }
                        .pillMinWidthAtLeastHeight()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = theme.subText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun AppearanceThemeCard(
    presets: List<AeginBrandPreset>,
    selectedPresetId: String,
    isDarkMode: Boolean,
    onSelectPreset: (String) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit
) {
    val theme = LocalAeginColors.current
    val resolvedTheme = LocalAeginTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "APPEARANCE & BRAND THEME",
                    color = theme.subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Configured via app_theme.json • Font: ${resolvedTheme.typography.fontFamilyName}",
                    color = theme.mutedText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AeginPillButton(
                    text = "Dark",
                    leadingIcon = AeginIconType.MOON,
                    selected = isDarkMode,
                    variant = if (isDarkMode) AeginPillButtonVariant.PRIMARY else AeginPillButtonVariant.SURFACE,
                    onClick = { onToggleDarkMode(true) }
                )
                AeginPillButton(
                    text = "Light",
                    leadingIcon = AeginIconType.SUN,
                    selected = !isDarkMode,
                    variant = if (!isDarkMode) AeginPillButtonVariant.PRIMARY else AeginPillButtonVariant.SURFACE,
                    onClick = { onToggleDarkMode(false) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = preset.id.equals(selectedPresetId, ignoreCase = true)
                val previewColors = preset.forMode(isDarkMode).colors
                Column(
                    modifier = Modifier
                        .width(168.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) theme.primary.copy(alpha = 0.12f) else theme.cardAlt)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) theme.primary else theme.border,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectPreset(preset.id) }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(previewColors.bg)
                                    .border(1.dp, theme.border, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(previewColors.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(previewColors.accent)
                            )
                        }
                        if (isSelected) {
                            AeginVectorIcon(
                                type = AeginIconType.CHECK,
                                tint = theme.primary,
                                size = 13.dp
                            )
                        }
                    }
                    Text(
                        text = preset.displayName,
                        color = theme.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (preset.description.isNotBlank()) {
                        Text(
                            text = preset.description,
                            color = theme.subText,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Compact live preview of pill button states driven by app_theme.json
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AeginPillButton(
                text = "Enabled",
                leadingIcon = AeginIconType.SPARKLES,
                enabled = true,
                onClick = {}
            )
            AeginPillButton(
                text = "Selected",
                leadingIcon = AeginIconType.CHECK,
                enabled = true,
                selected = true,
                onClick = {}
            )
            AeginPillButton(
                text = "Disabled",
                leadingIcon = AeginIconType.SHIELD,
                enabled = false,
                onClick = {}
            )
            AeginPillButton(
                text = "Primary",
                variant = AeginPillButtonVariant.PRIMARY,
                enabled = true,
                onClick = {}
            )
        }
    }
}

@Composable
private fun ProactiveAlertChannelsCard(
    channels: List<com.reveila.aegin.bridge.AlertChannelConfig>,
    appEdition: String,
    testingChannelId: String?,
    onToggleEnabled: (com.reveila.aegin.bridge.AlertChannelConfig, Boolean) -> Unit,
    onConfigure: (com.reveila.aegin.bridge.AlertChannelConfig) -> Unit,
    onAddCustomChannel: () -> Unit,
    onTest: (com.reveila.aegin.bridge.AlertChannelConfig) -> Unit
) {
    val theme = LocalAeginColors.current
    val isEnterprise = appEdition.equals("enterprise", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PROACTIVE ALERTS & CHANNELS",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (isEnterprise) "ENTERPRISE" else "INDIVIDUAL",
                        color = if (isEnterprise) theme.accent else theme.cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = if (isEnterprise) {
                        "Enterprise edition: sign in to IT-approved channels from channels.json. Custom webhooks are disabled."
                    } else {
                        "Connect Slack, Teams, Email, or custom webhooks (Discord, ntfy, Zapier). Secrets are encrypted via SecretManager."
                    },
                    color = theme.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            if (!isEnterprise) {
                AeginPillButton(
                    text = "+ Custom",
                    leadingIcon = AeginIconType.PLUS,
                    onClick = onAddCustomChannel
                )
            }
        }

        channels.forEach { ch ->
            val isConfigured = ch.hasCredentialsConfigured()
            val isTesting = testingChannelId == ch.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.cardAlt)
                    .border(
                        width = 1.dp,
                        color = if (ch.enabled && isConfigured) theme.success.copy(alpha = 0.45f) else theme.border,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ch.displayName(),
                            color = theme.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val badgeText = when {
                            ch.enabled && isConfigured -> "ACTIVE"
                            isConfigured -> "READY"
                            else -> "NOT SET"
                        }
                        val badgeColor = when {
                            ch.enabled && isConfigured -> theme.success
                            isConfigured -> theme.cyan
                            else -> theme.mutedText
                        }
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    val subtitle = when (ch.type.uppercase()) {
                        "SLACK" -> "Channel: ${ch.channel.ifBlank { "#alerts" }} • Min: ${ch.minSeverity}"
                        "MICROSOFT_TEAMS" -> "Adaptive Card Webhook • Min: ${ch.minSeverity}"
                        "EMAIL" -> "${ch.to.joinToString(", ").ifBlank { "No recipients" }} • Min: ${ch.minSeverity}"
                        else -> "Min: ${ch.minSeverity}"
                    }
                    Text(
                        text = subtitle,
                        color = theme.subText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AeginPillButton(
                        text = if (isTesting) "Sending..." else "Test",
                        enabled = ch.enabled && isConfigured && !isTesting,
                        onClick = { onTest(ch) }
                    )
                    AeginPillButton(
                        text = "Configure",
                        variant = if (isConfigured) AeginPillButtonVariant.SURFACE else AeginPillButtonVariant.PRIMARY,
                        onClick = { onConfigure(ch) }
                    )
                    Switch(
                        checked = ch.enabled,
                        onCheckedChange = { onToggleEnabled(ch, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.onPrimary,
                            checkedTrackColor = theme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertChannelConfigDialog(
    initial: com.reveila.aegin.bridge.AlertChannelConfig,
    isNewCustom: Boolean = false,
    canDelete: Boolean = false,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onDelete: (com.reveila.aegin.bridge.AlertChannelConfig) -> Unit = {},
    onSave: (com.reveila.aegin.bridge.AlertChannelConfig) -> Unit
) {
    val theme = LocalAeginColors.current
    val isSlack = initial.type.equals("SLACK", ignoreCase = true)
    val isTeams = initial.type.equals("MICROSOFT_TEAMS", ignoreCase = true)
    val isEmail = initial.type.equals("EMAIL", ignoreCase = true)
    val isCustom = initial.type.equals("CUSTOM_WEBHOOK", ignoreCase = true)

    var channelId by remember(initial) { mutableStateOf(initial.id) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled || !initial.hasCredentialsConfigured()) }
    var endpoint by remember(initial) { mutableStateOf(initial.endpoint) }
    var apiKey by remember(initial) { mutableStateOf(initial.apiKey) }
    var channel by remember(initial) { mutableStateOf(initial.channel) }
    var fromAddr by remember(initial) { mutableStateOf(initial.from) }
    var toCsv by remember(initial) { mutableStateOf(initial.to.joinToString(", ")) }
    var minSeverity by remember(initial) { mutableStateOf(initial.minSeverity.ifBlank { "SUCCESS" }) }

    val severities = listOf("INFO", "SUCCESS", "WARNING", "ACTION_REQUIRED", "CRITICAL")

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(theme.card)
                .border(1.dp, theme.border, RoundedCornerShape(18.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNewCustom) "Add Custom Webhook Channel" else "Configure ${initial.displayName()}",
                    color = theme.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Enabled", color = theme.subText, fontSize = 11.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.onPrimary,
                            checkedTrackColor = theme.primary
                        )
                    )
                }
            }

            if (isNewCustom) {
                OutlinedTextField(
                    value = channelId,
                    onValueChange = { channelId = it },
                    label = { Text("Channel ID (e.g. discord-home, ntfy-phone)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = {
                    Text(
                        when {
                            isSlack -> "Slack Webhook URL (or https://slack.com/api/chat.postMessage)"
                            isTeams -> "Microsoft Teams Webhook / Workflow URL"
                            isCustom -> "Custom Webhook URL (Discord, ntfy, Zapier, etc.)"
                            else -> "Email Gateway Endpoint URL (e.g. Resend / SendGrid)"
                        },
                        fontSize = 11.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.text,
                    unfocusedTextColor = theme.text,
                    focusedContainerColor = theme.inputBg,
                    unfocusedContainerColor = theme.inputBg,
                    focusedBorderColor = theme.primary,
                    unfocusedBorderColor = theme.border
                )
            )

            if (isSlack) {
                OutlinedTextField(
                    value = channel,
                    onValueChange = { channel = it },
                    label = { Text("Target Channel (e.g. #alerts)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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

            if (isSlack || isEmail || isCustom) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            when {
                                isSlack -> "Bot Token (Optional if using Incoming Webhook)"
                                isCustom -> "Bearer Token / Secret (Optional)"
                                else -> "Email Gateway API Key"
                            },
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (apiKey.startsWith("REF:")) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
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

            if (isEmail) {
                OutlinedTextField(
                    value = fromAddr,
                    onValueChange = { fromAddr = it },
                    label = { Text("From Address (e.g. alerts@yourdomain.com)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                    value = toCsv,
                    onValueChange = { toCsv = it },
                    label = { Text("Recipient Emails (comma-separated)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "Minimum Alert Severity",
                    color = theme.subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    severities.forEach { sev ->
                        AeginPillButton(
                            text = sev,
                            selected = minSeverity.equals(sev, ignoreCase = true),
                            variant = if (minSeverity.equals(sev, ignoreCase = true)) {
                                AeginPillButtonVariant.PRIMARY
                            } else {
                                AeginPillButtonVariant.SURFACE
                            },
                            onClick = { minSeverity = sev }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AeginPillButton(
                    text = if (isSaving) "Saving..." else "Save Credentials",
                    variant = AeginPillButtonVariant.PRIMARY,
                    enabled = !isSaving,
                    onClick = {
                        val parsedTo = toCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(
                            initial.copy(
                                id = channelId.trim().ifBlank { initial.id },
                                enabled = enabled,
                                endpoint = endpoint.trim(),
                                apiKey = apiKey.trim(),
                                channel = channel.trim(),
                                from = fromAddr.trim(),
                                to = parsedTo,
                                minSeverity = minSeverity
                            )
                        )
                    }
                )
                AeginPillButton(
                    text = "Cancel",
                    enabled = !isSaving,
                    onClick = onDismiss
                )
            }
            if (canDelete) {
                AeginPillButton(
                    text = "Remove Custom Channel",
                    enabled = !isSaving,
                    onClick = { onDelete(initial) }
                )
            }
        }
    }
}

@Composable
private fun GovernancePolicyEditorCard(
    policy: com.reveila.aegin.bridge.GovernancePolicyConfig,
    appEdition: String,
    isSaving: Boolean,
    onSavePolicy: (com.reveila.aegin.bridge.GovernancePolicyConfig) -> Unit,
    onResetDefaults: () -> Unit
) {
    val theme = LocalAeginColors.current
    val isEnterprise = appEdition.equals("enterprise", ignoreCase = true)

    var draftKeywords by remember(policy) { mutableStateOf(policy.restrictedKeywords) }
    var newKeywordDraft by remember { mutableStateOf("") }
    var alertThreshold by remember(policy) { mutableStateOf(policy.alertThreshold) }
    var hitlThreshold by remember(policy) { mutableStateOf(policy.hitlApprovalThreshold) }
    var blockThreshold by remember(policy) { mutableStateOf(policy.blockThreshold) }
    var retentionDays by remember(policy) { mutableStateOf(policy.auditLogRetentionDays) }

    val activePreset = when {
        hitlThreshold <= 0.68 -> "STRICT"
        hitlThreshold >= 0.88 -> "RELAXED"
        else -> "BALANCED"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "GOVERNANCE POLICY",
                        color = theme.subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = if (isEnterprise) "ENTERPRISE • READ-ONLY" else "INDIVIDUAL • EDITABLE",
                        color = if (isEnterprise) theme.accent else theme.success,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = if (isEnterprise) {
                        "Managed by Enterprise IT policy (${policy.policyName}). Local edits are disabled."
                    } else {
                        "Tailor restricted keywords & safety sensitivity for your personal workflow. Core prompt-injection and destructive-command guardrails remain protected."
                    },
                    color = theme.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Core protected guardrails summary
        Text(
            text = "Protected core rules: ${policy.promptInjectionPatternCount} prompt-injection patterns • ${policy.prohibitedCommandCount} blocked shell commands • ${policy.sensitiveDataPatternCount} DLP patterns",
            color = theme.subText,
            fontSize = 10.sp
        )

        // Safety sensitivity presets
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Safety Sensitivity (Alert ${(alertThreshold * 100).toInt()}% • Approval ${(hitlThreshold * 100).toInt()}% • Block ${(blockThreshold * 100).toInt()}%)",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("STRICT", "Strict", Triple(0.55, 0.65, 0.85)),
                    Triple("BALANCED", "Balanced", Triple(0.75, 0.80, 0.95)),
                    Triple("RELAXED", "Relaxed", Triple(0.85, 0.90, 0.98))
                ).forEach { (key, label, values) ->
                    val selected = activePreset == key
                    AeginPillButton(
                        text = label,
                        enabled = !isEnterprise && !isSaving,
                        selected = selected,
                        variant = if (selected) AeginPillButtonVariant.PRIMARY else AeginPillButtonVariant.SURFACE,
                        onClick = {
                            alertThreshold = values.first
                            hitlThreshold = values.second
                            blockThreshold = values.third
                        }
                    )
                }
            }
        }

        // Audit log retention
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Audit Log Retention ($retentionDays days)",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 90, 365).forEach { days ->
                    val selected = retentionDays == days
                    AeginPillButton(
                        text = "${days}d",
                        enabled = !isEnterprise && !isSaving,
                        selected = selected,
                        variant = if (selected) AeginPillButtonVariant.PRIMARY else AeginPillButtonVariant.SURFACE,
                        onClick = { retentionDays = days }
                    )
                }
            }
        }

        // Restricted keywords
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Restricted Keywords (${draftKeywords.size})",
                color = theme.subText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (draftKeywords.isEmpty()) {
                Text("No custom restricted keywords.", color = theme.mutedText, fontSize = 11.sp)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    draftKeywords.forEach { kw ->
                        AeginPillButton(
                            text = if (isEnterprise) kw else "$kw ×",
                            enabled = !isEnterprise && !isSaving,
                            onClick = {
                                draftKeywords = draftKeywords.filterNot { it.equals(kw, ignoreCase = true) }
                            }
                        )
                    }
                }
            }

            if (!isEnterprise) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeywordDraft,
                        onValueChange = { newKeywordDraft = it },
                        placeholder = { Text("Add keyword (e.g. TAX_ID, SECRET_PROJ)", color = theme.mutedText, fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            focusedContainerColor = theme.inputBg,
                            unfocusedContainerColor = theme.inputBg,
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.border
                        )
                    )
                    AeginPillButton(
                        text = "Add",
                        leadingIcon = AeginIconType.PLUS,
                        enabled = !isSaving && newKeywordDraft.trim().isNotEmpty(),
                        onClick = {
                            val clean = newKeywordDraft.trim().uppercase()
                            if (clean.isNotEmpty() && draftKeywords.none { it.equals(clean, ignoreCase = true) }) {
                                draftKeywords = draftKeywords + clean
                            }
                            newKeywordDraft = ""
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AeginPillButton(
                        text = if (isSaving) "Saving..." else "Save Policy",
                        variant = AeginPillButtonVariant.PRIMARY,
                        enabled = !isSaving,
                        onClick = {
                            onSavePolicy(
                                policy.copy(
                                    restrictedKeywords = draftKeywords,
                                    alertThreshold = alertThreshold,
                                    hitlApprovalThreshold = hitlThreshold,
                                    blockThreshold = blockThreshold,
                                    auditLogRetentionDays = retentionDays
                                )
                            )
                        }
                    )
                    AeginPillButton(
                        text = "Reset Defaults",
                        enabled = !isSaving,
                        onClick = onResetDefaults
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopCompanionPairingCard(
    pairedStatus: com.reveila.aegin.bridge.PairedClusterStatus,
    desktopRemoteUrlDraft: String,
    onDesktopRemoteUrlChange: (String) -> Unit,
    pairingInputDraft: String,
    onPairingInputChange: (String) -> Unit,
    isPairingBusy: Boolean,
    onEnableDesktopLead: () -> Unit,
    onDisablePairedMode: () -> Unit,
    onScanWifi: () -> Unit,
    onPairDesktop: () -> Unit
) {
    val theme = LocalAeginColors.current
    val clipboardManager = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(copiedFeedback) {
        if (copiedFeedback) {
            delay(2000L)
            copiedFeedback = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(theme.card)
            .border(1.dp, theme.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CLUSTER & NODE CONNECTION",
                    color = theme.subText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = pairedStatus.statusMessage,
                    color = theme.mutedText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
            val isLinked = pairedStatus.isServerRunning || pairedStatus.mode == "MOBILE_PAIRED"
            Text(
                text = if (isLinked) "CLUSTER ACTIVE" else "STANDALONE",
                color = if (isLinked) theme.success else theme.mutedText,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (pairedStatus.isDesktopHost) {
            OutlinedTextField(
                value = desktopRemoteUrlDraft,
                onValueChange = onDesktopRemoteUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Optional Remote URL (Tailscale / Relay)", fontSize = 11.sp) },
                placeholder = { Text("e.g. http://my-pc.tailnet:8080", fontSize = 11.sp) },
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
                AeginPillButton(
                    text = if (pairedStatus.isServerRunning) "Refresh Cluster" else "Host Cluster Lead",
                    variant = AeginPillButtonVariant.PRIMARY,
                    enabled = !isPairingBusy,
                    onClick = onEnableDesktopLead
                )
                if (pairedStatus.isServerRunning) {
                    AeginPillButton(
                        text = "Leave Cluster",
                        enabled = !isPairingBusy,
                        onClick = onDisablePairedMode
                    )
                }
            }

            if (pairedStatus.pairingPayloadJson.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLUSTER JOIN CODE (PASTE ON EDGE / WORKER)",
                        color = theme.subText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    AeginPillButton(
                        text = if (copiedFeedback) "Copied ✓" else "Copy",
                        leadingIcon = if (copiedFeedback) AeginIconType.CHECK else null,
                        variant = if (copiedFeedback) AeginPillButtonVariant.PRIMARY else AeginPillButtonVariant.SURFACE,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(pairedStatus.pairingPayloadJson))
                            copiedFeedback = true
                        }
                    )
                }
                SelectionContainer {
                    Text(
                        text = pairedStatus.pairingPayloadJson,
                        color = theme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.inputBg)
                            .border(1.dp, theme.border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = pairingInputDraft,
                onValueChange = onPairingInputChange,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                label = { Text("Cluster Leader URL or Join Code", fontSize = 11.sp) },
                placeholder = { Text("Paste join code, LAN URL, or https://my-server.example.com", fontSize = 11.sp) },
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
                AeginPillButton(
                    text = "Scan Wi-Fi",
                    enabled = !isPairingBusy,
                    onClick = onScanWifi
                )
                AeginPillButton(
                    text = "Join Cluster",
                    variant = AeginPillButtonVariant.PRIMARY,
                    enabled = !isPairingBusy && pairingInputDraft.isNotBlank(),
                    onClick = onPairDesktop
                )
            }
            if (pairedStatus.mode == "MOBILE_PAIRED") {
                AeginPillButton(
                    text = "Disconnect (Standalone)",
                    enabled = !isPairingBusy,
                    onClick = onDisablePairedMode
                )
            }
        }
    }
}
