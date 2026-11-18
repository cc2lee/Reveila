package com.reveila.aegin.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDefaults

actual object PlatformEngineBridge {
    private val modelDownloadState = MutableStateFlow(LocalModelDownloadState())
    private var initialized = false
    private var suspended = false
    private var docCount = 1
    private var chunkCount = 3
    private var llmSettingsJson = """
        {
          "ai.worker.llm": "On-Device Model",
          "ai.governance.llm": "On-Device Model"
        }
    """.trimIndent()

    actual suspend fun startService(systemHome: String?): Boolean {
        initialized = true
        return true
    }

    actual suspend fun getEngineStatus(): EngineStatus {
        initialized = true
        return EngineStatus(
            initialized = true,
            status = "ONLINE",
            platform = "iOS",
            storageMountPath = "${NSHomeDirectory()}/Documents/reveila"
        )
    }

    actual suspend fun getDeviceCapabilities(): DeviceCapabilityReport {
        return DeviceCapabilityReport(
            totalRamGb = 8.0,
            availableRamGb = 4.2,
            isLowMemory = false,
            freeDiskGb = 32.0,
            hasEnoughStorage = true,
            cpuAbi = "arm64",
            supportsSimdDotProd = true,
            supportsFp16 = true,
            supportsVulkanCompute = true,
            hardwareTier = "STANDARD TIER (8 – 12 GB RAM)",
            recommendedModel = "qwen2.5-3b-instruct-q4_k_m.gguf",
            recommendedModelSizeMb = 2200,
            canRunLocalGguf = true,
            modelsStagingPath = "${NSHomeDirectory()}/Library/Application Support/reveila/models",
            stagedModels = emptyList(),
            statusSummary = "iOS ARM64 Metal/NEON verified. Recommended profile: qwen2.5-3b-instruct-q4_k_m.gguf."
        )
    }

    actual suspend fun invoke(component: String, method: String, params: List<String>): String {
        return when (component) {
            "OrchestrationService" -> when (method) {
                "isRunning" -> initialized.toString()
                "isSuspended" -> suspended.toString()
                "toggleSuspend" -> {
                    suspended = params.firstOrNull()?.toBoolean() ?: false
                    suspended.toString()
                }
                "triggerVaultScan" -> "true"
                "resetApplication" -> {
                    clearStorage()
                    "true"
                }
                else -> "true"
            }
            "LlmProviderFactory" -> when (method) {
                "getSettings" -> llmSettingsJson
                "setSettings", "saveSettings" -> {
                    if (params.size > 1) llmSettingsJson = params[1]
                    "true"
                }
                "testProviderConfigJson" -> """{"ok":true,"message":"Verified (simulated iOS bridge).","fingerprint":"ios-verified"}"""
                else -> "{}"
            }
            "AgenticFabric" -> {
                val prompt = params.firstOrNull() ?: ""
                val sessionId = params.getOrNull(1)?.ifBlank { null } ?: "ios-session-1"
                """{"answer":"Sovereign iOS Agent Response: Processed \"$prompt\" against local Reference Documents ($chunkCount vector chunks).","sessionId":"$sessionId"}"""
            }
            else -> "{}"
        }
    }

    actual suspend fun authenticateBiometric(): Boolean = true

    actual fun saveSensitiveData(data: String) {
        setStorageItem("sensitive_vault_secret", data)
    }

    actual suspend fun triggerEmergencyStop(): Boolean {
        initialized = false
        return true
    }

    actual suspend fun setVaultFolder(folderPath: String, focusKeywords: String): VaultScanResult {
        setStorageItem("vault_folder_path", folderPath)
        if (focusKeywords.isNotBlank()) setStorageItem("vault_focus_keywords", focusKeywords)
        return scanVault()
    }

    actual suspend fun getVaultFolder(): VaultFolderConfig {
        return VaultFolderConfig(
            folder = getStorageItem("vault_folder_path") ?: "Documents/Reveila",
            focusKeywords = getStorageItem("vault_focus_keywords") ?: "strategy, confidential, roadmap, architectural"
        )
    }

    actual suspend fun ensureVaultDirectory(folderPath: String): String {
        return folderPath.ifBlank { "Documents/Reveila" }
    }

    actual suspend fun listVaultDirectories(parentPath: String): List<String> {
        return listOf("Reveila", "Notes", "Research")
    }

    actual suspend fun createVaultDirectory(parentPath: String, newFolderName: String): String {
        val cleanName = newFolderName.trim().ifBlank { "Reveila" }
        val basePrefix = parentPath.trim().trimEnd('/').ifBlank { "Documents" }
        return "$basePrefix/$cleanName"
    }

    actual suspend fun pickSystemFolder(): String? = null

    actual suspend fun scanVault(): VaultScanResult {
        docCount = 1
        chunkCount = 3
        return VaultScanResult(
            folder = getVaultFolder().folder,
            filesScanned = docCount,
            entitiesFound = 4,
            chunkCount = chunkCount,
            status = "SUCCESS"
        )
    }

    actual suspend fun getVaultStats(): VaultStats {
        return VaultStats(
            documentCount = docCount,
            chunkCount = chunkCount,
            folder = getVaultFolder().folder
        )
    }

    actual fun getStorageItem(key: String): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey("aegin_$key")
    }

    actual fun setStorageItem(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = "aegin_$key")
    }

    actual fun clearStorage() {
        val keys = listOf(
            "reveila_setup_completed",
            "selected_llm_mode",
            "selected_llm_provider",
            "vault_folder_path",
            "vault_focus_keywords",
            "use_biometrics"
        )
        keys.forEach { NSUserDefaults.standardUserDefaults.removeObjectForKey("aegin_$it") }
    }

    actual suspend fun verifyModelPreflight(modelSizeBytes: Long): PreflightCheckResult {
        val cap = getDeviceCapabilities()
        return PreflightCheckResult(
            allowed = cap.canRunLocalGguf,
            tierLabel = cap.hardwareTier,
            recommendedProfile = cap.recommendedModel,
            reason = cap.statusSummary
        )
    }

    actual suspend fun checkLocalModelAvailability(modelName: String): LocalModelCheckResult {
        val target = modelName.ifBlank { ModelTierCatalog.MODEL_GEMMA_2B }
        return LocalModelCheckResult(
            modelName = target,
            existsLocally = false,
            downloadUrl = ""
        )
    }

    actual suspend fun downloadLocalModel(modelName: String): Boolean = false

    actual suspend fun pauseLocalModelDownload(modelName: String) {
    }

    actual suspend fun cancelLocalModelDownload(modelName: String) {
    }

    actual fun observeLocalModelDownloadState(): StateFlow<LocalModelDownloadState> =
        modelDownloadState.asStateFlow()

    actual fun streamAgent(
        prompt: String,
        sessionId: String?,
        prevSummary: String?,
        assistantMode: String?,
        spaceId: String?,
        spaceName: String?
    ): Flow<String> = flow {
        if (!spaceId.isNullOrBlank()) {
            emit(invoke("AgenticFabric", "askAgentInSpace", listOf(prompt, spaceId, spaceName ?: "", sessionId ?: "", prevSummary ?: "", assistantMode ?: "")))
        } else {
            emit(invoke("AgenticFabric", "askAgent", listOf(prompt, sessionId ?: "", prevSummary ?: "")))
        }
    }

    actual suspend fun releaseLocalModel() {
    }
}
