package com.reveila.ai

import com.reveila.system.Constants
import com.reveila.util.json.JsonUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

open class OpenAiLlmProvider : BaseLlmProvider() {

    var allowNoApiKey: Boolean = false

    init {
        this.endpoint = "https://api.openai.com/v1/chat/completions"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val activeModel = if (!model.isNullOrBlank()) model else request.modelId

        val json = buildJsonObject {
            put("model", activeModel)
            put("temperature", temperature)
            put("stream", false)
            put("messages", buildJsonArray {
                for (msg in request.messages) {
                    val isToolRole = msg.role == LlmRole.TOOL
                    add(buildJsonObject {
                        put("role", if (isToolRole) "user" else msg.role.name.lowercase())
                        put("content", if (isToolRole) "[Tool Execution Result]\n${msg.content}" else msg.content)
                    })
                }
            })

            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in request.tools) {
                        try {
                            add(Json.parseToJsonElement(tool.toOpenAiToolJsonString()))
                        } catch (e: Exception) {
                            throw LlmException("Failed to serialize tool: ${e.message}", e)
                        }
                    }
                })
            }
        }
        return json.toString()
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        try {
            val resolvedKey = resolveApiKey()
            if (!resolvedKey.isNullOrBlank()) {
                headers["Authorization"] = "Bearer $resolvedKey"
            } else if (!allowNoApiKey) {
                headers["Authorization"] = "Bearer "
            }
        } catch (e: Exception) {
            if (!allowNoApiKey) {
                throw LlmException("Failed to resolve API key for OpenAI provider: ${e.message}", e)
            }
        }
        headers["Content-Type"] = "application/json"
        return headers
    }

    @Throws(LlmException::class)
    override fun parseResponse(json: String): LlmResponse {
        val response = LlmResponse()
        try {
            val root = Json.parseToJsonElement(json).jsonObject
            val choices = root["choices"]?.jsonArray
            if (choices != null && choices.isNotEmpty()) {
                val firstChoice = choices[0].jsonObject
                response.finishReason = extractJsonString(firstChoice["finish_reason"])
                val message = firstChoice["message"]?.jsonObject
                val textContent = extractJsonString(message?.get("content"))
                    ?.takeIf { it.isNotBlank() }
                    ?: extractJsonString(message?.get("reasoning_content")).orEmpty()

                val parsedToolCalls = mutableListOf<ToolCall>()
                val synthesizedCallMaps = mutableListOf<Map<String, Any?>>()
                val rawToolCalls = message?.get("tool_calls")?.takeIf { it !is JsonNull }?.jsonArray
                if (rawToolCalls != null) {
                    for (element in rawToolCalls) {
                        val callObj = element as? JsonObject ?: continue
                        val functionObj = callObj["function"] as? JsonObject ?: callObj
                        val fnName = extractJsonString(functionObj["name"])?.trim().orEmpty()
                        if (fnName.isEmpty()) continue
                        val parsedArgs = parseToolArguments(functionObj["arguments"])
                        parsedToolCalls.add(ToolCall(fnName, parsedArgs))
                        synthesizedCallMaps.add(mapOf("name" to fnName, "arguments" to parsedArgs))
                    }
                }

                if (parsedToolCalls.isNotEmpty()) {
                    response.toolCalls = parsedToolCalls
                    response.content = mergeTextAndNativeToolCalls(textContent, synthesizedCallMaps)
                } else {
                    response.content = textContent
                }
            }
        } catch (e: Exception) {
            throw LlmException("Failed to parse OpenAI response: ${e.message}", e)
        }
        return response
    }

    private fun extractJsonString(element: kotlinx.serialization.json.JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content
    }

    private fun parseToolArguments(rawArgs: kotlinx.serialization.json.JsonElement?): Map<String, Any?> {
        if (rawArgs == null || rawArgs is JsonNull) return emptyMap()
        val rawJson = when (rawArgs) {
            is JsonObject -> rawArgs.toString()
            is JsonPrimitive -> rawArgs.content.trim()
            else -> return emptyMap()
        }
        if (rawJson.isBlank()) return emptyMap()
        return try {
            JsonUtil.parseJsonStringToMap(rawJson)
        } catch (_: Exception) {
            mapOf("raw" to rawJson)
        }
    }

    /**
     * Bridges native OpenAI/Gemini `tool_calls` into Reveila's agentic JSON envelope when the
     * provider returned native function calls instead of embedding `"tool-call"` in `content`.
     */
    private fun mergeTextAndNativeToolCalls(
        textContent: String,
        callMaps: List<Map<String, Any?>>
    ): String {
        val trimmed = textContent.trim()
        if (trimmed.startsWith("{")) {
            try {
                val existing = JsonUtil.parseJsonStringToMap(JsonUtil.clean(trimmed))
                if (existing.containsKey("tool-call") || existing.containsKey("tool_call")) {
                    return textContent
                }
            } catch (_: Exception) {
                // Fall through and wrap textContent as reasoning alongside native tool calls.
            }
        }
        return JsonUtil.toJsonString(
            mapOf(
                "status" to Constants.AI_STATUS_TOOL_CALL,
                "reasoning" to trimmed.ifBlank { "Executing model-requested tool call(s)." },
                "result" to "",
                "confidence-score" to 1.0,
                "tool-call" to callMaps
            )
        )
    }

    override fun isConfigured(): Boolean {
        if (endpoint.isNullOrBlank()) return false
        if (allowNoApiKey) return true
        val key = try {
            resolveApiKey()
        } catch (_: Exception) {
            null
        }
        return !key.isNullOrBlank()
    }
}
