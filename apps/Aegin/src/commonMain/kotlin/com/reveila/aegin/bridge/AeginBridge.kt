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
    val folder: String = "ReveilaVault"
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
    val folder: String = "ReveilaVault",
    val focusKeywords: String = "strategy, confidential, roadmap, architectural"
)

@Serializable
data class LlmProviderConfig(
    val name: String,
    val type: String = "",
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

/**
 * Multiplatform expect declaration for connecting the Compose UI to the underlying
 * Reveila Sovereign Engine (JVM Desktop, Android Service, iOS Native, or Web Wasm).
 */
expect object PlatformEngineBridge {
    suspend fun startService(systemHome: String? = null): Boolean
    suspend fun getEngineStatus(): EngineStatus
    suspend fun invoke(component: String, method: String, params: List<String> = emptyList()): String
    suspend fun authenticateBiometric(): Boolean
    fun saveSensitiveData(data: String)
    suspend fun triggerEmergencyStop(): Boolean
    suspend fun setVaultFolder(folderPath: String, focusKeywords: String = ""): VaultScanResult
    suspend fun getVaultFolder(): VaultFolderConfig
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

    suspend fun scanVault(): VaultScanResult {
        return PlatformEngineBridge.scanVault()
    }

    suspend fun getVaultStats(): VaultStats {
        return PlatformEngineBridge.getVaultStats()
    }

    fun getStorageItem(key: String): String? = PlatformEngineBridge.getStorageItem(key)

    fun setStorageItem(key: String, value: String) = PlatformEngineBridge.setStorageItem(key, value)

    fun clearStorage() = PlatformEngineBridge.clearStorage()

    fun parseLlmSettingsJson(rawJson: String): Triple<List<LlmProviderConfig>, String, String> {
        val defaultStandard = listOf(
            LlmProviderConfig(
                name = "On-Device Model",
                type = "LocalLlamaProvider",
                endpoint = "http://127.0.0.1:8080/completion",
                model = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                modelOptions = listOf("qwen2.5-0.5b-instruct-q4_k_m.gguf", "gemma-2-2b-it-Q4_K_M.gguf"),
                temperature = 0.7,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "Google Gemini",
                type = "GeminiProvider",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                model = "gemini-1.5-flash",
                modelOptions = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-3-flash"),
                temperature = 0.1,
                apiKey = ""
            ),
            LlmProviderConfig(
                name = "OpenAI",
                type = "OpenAiProvider",
                endpoint = "https://api.openai.com/v1/chat/completions",
                model = "gpt-4o-mini",
                modelOptions = listOf("gpt-4o-mini", "gpt-4o"),
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
                    val type = (p["Type"] ?: p["type"])?.jsonPrimitive?.contentOrNull ?: ""
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
                            endpoint = endpoint,
                            model = model,
                            modelOptions = options,
                            temperature = temp,
                            apiKey = apiKey
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
            val typeVal = p.type.ifBlank {
                when (p.name) {
                    "On-Device Model" -> "LocalLlamaProvider"
                    "Google Gemini" -> "GeminiProvider"
                    else -> "OpenAiProvider"
                }
            }
            val opts = p.modelOptions.joinToString(",") { "\"${escapeJson(it)}\"" }
            """
            {
              "Name": "${escapeJson(p.name)}",
              "Type": "${escapeJson(typeVal)}",
              "Endpoint": "${escapeJson(p.endpoint)}",
              "Model": "${escapeJson(p.model)}",
              "Model.Options": [$opts],
              "Temperature": ${p.temperature},
              "Api.Key": "${escapeJson(p.apiKey)}",
              "api.key": "${escapeJson(p.apiKey)}"
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
}
