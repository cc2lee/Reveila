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
import com.reveila.aegin.ui.theme.AeginIconType
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
                            "General" -> "Settings"
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
                    localModelCatalog = localModelCatalog
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
                                    text = "Save & re-index",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
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
                    Text(
                        text = "Processor: ${capabilities.cpuAbi} • Fast Math: ${if (capabilities.supportsSimdDotProd) "Yes" else "No"} • GPU Acceleration: ${if (capabilities.supportsVulkanCompute) "Available" else "Not Available"}",
                        color = theme.subText,
                        fontSize = 12.sp
                    )
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
                                        showAlert("Index sync", "Personal Knowledge Folder scan completed successfully.")
                                    } catch (e: Exception) {
                                        showAlert("Scan failed", e.message ?: "Error triggering scan")
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
    localModelCatalog: List<LocalModelManifestEntry> = emptyList()
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
                .fillMaxWidth()
                .clip(CircleShape)
                .background(theme.primaryGradient)
                .clickable { onSaveConfiguration() }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Save AI configuration",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
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
    for (raw in providerOptions + listOf(currentModel)) {
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
                            text = "✓ Offline AI model is ready on this device.",
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
                    if (!capabilities.canRunLocalGguf) {
                        Text(
                            text = "⚠️ ${capabilities.statusSummary}",
                            color = theme.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    } else if (cautionMsg.isNotBlank()) {
                        Text(
                            text = "⚠️ $cautionMsg",
                            color = theme.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
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
                    val ggufChoices = remember(localModelCatalog, editData.modelOptions, editData.model) {
                        mergeLocalGgufChoices(localModelCatalog, editData.modelOptions, editData.model)
                    }
                    val hasAnyStagedGguf = stagedGgufNames.any { it.endsWith(".gguf", ignoreCase = true) } ||
                        (localModelCheck?.existsLocally == true)
                    if (ggufChoices.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ggufChoices.forEach { entry ->
                                val fname = entry.effectiveFilename()
                                val isSelected = editData.model.equals(fname, ignoreCase = true)
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
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                !ramCompatible -> theme.inputBg.copy(alpha = 0.5f)
                                                isSelected -> theme.primary.copy(alpha = 0.12f)
                                                else -> theme.inputBg
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) theme.primary else theme.border,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable(enabled = ramCompatible) {
                                            onEditDataChange(editData.copy(model = fname))
                                        }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
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
                                        isSelected && hasAnyStagedGguf -> "Replace"
                                        isSelected -> "Selected"
                                        else -> "Choose"
                                    }
                                    val badgeColor = when {
                                        !ramCompatible -> theme.warning
                                        isStaged -> theme.success
                                        isSelected -> theme.primary
                                        else -> theme.subText
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeColor.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
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
                    OutlinedTextField(
                        value = editData.model,
                        onValueChange = { onEditDataChange(editData.copy(model = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Custom .gguf filename", color = theme.subText, fontSize = 12.sp) }
                    )
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isDeviceModel) "LOCAL ENGINE ADDRESS" else "ENDPOINT URL",
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CAPABILITIES",
                    color = theme.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Choose what this model can handle. On-Device AI is preferred when available.",
                    color = theme.subText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                AeginBridge.PREDEFINED_TASK_INTENTS.forEach { intentKey ->
                    val isSelected = editData.intentOptions.any { it.equals(intentKey, ignoreCase = true) }
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
                        Text("Testing connection...", color = theme.cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    Text("🧪 Test connection", color = theme.cyan, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
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
