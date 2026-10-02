package com.reveila.ai

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class AiWatchdog(
    private val remoteLlmProvider: LlmProvider,
    private val localOllama: LlmProvider
) {
    companion object {
        private const val REMOTE_TIMEOUT_SECONDS = 10L
    }

    open fun getResponse(userPrompt: String): String {
        val remoteCall = CompletableFuture.supplyAsync {
            try {
                val request = LlmRequest.builder()
                    .addMessage(ReveilaMessage.system("You are a helpful assistant."))
                    .addMessage(ReveilaMessage.user(userPrompt))
                    .build()
                remoteLlmProvider.invoke(request).content
            } catch (e: Exception) {
                throw CompletionException(e)
            }
        }

        return try {
            remoteCall.get(REMOTE_TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: fallbackToLocal(userPrompt)
        } catch (e: TimeoutException) {
            System.err.println("Remote LLM is slow. Activating UX Recovery...")
            remoteCall.cancel(true)
            fallbackToLocal(userPrompt)
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
