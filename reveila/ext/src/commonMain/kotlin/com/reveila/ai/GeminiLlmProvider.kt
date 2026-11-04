package com.reveila.ai

open class GeminiLlmProvider : OpenAiLlmProvider() {

    init {
        this.endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    }

    @Throws(LlmException::class)
    override fun buildRequestBody(request: LlmRequest): String {
        val originalModel = model

        val activeModel = if (!model.isNullOrBlank()) model else request.modelId
        if (activeModel != null && (activeModel == "gemini-1.5-pro" ||
                activeModel == "gemini-1.5-pro-latest" ||
                activeModel == "gemini-2.0-flash")
        ) {
            this.model = "gemini-3-flash"
        }

        val body = super.buildRequestBody(request)
        this.model = originalModel

        return body
    }

    @Throws(LlmException::class)
    override fun getHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val key: String?
        try {
            key = resolveApiKey()
        } catch (e: Exception) {
            throw LlmException("Failed to resolve API key for Gemini provider: ${e.message}", e)
        }

        headers["Authorization"] = "Bearer $key"
        headers["Content-Type"] = "application/json"
        return headers
    }
}
