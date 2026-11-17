package com.reveila.ai

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Native message type for Reveila LLM interactions, optionally tagged with its owning Space.
 */
data class ReveilaMessage(
    val role: LlmRole,
    val content: String,
    val spaceId: String? = null,
    val timestamp: Long = 0L
) {
    fun role(): LlmRole = role
    fun content(): String = content

    fun withSpace(spaceId: String, timestamp: Long = this.timestamp): ReveilaMessage =
        copy(spaceId = AgentSession.normalizeSpaceId(spaceId), timestamp = timestamp)

    companion object {
        @JvmStatic
        @JvmOverloads
        fun system(content: String, spaceId: String? = null, timestamp: Long = 0L): ReveilaMessage =
            ReveilaMessage(LlmRole.SYSTEM, content, spaceId, timestamp)

        @JvmStatic
        @JvmOverloads
        fun user(content: String, spaceId: String? = null, timestamp: Long = 0L): ReveilaMessage =
            ReveilaMessage(LlmRole.USER, content, spaceId, timestamp)

        @JvmStatic
        @JvmOverloads
        fun assistant(content: String, spaceId: String? = null, timestamp: Long = 0L): ReveilaMessage =
            ReveilaMessage(LlmRole.ASSISTANT, content, spaceId, timestamp)

        @JvmStatic
        @JvmOverloads
        fun tool(content: String, spaceId: String? = null, timestamp: Long = 0L): ReveilaMessage =
            ReveilaMessage(LlmRole.TOOL, content, spaceId, timestamp)
    }
}
