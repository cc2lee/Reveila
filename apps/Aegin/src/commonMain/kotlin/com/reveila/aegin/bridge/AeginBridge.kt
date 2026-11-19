package com.reveila.aegin.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
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
    val intent: String = "auto",
    val prompt: String = "",
    val frequency: String = "daily",
    val scheduleTime: String = "08:00",
    val executionTarget: String = "auto",
    val enabled: Boolean = true,
    val status: String = "ACTIVE",
    val requiresApproval: Boolean = false,
    val approvalReason: String = "",
    val sandboxId: String = "",
    val currentStep: String = "",
    val fileName: String = "",
    val routeTarget: String = "LOCAL",
    val taskCategory: String = "LOCAL_TEXT_TRANSFORM",
    val routingReason: String = "",
    val lastRunAt: Long = 0L,
    val lastResult: String = ""
)

@Serializable
data class TaskNotificationItem(
    val eventId: String,
    val taskId: String,
    val title: String = "",
    val status: String = "",
    val summary: String = "",
    val timestamp: Long = 0L
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
    val notifications: List<TaskNotificationItem> = emptyList(),
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
    val apiKey: String = "",
    val intentOptions: List<String> = emptyList(),
    val tested: Boolean = false,
    val testedFingerprint: String = ""
)

@Serializable
data class ConnectionTestResult(
    val ok: Boolean,
    val message: String,
    val fingerprint: String = ""
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class SpaceThreadSummary(
    val spaceId: String,
    val spaceName: String,
    val latestSessionId: String = "",
    val summary: String = "",
    val messageCount: Int = 0,
    val lastMessagePreview: String = "",
    val updatedAt: Long = 0L
)

@Serializable
data class SpaceMessageEntry(
    val role: String,
    val content: String,
    val spaceId: String = "main-space",
    val timestamp: Long = 0L
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

@Serializable
data class LocalModelManifestEntry(
    val modelId: String = "",
    val filename: String = "",
    val role: String = "",
    val repoPath: String = "",
    val approxSizeBytes: Long = 0L,
    val sha256: String = "",
    val requiresAuthToken: Boolean = false
) {
    fun effectiveFilename(): String {
        val fromPath = repoPath.substringAfterLast('/').substringBefore('?')
        return filename.ifBlank { fromPath.ifBlank { if (modelId.endsWith(".gguf", ignoreCase = true)) modelId else "$modelId.gguf" } }
    }
}

@Serializable
data class LocalModelManifest(
    val downloadBaseUrl: String = "https://huggingface.co",
    val models: List<LocalModelManifestEntry> = emptyList()
)

@Serializable
data class LocalModelCheckResult(
    val modelName: String = "",
    val existsLocally: Boolean = false,
    val localPath: String = "",
    val partialBytes: Long = 0L,
    val downloadUrl: String = "",
    val approxSizeBytes: Long = 0L,
    val availableStorageBytes: Long = -1L,
    val sha256: String = "",
    val isWifiConnected: Boolean = true,
    val isMeteredNetwork: Boolean = false,
    val requiresAuthToken: Boolean = false
)

@Serializable
data class LocalModelDownloadState(
    val status: String = "IDLE",
    val modelName: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int = 0,
    val sourceUrl: String = "",
    val message: String = ""
)

@Serializable
data class BrowserElementItem(
    val index: Int,
    val tagName: String,
    val type: String = "",
    val name: String = "",
    val elementId: String = "",
    val text: String = "",
    val placeholder: String = "",
    val value: String = "",
    val href: String = "",
    val isClickable: Boolean = false,
    val isInputField: Boolean = false,
    val isSecret: Boolean = false,
    val formId: String = ""
)

@Serializable
data class BrowserLiveState(
    val isVisible: Boolean = false,
    val url: String = "",
    val title: String = "",
    val statusCode: Int = 0,
    val lastAction: String = "",
    val contentPreview: String = "",
    val rawHtml: String = "",
    val elements: List<BrowserElementItem> = emptyList(),
    val controlOwner: String = "AGENT",
    val waitingForUser: Boolean = false,
    val takeoverReason: String = "",
    val takeoverMessage: String = "",
    val updatedAtMs: Long = 0L
)

@Serializable
data class TerminalLiveState(
    val isVisible: Boolean = false,
    val executionId: String = "default",
    val shellName: String = "",
    val workingDir: String = "",
    val currentScript: String = "",
    val outputText: String = "",
    val exitCode: Int? = null,
    val status: String = "IDLE",
    val controlOwner: String = "AGENT",
    val waitingForUser: Boolean = false,
    val updatedAtMs: Long = 0L
)

@Serializable
data class DownloadTaskSummary(
    val downloadId: String,
    val fileName: String,
    val url: String = "",
    val destinationPath: String = "",
    val status: String = "IDLE",
    val percentage: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String = "",
    val updatedAtMs: Long = 0L
)

@Serializable
data class DownloadLiveState(
    val isVisible: Boolean = false,
    val controlOwner: String = "AGENT",
    val availableStorageBytes: Long = -1L,
    val items: List<DownloadTaskSummary> = emptyList(),
    val updatedAtMs: Long = 0L
)

@Serializable
data class PendingToolAuthState(
    val requestId: String,
    val traceId: String = "",
    val sessionId: String = "",
    val spaceId: String = "main-space",
    val toolName: String,
    val action: String = "",
    val scope: String = "*",
    val title: String = "",
    val reason: String = "",
    val commandPreview: String = "",
    val riskLevel: String = "MEDIUM",
    val requiresCredentials: Boolean = false,
    val credentialType: String = "NONE",
    val credentialDomain: String = "",
    val credentialKey: String = "",
    val createdAtMs: Long = 0L
)

@Serializable
data class ToolHubSnapshot(
    val browser: BrowserLiveState = BrowserLiveState(),
    val terminal: TerminalLiveState = TerminalLiveState(),
    val downloads: DownloadLiveState = DownloadLiveState(),
    val pendingAuthorization: PendingToolAuthState? = null,
    val waitingForUserInput: Boolean = false
)

data class ToolAuthResolutionResult(
    val answer: String,
    val status: String,
    val waitingForUser: Boolean = false,
    val sessionId: String? = null
)

/**
 * Multiplatform expect declaration for connecting the Compose UI to the underlying
 * Reveila Sovereign Engine (JVM Desktop, Android Service, iOS Native, or Web Wasm).
 */
expect object PlatformEngineBridge {
    suspend fun startService(systemHome: String? = null): Boolean
    suspend fun getEngineStatus(): EngineStatus
    suspend fun getDeviceCapabilities(): DeviceCapabilityReport
    suspend fun verifyModelPreflight(modelSizeBytes: Long): PreflightCheckResult
    suspend fun checkLocalModelAvailability(modelName: String): LocalModelCheckResult
    suspend fun downloadLocalModel(modelName: String): Boolean
    suspend fun pauseLocalModelDownload(modelName: String = "")
    suspend fun cancelLocalModelDownload(modelName: String = "")
    fun observeLocalModelDownloadState(): StateFlow<LocalModelDownloadState>
    suspend fun invoke(component: String, method: String, params: List<String> = emptyList()): String
    fun streamAgent(
        prompt: String,
        sessionId: String? = null,
        prevSummary: String? = null,
        assistantMode: String? = null,
        spaceId: String? = null,
        spaceName: String? = null
    ): Flow<String>
    suspend fun releaseLocalModel()
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
    val PREDEFINED_TASK_INTENTS: List<String> = listOf(
        "LOCAL_TEXT_TRANSFORM",
        "LOCAL_STRUCTURED_EXTRACT",
        "LOCAL_DEVICE_ACTION",
        "CLOUD_LIVE_RETRIEVAL",
        "CLOUD_DEEP_REASONING",
        "CLOUD_MULTI_STEP_AGENT"
    )

    val DEFAULT_LOCAL_INTENTS: List<String> = listOf(
        "LOCAL_TEXT_TRANSFORM",
        "LOCAL_STRUCTURED_EXTRACT",
        "LOCAL_DEVICE_ACTION"
    )

    val DEFAULT_CLOUD_INTENTS: List<String> = listOf(
        "CLOUD_LIVE_RETRIEVAL",
        "CLOUD_DEEP_REASONING",
        "CLOUD_MULTI_STEP_AGENT"
    )

    fun formatIntentLabel(intentKey: String): String =
        AeginDisplayMapper.taskCategory(intentKey)

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

    suspend fun verifyModelPreflight(modelSizeBytes: Long): PreflightCheckResult {
        return PlatformEngineBridge.verifyModelPreflight(modelSizeBytes)
    }

    suspend fun checkLocalModelAvailability(modelName: String): LocalModelCheckResult {
        return PlatformEngineBridge.checkLocalModelAvailability(modelName)
    }

    suspend fun downloadLocalModel(modelName: String): Boolean {
        return PlatformEngineBridge.downloadLocalModel(modelName)
    }

    suspend fun pauseLocalModelDownload(modelName: String = "") {
        PlatformEngineBridge.pauseLocalModelDownload(modelName)
    }

    suspend fun cancelLocalModelDownload(modelName: String = "") {
        PlatformEngineBridge.cancelLocalModelDownload(modelName)
    }

    fun observeLocalModelDownloadState(): StateFlow<LocalModelDownloadState> {
        return PlatformEngineBridge.observeLocalModelDownloadState()
    }

    fun parseModelManifest(rawJson: String): LocalModelManifest {
        return try {
            json.decodeFromString<LocalModelManifest>(rawJson)
        } catch (_: Exception) {
            LocalModelManifest()
        }
    }

    fun resolveManifestEntry(manifest: LocalModelManifest, modelNameOrId: String): LocalModelManifestEntry? {
        val target = modelNameOrId.trim()
        if (target.isEmpty()) return null
        val targetNoExt = target.removeSuffix(".gguf").removeSuffix(".GGUF")
        return manifest.models.firstOrNull { entry ->
            val fname = entry.effectiveFilename()
            fname.equals(target, ignoreCase = true) ||
                entry.modelId.equals(target, ignoreCase = true) ||
                entry.modelId.equals(targetNoExt, ignoreCase = true) ||
                fname.removeSuffix(".gguf").equals(targetNoExt, ignoreCase = true)
        }
    }

    fun buildManifestDownloadUrl(manifest: LocalModelManifest, entry: LocalModelManifestEntry): String {
        val repoPath = entry.repoPath.trim()
        val rawUrl = if (repoPath.startsWith("http://", ignoreCase = true) || repoPath.startsWith("https://", ignoreCase = true)) {
            repoPath
        } else {
            val base = manifest.downloadBaseUrl.trim().trimEnd('/')
            val path = repoPath.trimStart('/')
            if (base.isNotEmpty() && path.isNotEmpty()) "$base/$path" else ""
        }
        if (rawUrl.isEmpty()) return ""
        return if (rawUrl.contains("huggingface.co", ignoreCase = true) && !rawUrl.contains("download=")) {
            if (rawUrl.contains('?')) "$rawUrl&download=true" else "$rawUrl?download=true"
        } else {
            rawUrl
        }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) {
            val tenths = ((gb * 10.0) + 0.5).toLong()
            return "${tenths / 10}.${tenths % 10} GB"
        }
        val mb = (bytes + (512L * 1024L)) / (1024L * 1024L)
        return "$mb MB"
    }

    suspend fun releaseLocalModel() {
        PlatformEngineBridge.releaseLocalModel()
    }

    suspend fun invoke(component: String, method: String, params: List<String> = emptyList()): String {
        return PlatformEngineBridge.invoke(component, method, params)
    }

    fun normalizeSpaceId(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "main-space"
        val unprefixed = when {
            trimmed.startsWith("space:", ignoreCase = true) -> trimmed.substring(6).trim()
            trimmed.startsWith("space-", ignoreCase = true) && trimmed.length > 6 -> trimmed.substring(6).trim()
            else -> trimmed
        }
        val slug = buildString {
            var prevDash = false
            for (ch in unprefixed.lowercase()) {
                if (ch in 'a'..'z' || ch in '0'..'9') {
                    append(ch)
                    prevDash = false
                } else if (!prevDash && isNotEmpty()) {
                    append('-')
                    prevDash = true
                }
            }
        }.trim('-')
        return slug.ifEmpty { "main-space" }
    }

    suspend fun getSpaceHistory(spaceId: String, limit: Int = 100): List<SpaceMessageEntry> {
        return try {
            val raw = invoke(
                "OrchestrationService",
                "getSpaceHistoryJson",
                listOf(spaceId, limit.toString())
            )
            if (raw.isBlank() || raw == "null") return emptyList()
            val arr = json.parseToJsonElement(raw).jsonArray
            val parsed = arr.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val msgSpace = obj["spaceId"]?.jsonPrimitive?.contentOrNull ?: normalizeSpaceId(spaceId)
                    val ts = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
                    SpaceMessageEntry(role = role, content = content, spaceId = msgSpace, timestamp = ts)
                } catch (_: Exception) {
                    null
                }
            }
            trimSpaceHistoryToLastSummary(parsed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    const val CONVERSATION_SUMMARY_PREFIX: String = "Earlier conversation summary:"

    fun isConversationSummaryEntry(entry: SpaceMessageEntry): Boolean =
        entry.role.equals("SYSTEM", ignoreCase = true) &&
            entry.content.trim().startsWith(CONVERSATION_SUMMARY_PREFIX, ignoreCase = true)

    fun extractConversationSummaryText(rawContent: String): String {
        val trimmed = rawContent.trim()
        return if (trimmed.startsWith(CONVERSATION_SUMMARY_PREFIX, ignoreCase = true)) {
            trimmed.substring(CONVERSATION_SUMMARY_PREFIX.length).trim()
        } else {
            trimmed
        }
    }

    /**
     * Limits space history for UI display so it starts at the most recent persisted
     * conversation summary (if any) and includes all turns after it, excluding any older
     * pre-summary messages and unrelated system messages.
     */
    fun trimSpaceHistoryToLastSummary(history: List<SpaceMessageEntry>): List<SpaceMessageEntry> {
        if (history.isEmpty()) return emptyList()
        val lastSummaryIdx = history.indexOfLast { isConversationSummaryEntry(it) }
        val scoped = if (lastSummaryIdx >= 0) history.drop(lastSummaryIdx) else history
        return scoped.filter { entry ->
            isConversationSummaryEntry(entry) ||
                entry.role.equals("USER", ignoreCase = true) ||
                entry.role.equals("ASSISTANT", ignoreCase = true)
        }
    }

    suspend fun listSpaces(): List<SpaceThreadSummary> {
        return try {
            val raw = invoke("OrchestrationService", "listSpacesJson", emptyList())
            if (raw.isBlank() || raw == "null") return emptyList()
            val arr = json.parseToJsonElement(raw).jsonArray
            arr.mapNotNull { elem ->
                try {
                    val obj = elem.jsonObject
                    val sid = obj["spaceId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SpaceThreadSummary(
                        spaceId = sid,
                        spaceName = obj["spaceName"]?.jsonPrimitive?.contentOrNull ?: sid,
                        latestSessionId = obj["latestSessionId"]?.jsonPrimitive?.contentOrNull ?: "",
                        summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "",
                        messageCount = obj["messageCount"]?.jsonPrimitive?.intOrNull ?: 0,
                        lastMessagePreview = obj["lastMessagePreview"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun selectRecentSpacesForStrip(
        persistedSpaces: List<SpaceThreadSummary>,
        activeSpaceId: String,
        activeSpaceName: String,
        maxCount: Int = 3
    ): List<SpaceThreadSummary> {
        if (maxCount <= 0) return emptyList()
        val normalizedActiveId = normalizeSpaceId(activeSpaceId)
        val sortedUnique = linkedMapOf<String, SpaceThreadSummary>()

        for (space in persistedSpaces.sortedByDescending { it.updatedAt }) {
            val key = normalizeSpaceId(space.spaceId)
            if (!sortedUnique.containsKey(key)) {
                sortedUnique[key] = space.copy(spaceId = key)
            }
        }

        if (!sortedUnique.containsKey("main-space")) {
            sortedUnique["main-space"] = SpaceThreadSummary(
                spaceId = "main-space",
                spaceName = "Main Space"
            )
        }

        val result = sortedUnique.values.take(maxCount).toMutableList()
        if (result.none { it.spaceId == normalizedActiveId }) {
            val activeEntry = sortedUnique[normalizedActiveId] ?: SpaceThreadSummary(
                spaceId = normalizedActiveId,
                spaceName = activeSpaceName.ifBlank { "Main Space" }
            )
            if (result.size >= maxCount) {
                result[result.lastIndex] = activeEntry
            } else {
                result.add(activeEntry)
            }
        }
        return result
    }

    suspend fun getSpaceSummary(spaceId: String): String {
        return try {
            val raw = invoke("AgentSessionManager", "getSpaceSummary", listOf(spaceId))
            if (raw == "null") "" else raw
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun clearSpace(spaceId: String) {
        try {
            invoke("OrchestrationService", "clearSpace", listOf(spaceId))
        } catch (_: Exception) {
        }
    }

    fun streamPrompt(
        prompt: String,
        sessionId: String?,
        prevSummary: String?,
        assistantMode: String? = null,
        spaceId: String? = null,
        spaceName: String? = null,
        onSessionId: (String) -> Unit = {}
    ): Flow<String> = flow {
        var emitted = false
        PlatformEngineBridge.streamAgent(prompt, sessionId, prevSummary, assistantMode, spaceId, spaceName).collect { chunk ->
            val trimmed = chunk.trimStart()
            if (trimmed.startsWith("{") && trimmed.contains("\"__session_meta__\"")) {
                val (_, newSessionId) = parseAgentResponse(chunk, sessionId)
                if (!newSessionId.isNullOrBlank()) {
                    onSessionId(newSessionId)
                }
            } else if (trimmed.startsWith("{") && (trimmed.contains("\"answer\"") || trimmed.contains("\"result\""))) {
                emitted = true
                val (answer, newSessionId) = parseAgentResponse(chunk, sessionId)
                if (!newSessionId.isNullOrBlank()) {
                    onSessionId(newSessionId)
                }
                emit(answer)
            } else {
                emitted = true
                emit(chunk)
            }
        }
        if (!emitted) {
            val (answer, newSessionId) = if (!spaceId.isNullOrBlank()) {
                askAgentInSpace(prompt, spaceId, spaceName, sessionId, prevSummary, assistantMode)
            } else {
                askAgent(prompt, sessionId, prevSummary, assistantMode)
            }
            if (!newSessionId.isNullOrBlank()) {
                onSessionId(newSessionId)
            }
            emit(answer)
        }
    }

    suspend fun askAgentInSpace(
        prompt: String,
        spaceId: String,
        spaceName: String? = null,
        sessionId: String? = null,
        prevSummary: String? = null,
        assistantMode: String? = null
    ): Pair<String, String?> {
        val raw = invoke(
            "AgenticFabric",
            "askAgentInSpace",
            listOf(
                prompt,
                spaceId,
                spaceName ?: "",
                sessionId ?: "",
                prevSummary ?: "",
                assistantMode ?: ""
            )
        )
        return parseAgentResponse(raw, sessionId)
    }

    suspend fun askAgent(
        prompt: String,
        sessionId: String?,
        prevSummary: String?,
        assistantMode: String? = null
    ): Pair<String, String?> {
        val raw = if (!assistantMode.isNullOrBlank()) {
            invoke(
                "AgenticFabric",
                "askAgentWithMode",
                listOf(prompt, sessionId ?: "", prevSummary ?: "", assistantMode)
            )
        } else {
            invoke(
                "AgenticFabric",
                "askAgent",
                listOf(prompt, sessionId ?: "", prevSummary ?: "")
            )
        }
        return parseAgentResponse(raw, sessionId)
    }

    fun parseAgentReplyText(raw: String): Pair<String, String?> = parseAgentResponse(raw, null)

    private fun parseAgentResponse(raw: String, fallbackSessionId: String?): Pair<String, String?> {
        return try {
            val element = json.parseToJsonElement(raw)
            val obj = parsePossiblyNestedObject(element)
            val answer = obj["answer"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                ?: obj["result"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                ?: obj["reasoning"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                ?: raw
            val newSessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull ?: fallbackSessionId
            Pair(answer, newSessionId)
        } catch (_: Exception) {
            Pair(raw, fallbackSessionId)
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

    fun parseLlmSettingsJson(
        rawJson: String,
        capabilities: DeviceCapabilityReport? = null
    ): Triple<List<LlmProviderConfig>, String, String> {
        val defaultLocalModel = capabilities?.recommendedModel
            ?.takeIf { capabilities.canRunLocalGguf && it.endsWith(".gguf", ignoreCase = true) }
            ?: ModelTierCatalog.MODEL_3B
        val defaultStandard = listOf(
            LlmProviderConfig(
                name = "On-Device Model",
                type = "Local",
                provider = "",
                endpoint = "local://on-device",
                model = defaultLocalModel,
                modelOptions = ModelTierCatalog.standardProfiles,
                temperature = 0.7,
                apiKey = "",
                intentOptions = DEFAULT_LOCAL_INTENTS
            ),
            LlmProviderConfig(
                name = "Google Gemini",
                type = "Cloud",
                provider = "Gemini",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                model = "gemini-1.5-flash",
                modelOptions = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-3-flash"),
                temperature = 0.1,
                apiKey = "",
                intentOptions = DEFAULT_CLOUD_INTENTS
            ),
            LlmProviderConfig(
                name = "OpenAI",
                type = "Cloud",
                provider = "OpenAI",
                endpoint = "https://api.openai.com/v1/chat/completions",
                model = "gpt-4o-mini",
                modelOptions = listOf("gpt-4o-mini", "gpt-4o"),
                temperature = 0.7,
                apiKey = "",
                intentOptions = DEFAULT_CLOUD_INTENTS
            ),
            LlmProviderConfig(
                name = "DeepSeek",
                type = "Cloud",
                provider = "DeepSeek",
                endpoint = "https://api.deepseek.com/chat/completions",
                model = "deepseek-chat",
                modelOptions = listOf("deepseek-chat", "deepseek-reasoner"),
                temperature = 0.7,
                apiKey = "",
                intentOptions = DEFAULT_CLOUD_INTENTS
            ),
            LlmProviderConfig(
                name = "Qwen",
                type = "Cloud",
                provider = "Qwen",
                endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                model = "qwen-plus",
                modelOptions = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
                temperature = 0.7,
                apiKey = "",
                intentOptions = DEFAULT_CLOUD_INTENTS
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
                    val isSelfHosted = rawType.equals("self-hosted", ignoreCase = true) ||
                        rawType.equals("selfhosted", ignoreCase = true)
                    val isLocal = !isSelfHosted && (
                        rawType.equals("local", ignoreCase = true) ||
                            rawType.contains("llama", ignoreCase = true) ||
                            rawType.contains("ondevice", ignoreCase = true) ||
                            name == "On-Device Model"
                        )
                    val type = when {
                        isLocal -> "Local"
                        isSelfHosted -> "Self-Hosted"
                        else -> "Cloud"
                    }
                    if (providerField.isBlank() && !isLocal) {
                        providerField = when {
                            isSelfHosted -> "OpenAI"
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
                    val intentArr = (p["Intent.Options"] ?: p["intent.options"] ?: p["intent_options"])?.jsonArray
                    val parsedIntents = intentArr
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()
                    val effectiveIntents = if (parsedIntents.isNotEmpty()) {
                        parsedIntents
                    } else if (isLocal || isSelfHosted) {
                        DEFAULT_LOCAL_INTENTS
                    } else {
                        DEFAULT_CLOUD_INTENTS
                    }
                    val tested = (p["Tested"] ?: p["tested"])?.jsonPrimitive?.booleanOrNull ?: false
                    val testedFp = (p["Tested.Fingerprint"] ?: p["tested.fingerprint"])?.jsonPrimitive?.contentOrNull ?: ""
                    parsedList.add(
                        LlmProviderConfig(
                            name = name,
                            type = type,
                            provider = if (isLocal) "" else providerField,
                            endpoint = endpoint,
                            model = model,
                            modelOptions = options,
                            temperature = temp,
                            apiKey = if (isLocal) "" else apiKey,
                            intentOptions = effectiveIntents,
                            tested = tested,
                            testedFingerprint = testedFp
                        )
                    )
                }
            }

            if (onboardedArr == null) {
                for (def in defaultStandard) {
                    if (parsedList.none { it.name.equals(def.name, ignoreCase = true) }) {
                        parsedList.add(def)
                    }
                }
            } else if (parsedList.none { it.name.equals("On-Device Model", ignoreCase = true) }) {
                parsedList.add(0, defaultStandard.first())
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

    fun extractAssistantLlm(rawJson: String): String {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            root["ai.assistant.llm"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun providerToConfigJson(
        p: LlmProviderConfig,
        localModelReadyHint: Boolean? = null
    ): String {
        val isLocal = p.type.equals("Local", ignoreCase = true) || p.name == "On-Device Model"
        val isSelfHosted = p.type.equals("Self-Hosted", ignoreCase = true)
        val typeVal = when {
            isLocal -> "Local"
            isSelfHosted -> "Self-Hosted"
            else -> "Cloud"
        }
        val opts = p.modelOptions.joinToString(",") { "\"${escapeJson(it)}\"" }
        val intentList = p.intentOptions.ifEmpty {
            if (isLocal || isSelfHosted) DEFAULT_LOCAL_INTENTS else DEFAULT_CLOUD_INTENTS
        }
        val intentOpts = intentList.joinToString(",") { "\"${escapeJson(it)}\"" }
        val providerLine = if (!isLocal && p.provider.isNotBlank()) {
            "\n              \"Provider\": \"${escapeJson(p.provider)}\","
        } else ""
        val apiKeyLine = if (!isLocal) {
            ",\n              \"api.key\": \"${escapeJson(p.apiKey)}\""
        } else ""
        val localReadyLine = if (localModelReadyHint != null) {
            ",\n              \"localModelReady\": $localModelReadyHint"
        } else ""

        return """
            {
              "Name": "${escapeJson(p.name)}",
              "Type": "$typeVal",$providerLine
              "Endpoint": "${escapeJson(p.endpoint)}",
              "Model": "${escapeJson(p.model)}",
              "Model.Options": [$opts],
              "Temperature": ${p.temperature}$apiKeyLine,
              "Tested": ${p.tested},
              "Tested.Fingerprint": "${escapeJson(p.testedFingerprint)}",
              "Intent.Options": [$intentOpts]$localReadyLine
            }
        """.trimIndent()
    }

    suspend fun testProviderConnection(config: LlmProviderConfig): ConnectionTestResult {
        val isLocal = config.type.equals("Local", ignoreCase = true) || config.name == "On-Device Model"
        val localCheck = if (isLocal) checkLocalModelAvailability(config.model) else null
        val payloadJson = providerToConfigJson(
            p = config,
            localModelReadyHint = localCheck?.existsLocally
        )
        return try {
            val raw = invoke("LlmProviderFactory", "testProviderConfigJson", listOf(payloadJson))
            val obj = json.parseToJsonElement(raw).jsonObject
            ConnectionTestResult(
                ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: false,
                message = obj["message"]?.jsonPrimitive?.contentOrNull ?: raw,
                fingerprint = obj["fingerprint"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        } catch (e: Exception) {
            ConnectionTestResult(
                ok = false,
                message = e.message ?: "Unable to verify model service."
            )
        }
    }

    fun buildLlmSettingsJson(
        providers: List<LlmProviderConfig>,
        workerProvider: String,
        governanceProvider: String,
        assistantProvider: String? = null
    ): String {
        val providersJson = providers.filter { it.name != "Custom" }.joinToString(",\n") { p ->
            providerToConfigJson(p)
        }
        val govVal = if (governanceProvider == "Disable") "" else governanceProvider
        val assistantLine = if (!assistantProvider.isNullOrBlank()) {
            "\n          \"ai.assistant.llm\": \"${escapeJson(assistantProvider)}\","
        } else ""
        return """
        {
          "ai.worker.llm": "${escapeJson(workerProvider)}",
          "ai.governance.llm": "${escapeJson(govVal)}",$assistantLine
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
                        intent = obj["intent"]?.jsonPrimitive?.contentOrNull ?: "auto",
                        prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull ?: "",
                        frequency = obj["frequency"]?.jsonPrimitive?.contentOrNull ?: "daily",
                        scheduleTime = obj["scheduleTime"]?.jsonPrimitive?.contentOrNull ?: "08:00",
                        executionTarget = obj["executionTarget"]?.jsonPrimitive?.contentOrNull ?: "auto",
                        enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                        status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "ACTIVE",
                        requiresApproval = obj["requiresApproval"]?.jsonPrimitive?.booleanOrNull ?: false,
                        approvalReason = obj["approvalReason"]?.jsonPrimitive?.contentOrNull ?: "",
                        sandboxId = obj["sandboxId"]?.jsonPrimitive?.contentOrNull ?: "",
                        currentStep = obj["currentStep"]?.jsonPrimitive?.contentOrNull ?: "",
                        fileName = obj["fileName"]?.jsonPrimitive?.contentOrNull ?: "$taskId.json",
                        routeTarget = obj["routeTarget"]?.jsonPrimitive?.contentOrNull ?: "LOCAL",
                        taskCategory = obj["taskCategory"]?.jsonPrimitive?.contentOrNull ?: "LOCAL_TEXT_TRANSFORM",
                        routingReason = obj["routingReason"]?.jsonPrimitive?.contentOrNull ?: "",
                        lastRunAt = obj["lastRunAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        lastResult = obj["lastResult"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

            val notificationsArr = root["notifications"]?.jsonArray
            val notifications = notificationsArr?.mapNotNull { el ->
                try {
                    val obj = el.jsonObject
                    val eventId = obj["eventId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    TaskNotificationItem(
                        eventId = eventId,
                        taskId = obj["taskId"]?.jsonPrimitive?.contentOrNull ?: "",
                        title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "",
                        summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: "",
                        timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L
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
                notifications = notifications,
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
        requiresApproval: Boolean,
        intent: String = "auto",
        executionTarget: String = "auto"
    ): String {
        return invoke(
            "AutonomousAgent",
            "createTaskWithIntent",
            listOf(title, intent, prompt, frequency, scheduleTime, requiresApproval.toString(), executionTarget)
        )
    }

    suspend fun updateTask(
        taskId: String,
        title: String,
        prompt: String,
        frequency: String,
        scheduleTime: String,
        enabled: Boolean,
        intent: String = "",
        requiresApproval: Boolean = false,
        executionTarget: String = "auto"
    ): String {
        return invoke(
            "AutonomousAgent",
            "updateTaskWithIntent",
            listOf(
                taskId,
                title,
                intent,
                prompt,
                frequency,
                scheduleTime,
                enabled.toString(),
                requiresApproval.toString(),
                executionTarget
            )
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

    // =========================================================================
    // Live Interactive Tool Windows, Takeover & Authorization Bridge
    // =========================================================================

    fun parseToolHubSnapshot(rawJson: String): ToolHubSnapshot {
        if (rawJson.isBlank() || rawJson == "null") return ToolHubSnapshot()
        return try {
            json.decodeFromString(ToolHubSnapshot.serializer(), rawJson)
        } catch (_: Exception) {
            ToolHubSnapshot()
        }
    }

    suspend fun getToolActivitySnapshot(): ToolHubSnapshot {
        return try {
            val raw = invoke("AgenticFabric", "getToolActivitySnapshotJson", emptyList())
            parseToolHubSnapshot(raw)
        } catch (_: Exception) {
            ToolHubSnapshot()
        }
    }

    suspend fun resolvePendingToolAuthorization(
        requestId: String,
        decision: String,
        username: String = "",
        password: String = "",
        secretKey: String = "",
        secretValue: String = "",
        editedScript: String = ""
    ): ToolAuthResolutionResult {
        return try {
            val raw = invoke(
                "AgenticFabric",
                "resolvePendingToolAuthorizationJson",
                listOf(requestId, decision, username, password, secretKey, secretValue, editedScript)
            )
            val obj = json.parseToJsonElement(raw).jsonObject
            ToolAuthResolutionResult(
                answer = obj["answer"]?.jsonPrimitive?.contentOrNull ?: raw,
                status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "COMPLETED",
                waitingForUser = obj["waitingForUser"]?.jsonPrimitive?.booleanOrNull ?: false,
                sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull
            )
        } catch (e: Exception) {
            ToolAuthResolutionResult(
                answer = e.message ?: "Failed to process authorization.",
                status = "FAILED"
            )
        }
    }

    suspend fun browserUserAction(
        action: String,
        url: String = "",
        target: String = "",
        text: String = "",
        form: String = "",
        cookieHeader: String = "",
        rawHtml: String = ""
    ): ToolHubSnapshot {
        return try {
            val raw = invoke(
                "AgenticFabric",
                "browserUserActionJson",
                listOf(action, url, target, text, form, cookieHeader, rawHtml)
            )
            parseToolHubSnapshot(raw)
        } catch (_: Exception) {
            getToolActivitySnapshot()
        }
    }

    suspend fun terminalUserAction(
        action: String,
        script: String = "",
        workingDir: String = ""
    ): ToolHubSnapshot {
        return try {
            val raw = invoke(
                "AgenticFabric",
                "terminalUserActionJson",
                listOf(action, script, workingDir)
            )
            parseToolHubSnapshot(raw)
        } catch (_: Exception) {
            getToolActivitySnapshot()
        }
    }

    suspend fun downloadUserAction(
        action: String,
        downloadId: String = ""
    ): ToolHubSnapshot {
        return try {
            val raw = invoke(
                "AgenticFabric",
                "downloadUserActionJson",
                listOf(action, downloadId)
            )
            parseToolHubSnapshot(raw)
        } catch (_: Exception) {
            getToolActivitySnapshot()
        }
    }

    suspend fun secretVaultAction(
        action: String,
        key: String = "",
        value: String = "",
        category: String = "GENERAL",
        domain: String = "",
        username: String = ""
    ): String {
        return try {
            invoke(
                "AgenticFabric",
                "secretVaultUserActionJson",
                listOf(action, key, value, category, domain, username)
            )
        } catch (e: Exception) {
            """{"error":"${escapeJson(e.message ?: "Vault unavailable")}"}"""
        }
    }
}
