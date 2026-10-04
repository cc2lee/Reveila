package com.reveila.ai

import com.reveila.ai.util.GemmaPromptFormatter
import com.reveila.ai.util.Llama3PromptFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

open class LocalLlamaProvider : BaseLlmProvider() {

    init {
        this.endpoint = "http://localhost:8888/completion"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val formattedPrompt: String
        val activeModel = model?.lowercase() ?: ""

        val isLlama = activeModel.contains("llama-3") || activeModel.contains("llama3")
        val stopToken = if (isLlama) "<|eot_id|>" else "<end_of_turn>"
        formattedPrompt = if (isLlama) {
            Llama3PromptFormatter.format(request.messages)
        } else {
            GemmaPromptFormatter.format(request.messages)
        }

        val json = buildJsonObject {
            put("prompt", formattedPrompt)
            put("temperature", temperature)
            put("n_predict", 1024)
            put("stream", false)
            put("stop", buildJsonArray { add(JsonPrimitive(stopToken)) })
        }

        return json.toString()
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        return headers
    }

    @Throws(LlmException::class)
    override fun parseResponse(json: String): LlmResponse {
        val llmResponse = LlmResponse()
        try {
            val root = Json.parseToJsonElement(json).jsonObject
            val content = root["content"]?.jsonPrimitive?.content?.trim() ?: ""
            llmResponse.content = content
        } catch (e: Exception) {
            llmResponse.content = ""
        }
        return llmResponse
    }

    override fun isConfigured(): Boolean {
        return endpoint != null
    }
}
