package com.reveila.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

open class OpenAiLlmProvider : BaseLlmProvider() {

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
                    add(buildJsonObject {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                    })
                }
            })

            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in request.tools) {
                        try {
                            add(Json.parseToJsonElement(tool.toJsonString()))
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
            headers["Authorization"] = "Bearer " + resolveApiKey()
        } catch (e: Exception) {
            throw LlmException("Failed to resolve API key for OpenAI provider: ${e.message}", e)
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
                val message = choices[0].jsonObject["message"]?.jsonObject
                response.content = message?.get("content")?.jsonPrimitive?.content ?: ""
            }
        } catch (e: Exception) {
            throw LlmException("Failed to parse OpenAI response: ${e.message}", e)
        }
        return response
    }

    override fun isConfigured(): Boolean {
        return endpoint != null && apiKey != null
    }
}
