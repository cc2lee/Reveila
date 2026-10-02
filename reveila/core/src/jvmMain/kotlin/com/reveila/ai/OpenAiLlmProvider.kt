package com.reveila.ai

import java.util.HashMap
import org.json.JSONArray
import org.json.JSONObject

open class OpenAiLlmProvider : BaseLlmProvider() {

    init {
        this.endpoint = "https://api.openai.com/v1/chat/completions"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val body = JSONObject()
        val activeModel = if (!model.isNullOrBlank()) model else request.modelId

        body.put("model", activeModel)
        body.put("temperature", temperature)
        body.put("stream", false)

        val messages = JSONArray()
        for (msg in request.messages) {
            val msgJson = JSONObject()
            msgJson.put("role", msg.role.name.lowercase())
            msgJson.put("content", msg.content)
            messages.put(msgJson)
        }
        body.put("messages", messages)

        if (request.tools.isNotEmpty()) {
            val toolsJson = JSONArray()
            for (tool in request.tools) {
                try {
                    toolsJson.put(JSONObject(tool.toJsonString()))
                } catch (e: Exception) {
                    throw LlmException("Failed to serialize tool: ${e.message}", e)
                }
            }
            body.put("tools", toolsJson)
        }
        return body.toString()
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = HashMap<String, String>()
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
        val resp = JSONObject(json)
        val response = LlmResponse()

        val choices = resp.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val message = choices.getJSONObject(0).getJSONObject("message")
            response.content = message.optString("content", "")
        }
        return response
    }

    override fun isConfigured(): Boolean {
        return endpoint != null && apiKey != null
    }
}
