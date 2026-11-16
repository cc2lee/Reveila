package com.reveila.ai

interface LlmProvider {
    @Throws(LlmException::class)
    fun invoke(request: LlmRequest): LlmResponse
    fun isEnabled(): Boolean
    fun isConfigured(): Boolean
    fun getName(): String
    fun getSupportedIntents(): List<String> = emptyList()
    fun supportsIntent(intentCategory: String): Boolean {
        val supported = getSupportedIntents()
        if (supported.isEmpty()) return false
        return supported.any { it.equals(intentCategory.trim(), ignoreCase = true) }
    }
}
