package com.reveila.ai

import kotlin.jvm.JvmStatic

/**
 * Native message type for Reveila LLM interactions.
 */
data class ReveilaMessage(
    val role: LlmRole,
    val content: String
) {
    fun role(): LlmRole = role
    fun content(): String = content

    companion object {
        @JvmStatic
        fun system(content: String): ReveilaMessage = ReveilaMessage(LlmRole.SYSTEM, content)

        @JvmStatic
        fun user(content: String): ReveilaMessage = ReveilaMessage(LlmRole.USER, content)

        @JvmStatic
        fun assistant(content: String): ReveilaMessage = ReveilaMessage(LlmRole.ASSISTANT, content)

        @JvmStatic
        fun tool(content: String): ReveilaMessage = ReveilaMessage(LlmRole.TOOL, content)
    }
}
