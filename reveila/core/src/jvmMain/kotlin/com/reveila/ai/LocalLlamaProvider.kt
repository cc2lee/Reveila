package com.reveila.ai

import com.reveila.ai.util.GemmaPromptFormatter
import com.reveila.ai.util.Llama3PromptFormatter
import java.util.HashMap
import org.json.JSONArray
import org.json.JSONObject

open class LocalLlamaProvider : BaseLlmProvider() {

    init {
        this.endpoint = "http://localhost:8888/completion"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val body = JSONObject()

        val formattedPrompt: String
        val activeModel = model?.lowercase() ?: ""

        if (activeModel.contains("llama-3") || activeModel.contains("llama3")) {
            formattedPrompt = Llama3PromptFormatter.format(request.messages)
            body.put("stop", JSONArray().put("<|eot_id|>"))
        } else {
            formattedPrompt = GemmaPromptFormatter.format(request.messages)
            body.put("stop", JSONArray().put("<end_of_turn>"))
        }

        body.put("prompt", formattedPrompt)
        body.put("temperature", temperature)
        body.put("n_predict", 1024)
        body.put("stream", false)

        return body.toString()
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"
        return headers
    }

    @Throws(LlmException::class)
    override fun parseResponse(json: String): LlmResponse {
        val resp = JSONObject(json)
        val llmResponse = LlmResponse()

        val content = resp.optString("content", "").trim()
        llmResponse.content = content

        return llmResponse
    }

    override fun isConfigured(): Boolean {
        return endpoint != null
    }
}
