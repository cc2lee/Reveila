package com.reveila.aegin.bridge

import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSUserDefaults

actual object PlatformEngineBridge {
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
            "UiController" -> when (method) {
                "getSettings" -> llmSettingsJson
                "setSettings", "saveSettings" -> {
                    if (params.size > 1) llmSettingsJson = params[1]
                    "true"
                }
                else -> "{}"
            }
            "AgenticFabric" -> {
                val prompt = params.firstOrNull() ?: ""
                val sessionId = params.getOrNull(1)?.ifBlank { null } ?: "ios-session-1"
                """{"answer":"Sovereign iOS Agent Response: Processed \"$prompt\" against local KnowledgeVault ($chunkCount vector chunks).","sessionId":"$sessionId"}"""
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
            folder = getStorageItem("vault_folder_path") ?: "ReveilaVault",
            focusKeywords = getStorageItem("vault_focus_keywords") ?: "strategy, confidential, roadmap, architectural"
        )
    }

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
}
