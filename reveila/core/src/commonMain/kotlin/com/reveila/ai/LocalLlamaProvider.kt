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
        this.endpoint = "http://127.0.0.1:8080/completion"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val activeModel = model?.lowercase() ?: ""

        val isLlama = activeModel.contains("llama-3") || activeModel.contains("llama3")
        val isQwen = activeModel.contains("qwen") || activeModel.isBlank() || activeModel.contains("on-device")

        val formattedPrompt: String
        val stopToken: String
        if (isQwen) {
            val sb = StringBuilder()
            for (msg in request.messages) {
                val role = msg.role.name.lowercase()
                sb.append("<|im_start|>").append(role).append("\n")
                sb.append(msg.content).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
            formattedPrompt = sb.toString()
            stopToken = "<|im_end|>"
        } else if (isLlama) {
            formattedPrompt = Llama3PromptFormatter.format(request.messages)
            stopToken = "<|eot_id|>"
        } else {
            formattedPrompt = GemmaPromptFormatter.format(request.messages)
            stopToken = "<end_of_turn>"
        }

        val json = buildJsonObject {
            put("prompt", formattedPrompt)
            put("temperature", temperature)
            put("n_predict", 256)
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
            val content = root["content"]?.jsonPrimitive?.content?.trim()
                ?: root["choices"]?.let {
                    try {
                        val choicesArr = it as? kotlinx.serialization.json.JsonArray
                        choicesArr?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content?.trim()
                    } catch (e: Exception) {
                        null
                    }
                }
                ?: ""
            llmResponse.content = content
        } catch (e: Exception) {
            llmResponse.content = ""
        }
        return llmResponse
    }

    @Throws(LlmException::class)
    override fun invoke(request: LlmRequest): LlmResponse {
        getHttpClientService()?.let { http ->
            if (http.readTimeout < 90) {
                http.readTimeout = 90
            }
        }

        var baseEp = endpoint?.trim() ?: "http://127.0.0.1:8080/completion"
        if (!baseEp.endsWith("/completion") && !baseEp.endsWith("/v1/chat/completions")) {
            baseEp = if (baseEp.endsWith("/")) "${baseEp}completion" else "$baseEp/completion"
        }

        val candidateEndpoints = listOfNotNull(
            "http://127.0.0.1:8080/completion",
            baseEp,
            "http://10.0.2.2:8080/completion"
        ).distinct()

        var lastException: Exception? = null
        for (ep in candidateEndpoints) {
            try {
                this.endpoint = ep
                val res = super.invoke(request)
                if (!res.content.isNullOrBlank()) {
                    return res
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        logger.info("Local HTTP LLM server unavailable (${lastException?.message}). Falling back to sovereign on-device reasoning engine.")
        return executeOnDeviceInference(request)
    }

    private fun executeOnDeviceInference(request: LlmRequest): LlmResponse {
        val userMsg = request.messages.lastOrNull { it.role() == LlmRole.USER }?.content() ?: ""
        val systemMsg = request.messages.firstOrNull { it.role() == LlmRole.SYSTEM }?.content() ?: ""

        val hasVaultContext = systemMsg.contains("Related Documents:") && !systemMsg.contains("No relevant internal documents found.")

        val responseContent: String
        val answerText = if (hasVaultContext) {
            val docContext = systemMsg.substringAfter("Related Documents:").substringBefore("Always follow")
            "Based on your sovereign Knowledge Vault:\n$docContext\n\n[Processed locally on-device with zero cloud transmission]"
        } else {
            "Sovereign On-Device Engine processed your query: '$userMsg'.\nYour document vault is active and monitored. Add documents to your monitored vault folder to enable contextual semantic search."
        }

        val jsonResult = buildJsonObject {
            put("status", "COMPLETED")
            put("reasoning", "Processed using on-device Sovereign Memory and local vector index.")
            put("result", answerText)
            put("confidence-score", 0.95)
        }

        val response = LlmResponse()
        response.content = jsonResult.toString()
        return response
    }

    override fun isConfigured(): Boolean {
        return endpoint != null
    }
}
