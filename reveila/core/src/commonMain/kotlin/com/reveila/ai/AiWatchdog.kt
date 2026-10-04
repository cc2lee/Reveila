package com.reveila.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

open class AiWatchdog(
    private val remoteLlmProvider: LlmProvider,
    private val localOllama: LlmProvider
) {
    companion object {
        private const val REMOTE_TIMEOUT_MILLIS = 10000L
    }

    open fun getResponse(userPrompt: String): String = runBlocking {
        try {
            val result = withTimeoutOrNull(REMOTE_TIMEOUT_MILLIS) {
                withContext(Dispatchers.Default) {
                    val request = LlmRequest.builder()
                        .addMessage(ReveilaMessage.system("You are a helpful assistant."))
                        .addMessage(ReveilaMessage.user(userPrompt))
                        .build()
                    remoteLlmProvider.invoke(request).content
                }
            }
            result ?: fallbackToLocal(userPrompt)
        } catch (e: Exception) {
            "Error connecting to AI: ${e.message}"
        }
    }

    private fun fallbackToLocal(prompt: String): String {
        return try {
            val request = LlmRequest.builder()
                .addMessage(ReveilaMessage.system("You are a helpful assistant."))
                .addMessage(ReveilaMessage.user(prompt))
                .build()
            "NOTICE: Remote AI is slow. Using Local Model: ${localOllama.invoke(request).content}"
        } catch (e: Exception) {
            "NOTICE: Remote AI is slow. Using Local Model: Error - ${e.message}"
        }
    }
}
