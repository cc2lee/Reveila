package com.reveila.aegin.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class TaskItem(
    val taskId: String,
    val title: String = "",
    val intent: String = "autonomous.task",
    val prompt: String = "",
    val frequency: String = "daily",
    val scheduleTime: String = "08:00",
    val enabled: Boolean = true,
    val status: String = "ACTIVE",
    val requiresApproval: Boolean = false,
    val approvalReason: String = "",
    val lastRunAt: Long = 0L,
    val lastResult: String = ""
)

@Serializable
data class AuditLogItem(
    val action: String,
    val details: String = "",
    val timestamp: Long = 0L,
    val pluginName: String = "AutonomousAgent"
)

@Serializable
data class ActiveSessionItem(
    val id: String,
    val title: String = "Active Session",
    val messageCount: Int = 0
)

@Serializable
data class TasksDashboardState(
    val tasksDir: String = "",
    val killSwitchEngaged: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val activeSessions: List<ActiveSessionItem> = emptyList(),
    val auditLogs: List<AuditLogItem> = emptyList(),
    val error: String = ""
)

@Serializable
data class EngineStatus(
    val initialized: Boolean,
    val status: String,
    val platform: String = "UNKNOWN",
    val storageMountPath: String = "/data/user/0/com.reveila.aegin/files"
)

@Serializable
data class VaultStats(
    val documentCount: Int = 0,
    val chunkCount: Int = 0,
    val folder: String = "Documents/Reveila"
)

@Serializable
data class VaultScanResult(
    val folder: String,
    val filesScanned: Int,
    val entitiesFound: Int,
    val chunkCount: Int = 0,
    val status: String = "SUCCESS"
)

@Serializable
data class VaultFolderConfig(
    val folder: String = "Documents/Reveila",
    val focusKeywords: String = "strategy, confidential, roadmap, architectural"
)

@Serializable
data class LlmProviderConfig(
    val name: String,
    val type: String = "Cloud",
    val provider: String = "",
    val endpoint: String = "",
    val model: String = "",
    val modelOptions: List<String> = emptyList(),
    val temperature: Double = 0.7,
    val apiKey: String = ""
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeviceCapabilityReport(
    val totalRamGb: Double = 8.0,
    val availableRamGb: Double = 4.0,
    val isLowMemory: Boolean = false,
    val freeDiskGb: Double = 10.0,
    val hasEnoughStorage: Boolean = true,
    val cpuAbi: String = "arm64-v8a",
    val supportsSimdDotProd: Boolean = true,
    val supportsFp16: Boolean = true,
    val supportsVulkanCompute: Boolean = false,
    val hardwareTier: String = "STANDARD (8-12 GB)",
    val recommendedModel: String = "qwen2.5-3b-instruct-q4_k_m.gguf",
    val recommendedModelSizeMb: Int = 2200,
    val canRunLocalGguf: Boolean = true,
    val modelsStagingPath: String = "",
    val stagedModels: List<String> = emptyList(),
    val statusSummary: String = "Hardware verified for local GGUF inference."
)

/**
 * Multiplatform expect declaration for connecting the Compose UI to the underlying
 * Reveila Sovereign Engine (JVM Desktop, Android Service, iOS Native, or Web Wasm).
 */
expect object PlatformEngineBridge {
    suspend fun startService(systemHome: String? = null): Boolean
    suspend fun getEngineStatus(): EngineStatus
    suspend fun getDeviceCapabilities(): DeviceCapabilityReport
    suspend fun invoke(component: String, method: String, params: List<String> = emptyList()): String
    suspend fun authenticateBiometric(): Boolean
    fun saveSensitiveData(data: String)
    suspend fun triggerEmergencyStop(): Boolean
    suspend fun setVaultFolder(folderPath: String, focusKeywords: String = ""): VaultScanResult
    suspend fun getVaultFolder(): VaultFolderConfig
    suspend fun ensureVaultDirectory(folderPath: String): String
    suspend fun listVaultDirectories(parentPath: String): List<String>
    suspend fun createVaultDirectory(parentPath: String, newFolderName: String): String
    suspend fun pickSystemFolder(): String?
    suspend fun scanVault(): VaultScanResult
    suspend fun getVaultStats(): VaultStats
    fun getStorageItem(key: String): String?
    fun setStorageItem(key: String, value: String)
    fun clearStorage()
}

/**
 * AeginBridge: The Universal Multiplatform Runtime Interface (Kotlin equivalent of ReveilaBridge.js).
 */
object AeginBridge {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    suspend fun startService(systemHome: String? = null): Boolean {
        return PlatformEngineBridge.startService(systemHome)
    }

    suspend fun getEngineStatus(): EngineStatus {
        return PlatformEngineBridge.getEngineStatus()
    }

    suspend fun getDeviceCapabilities(): DeviceCapabilityReport {
        return PlatformEngineBridge.getDeviceCapabilities()
    }

    suspend fun invoke(component: String, method: String, params: List<String> = emptyList()): String {
        return PlatformEngineBridge.invoke(component, method, params)
    }

    suspend fun askAgent(prompt: String, sessionId: String?, prevSummary: String?): Pair<String, String?> {
        val raw = invoke(
            "AgenticFabric",
            "askAgent",
            listOf(prompt, sessionId ?: "", prevSummary ?: "")
        )
        return try {
            val element = json.parseToJsonElement(raw)
            val obj = parsePossiblyNestedObject(element)
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull
                ?: obj["result"]?.jsonPrimitive?.contentOrNull
                ?: raw
            val newSessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull ?: sessionId
            Pair(answer, newSessionId)
        } catch (_: Exception) {
            Pair(raw, sessionId)
        }
    }

    private fun parsePossiblyNestedObject(element: JsonElement): JsonObject {
        val obj = element.jsonObject
        val nvp = obj["nameValuePairs"]
        if (nvp is JsonObject) {
            return nvp
        }
        return obj
    }

    suspend fun authenticateBiometric(): Boolean = PlatformEngineBridge.authenticateBiometric()

    fun saveSensitiveData(data: String) = PlatformEngineBridge.saveSensitiveData(data)

    suspend fun triggerEmergencyStop(): Boolean = PlatformEngineBridge.triggerEmergencyStop()

    suspend fun setVaultFolder(folderPath: String, focusKeywords: String = ""): VaultScanResult {
        return PlatformEngineBridge.setVaultFolder(folderPath, focusKeywords)
    }

    suspend fun getVaultFolder(): VaultFolderConfig {
        return PlatformEngineBridge.getVaultFolder()
    }

    suspend fun ensureVaultDirectory(folderPath: String): String {
        return PlatformEngineBridge.ensureVaultDirectory(folderPath)
    }

    suspend fun listVaultDirectories(parentPath: String): List<String> {
        return PlatformEngineBridge.listVaultDirectories(parentPath)
    }

    suspend fun createVaultDirectory(parentPath: String, newFolderName: String): String {
        return PlatformEngineBridge.createVaultDirectory(parentPath, newFolderName)
    }

    suspend fun pickSystemFolder(): String? {
        return PlatformEngineBridge.pickSystemFolder()
    }

    suspend fun scanVault(): VaultScanResult {
        return PlatformEngineBridge.scanVault()
    }

    suspend fun getVaultStats(): VaultStats {
        return PlatformEngineBridge.getVaultStats()
    }

    fun getStorageItem(key: String): String? = PlatformEngineBridge.getStorageItem(key)

    fun setStorageItem(key: String, value: String) = PlatformEngineBridge.setStorageItem(key, value)

    fun clearStorage() = PlatformEngineBridge.clearStorage()

    suspend fun getConfiguredCloudProvider(): String? {
        try {
            val configured = invoke("LlmProviderFactory", "getConfiguredCloudProvider", emptyList())
            if (configured.isNotBlank() && configured != "null") {
                return configured.trim()
            }
        } catch (_: Exception) {
        }

        val geminiKey = getStorageItem("gemini_api_key")
        if (!geminiKey.isNullOrBlank()) return "Google Gemini"
        val openaiKey = getStorageItem("openai_api_key")
        if (!openaiKey.isNullOrBlank()) return "OpenAI"
        val deepseekKey = getStorageItem("deepseek_api_key")
        if (!deepseekKey.isNullOrBlank()) return "DeepSeek"
        val qwenKey = getStorageItem("qwen_api_key")
        if (!qwenKey.isNullOrBlank()) return "Qwen"

        try {
            val rawLlm = invoke("LlmProviderFactory", "getSettings", listOf("llm.json"))
            val (providers, _, _) = parseLlmSettingsJson(rawLlm)
            for (p in providers) {
                if (p.name == "On-Device Model" || p.type.equals("Local", ignoreCase = true) || p.name == "Disable" || p.name == "Custom") continue
                if (p.apiKey.isNotBlank() && !p.apiKey.startsWith("REF:")) {
                    return p.name
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    suspend fun isCloudConfigured(): Boolean {
        return getConfiguredCloudProvider() != null
    }

    fun parseLlmSettingsJson(rawJson: String): Triple<List<LlmProviderConfig>, String, String> {
        val defaultStandard = listOf(
            LlmProviderConfig(
                name = "On-Device Model",
                type = "Local",
                provider = "",
                endpoint = "http://127.0.0.1:8080/completion",
                model = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                modelOptions = listOf("qwen2.5-0.5b-instruct-q4_k_m.gguf", "gemma-2-2b-it-Q4_K_M.gguf"),
                temperature = 0.7,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "Google Gemini",
                type = "Cloud",
                provider = "Gemini",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                model = "gemini-1.5-flash",
                modelOptions = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-3-flash"),
                temperature = 0.1,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "OpenAI",
                type = "Cloud",
                provider = "OpenAI",
                endpoint = "https://api.openai.com/v1/chat/completions",
                model = "gpt-4o-mini",
                modelOptions = listOf("gpt-4o-mini", "gpt-4o"),
                temperature = 0.7,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "DeepSeek",
                type = "Cloud",
                provider = "DeepSeek",
                endpoint = "https://api.deepseek.com/chat/completions",
                model = "deepseek-chat",
                modelOptions = listOf("deepseek-chat", "deepseek-reasoner"),
                temperature = 0.7,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "Qwen",
                type = "Cloud",
                provider = "Qwen",
                endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                model = "qwen-plus",
                modelOptions = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
                temperature = 0.7,
                apiKey = ""
            )
        )

        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val onboardedArr = (root["onboarded.providers"] ?: root["onboarded_providers"])?.jsonArray
            val parsedList = mutableListOf<LlmProviderConfig>()

            if (onboardedArr != null) {
                for (item in onboardedArr) {
                    val p = item.jsonObject
                    val name = (p["Name"] ?: p["name"])?.jsonPrimitive?.contentOrNull ?: continue
                    val rawType = (p["Type"] ?: p["type"])?.jsonPrimitive?.contentOrNull ?: ""
                    var providerField = (p["Provider"] ?: p["provider"])?.jsonPrimitive?.contentOrNull ?: ""
                    val isLocal = rawType.equals("local", ignoreCase = true) ||
                        rawType.contains("llama", ignoreCase = true) ||
                        rawType.contains("ondevice", ignoreCase = true) ||
                        name == "On-Device Model"
                    val type = if (isLocal) "Local" else "Cloud"
                    if (providerField.isBlank() && !isLocal) {
                        providerField = when {
                            rawType.contains("gemini", ignoreCase = true) || name.contains("gemini", ignoreCase = true) -> "Gemini"
                            rawType.contains("deepseek", ignoreCase = true) || name.contains("deepseek", ignoreCase = true) -> "DeepSeek"
                            rawType.contains("qwen", ignoreCase = true) || name.contains("qwen", ignoreCase = true) -> "Qwen"
                            rawType.contains("openai", ignoreCase = true) || name.contains("openai", ignoreCase = true) -> "OpenAI"
                            else -> name
                        }
                    }
                    val endpoint = (p["Endpoint"] ?: p["endpoint"])?.jsonPrimitive?.contentOrNull ?: ""
                    val model = (p["Model"] ?: p["model"])?.jsonPrimitive?.contentOrNull ?: ""
                    val optionsArr = (p["Model.Options"] ?: p["model.options"] ?: p["model_options"])?.jsonArray
                    val options = optionsArr?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val temp = (p["Temperature"] ?: p["temperature"])?.jsonPrimitive?.doubleOrNull ?: 0.7
                    val apiKey = (p["Api.Key"] ?: p["api.key"] ?: p["apiKey"])?.jsonPrimitive?.contentOrNull ?: ""
                    parsedList.add(
                        LlmProviderConfig(
                            name = name,
                            type = type,
                            provider = if (isLocal) "" else providerField,
                            endpoint = endpoint,
                            model = model,
                            modelOptions = options,
                            temperature = temp,
                            apiKey = if (isLocal) "" else apiKey
                        )
                    )
                }
            }

            for (def in defaultStandard) {
                if (parsedList.none { it.name.equals(def.name, ignoreCase = true) }) {
                    parsedList.add(def)
                }
            }

            val legacyWorkerMap = mapOf(
                "OpenAiProvider" to "OpenAI",
                "AnthropicProvider" to "Anthropic",
                "GeminiProvider" to "Google Gemini",
                "OnDeviceProvider" to "On-Device Model"
            )
            val legacyGovMap = legacyWorkerMap + ("" to "Disable")

            var worker = root["ai.worker.llm"]?.jsonPrimitive?.contentOrNull ?: "On-Device Model"
            worker = legacyWorkerMap[worker] ?: worker.ifBlank { "On-Device Model" }

            var gov = root["ai.governance.llm"]?.jsonPrimitive?.contentOrNull ?: "Disable"
            gov = legacyGovMap[gov] ?: gov.ifBlank { "Disable" }

            Triple(parsedList, worker, gov)
        } catch (_: Exception) {
            Triple(defaultStandard, "On-Device Model", "On-Device Model")
        }
    }

    fun buildLlmSettingsJson(
        providers: List<LlmProviderConfig>,
        workerProvider: String,
        governanceProvider: String
    ): String {
        val providersJson = providers.filter { it.name != "Custom" }.joinToString(",\n") { p ->
            val isLocal = p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model"
            val typeVal = if (isLocal) "Local" else "Cloud"
            val opts = p.modelOptions.joinToString(",") { "\"${escapeJson(it)}\"" }
            val providerLine = if (!isLocal && p.provider.isNotBlank()) {
                "\n              \"Provider\": \"${escapeJson(p.provider)}\","
            } else ""
            val apiKeyLine = if (!isLocal) {
                ",\n              \"api.key\": \"${escapeJson(p.apiKey)}\""
            } else ""

            """
            {
              "Name": "${escapeJson(p.name)}",
              "Type": "$typeVal",$providerLine
              "Endpoint": "${escapeJson(p.endpoint)}",
              "Model": "${escapeJson(p.model)}",
              "Model.Options": [$opts],
              "Temperature": ${p.temperature}$apiKeyLine
            }
            """.trimIndent()
        }
        val govVal = if (governanceProvider == "Disable") "" else governanceProvider
        return """
        {
          "ai.worker.llm": "${escapeJson(workerProvider)}",
          "ai.governance.llm": "${escapeJson(govVal)}",
          "onboarded.providers": [
            $providersJson
          ]
        }
        """.trimIndent()
    }

    fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    suspend fun getTasksDashboard(): TasksDashboardState {
        return try {
            val raw = invoke("AutonomousAgent", "getDashboardStateJson", emptyList())
            val root = json.parseToJsonElement(raw).jsonObject
            val tasksDir = root["tasksDir"]?.jsonPrimitive?.contentOrNull ?: ""
            val killSwitchEngaged = root["killSwitchEngaged"]?.jsonPrimitive?.booleanOrNull ?: false

            val tasksArr = root["tasks"]?.jsonArray
            val tasks = tasksArr?.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val taskId = obj["taskId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    TaskItem(
                        taskId = taskId,
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: taskId,
                        intent = obj["intent"]?.jsonPrimitive?.contentOrNull ?: "autonomous.task",
                        prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                        frequency = obj["frequency"]?.jsonPrimitive?.contentOrNull ?: "daily",
                        scheduleTime = obj["scheduleTime"]?.jsonPrimitive?.contentOrNull ?: "08:00",
                        enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                        status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "ACTIVE",
                        requiresApproval = obj["requiresApproval"]?.jsonPrimitive?.booleanOrNull ?: false,
                        approvalReason = obj["approvalReason"]?.jsonPrimitive?.contentOrNull ?: "",
                        lastRunAt = obj["lastRunAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        lastResult = obj["lastResult"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

            val sessionsArr = root["activeSessions"]?.jsonArray
            val sessions = sessionsArr?.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ActiveSessionItem(
                        id = id,
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Session",
                        messageCount = obj["messageCount"]?.jsonPrimitive?.intOrNull ?: 0
                    )
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

            val logsArr = root["auditLogs"]?.jsonArray
            val logs = logsArr?.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    AuditLogItem(
                        action = obj["action"]?.jsonPrimitive?.contentOrNull ?: "EVENT",
                        details = obj["details"]?.jsonPrimitive?.contentOrNull ?: "",
                        timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
                        pluginName = obj["pluginName"]?.jsonPrimitive?.contentOrNull ?: "AutonomousAgent"
                    )
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

            TasksDashboardState(
                tasksDir = tasksDir,
                killSwitchEngaged = killSwitchEngaged,
                tasks = tasks,
                activeSessions = sessions,
                auditLogs = logs
            )
        } catch (e: Exception) {
            TasksDashboardState(error = "${e::class.simpleName}: ${e.message}")
        }
    }

    suspend fun createTask(
        title: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        requiresApproval: Boolean
    ): String {
        return invoke(
            "AutonomousAgent",
            "createTask",
            listOf(title, prompt, frequency, scheduleTime, requiresApproval.toString())
        )
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        enabled: Boolean
    ): String {
        return invoke(
            "AutonomousAgent",
            "updateTask",
            listOf(taskId, title, prompt, frequency, scheduleTime, enabled.toString())
        )
    }

    suspend fun runTaskNow(taskId: String): String {
        return invoke("AutonomousAgent", "runTaskNow", listOf(taskId))
    }

    suspend fun approveTask(taskId: String): String {
        return invoke("AutonomousAgent", "approveTask", listOf(taskId))
    }

    suspend fun rejectTask(taskId: String): String {
        return invoke("AutonomousAgent", "rejectTask", listOf(taskId))
    }

    suspend fun killTask(taskId: String): String {
        return invoke("AutonomousAgent", "killTask", listOf(taskId))
    }

    suspend fun pauseTask(taskId: String): String {
        return invoke("AutonomousAgent", "pauseTask", listOf(taskId))
    }

    suspend fun resumeTask(taskId: String): String {
        return invoke("AutonomousAgent", "resumeTask", listOf(taskId))
    }

    suspend fun deleteTask(taskId: String): String {
        return invoke("AutonomousAgent", "deleteTask", listOf(taskId))
    }

    suspend fun setGlobalKillSwitch(engage: Boolean): String {
        return invoke("AutonomousAgent", "setGlobalKillSwitch", listOf(engage.toString()))
    }
}
