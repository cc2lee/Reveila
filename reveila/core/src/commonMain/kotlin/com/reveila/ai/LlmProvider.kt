package com.reveila.ai

interface LlmProvider {
    @Throws(LlmException::class)
    fun invoke(request: LlmRequest): LlmResponse
    fun isEnabled(): Boolean
    fun isConfigured(): Boolean
    fun getName(): String
}
