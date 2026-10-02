package com.reveila.ai

/**
 * Native message type for Reveila LLM interactions.
 */
data class ReveilaMessage(
    val role: LlmRole,
    val content: String
) {
    // Record accessor compatibility for Java callers
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
