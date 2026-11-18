package com.reveila.ai

import kotlinx.coroutines.flow.Flow

/**
 * Vendor-agnostic in-process native inference delegate (e.g., Android JNI llama.cpp mmap engine).
 * Eliminates localhost HTTP round-trips when an in-process engine is loaded.
 */
interface OnDeviceInferenceDelegate {
    fun isModelLoaded(): Boolean
    fun getLoadedModelPath(): String? = null
    fun formatChatMessages(messages: List<ReveilaMessage>): Pair<String, String>? = null
    fun generateBlocking(
        formattedPrompt: String,
        stopToken: String,
        maxTokens: Int = 256,
        grammar: String = ""
    ): String = generateBlocking(formattedPrompt, stopToken, maxTokens)
    fun generateBlocking(formattedPrompt: String, stopToken: String, maxTokens: Int = 256): String
    fun generateStream(formattedPrompt: String, stopToken: String, maxTokens: Int = 256): Flow<String>
    fun scheduleRelease()
}
