package com.reveila.ai

import com.reveila.system.platform.PlatformSystem
import kotlin.jvm.JvmOverloads

/**
 * The AgentSession represents a stateful container for shared context
 * and conversation history organized within a persisted Space.
 */
open class AgentSession @JvmOverloads constructor(
    val sessionId: String,
    val parentTraceId: String?,
    windowSize: Int = 20,
    spaceId: String = DEFAULT_SPACE_ID,
    spaceName: String = DEFAULT_SPACE_NAME
) {
    companion object {
        const val ID: String = "_session_id"
        const val THOUGHT: String = "_thought"
        const val SPACE_ID: String = "_space_id"
        const val SPACE_NAME: String = "_space_name"
        const val SPACE_SUMMARY: String = "_space_summary"
        const val DEFAULT_SPACE_ID: String = "main-space"
        const val DEFAULT_SPACE_NAME: String = "Main Space"
        const val CONVERSATION_SUMMARY_PREFIX: String = "Earlier conversation summary:"

        fun isConversationSummaryMessage(role: LlmRole, content: String): Boolean =
            role == LlmRole.SYSTEM && content.trim().startsWith(CONVERSATION_SUMMARY_PREFIX, ignoreCase = true)

        fun extractConversationSummaryBody(content: String): String {
            val trimmed = content.trim()
            return if (trimmed.startsWith(CONVERSATION_SUMMARY_PREFIX, ignoreCase = true)) {
                trimmed.substring(CONVERSATION_SUMMARY_PREFIX.length).trim()
            } else {
                trimmed
            }
        }

        fun normalizeSpaceId(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return DEFAULT_SPACE_ID
            val unprefixed = when {
                trimmed.startsWith("space:", ignoreCase = true) -> trimmed.substring(6).trim()
                trimmed.startsWith("space-", ignoreCase = true) && trimmed.length > 6 -> trimmed.substring(6).trim()
                else -> trimmed
            }
            if (unprefixed.isEmpty()) return DEFAULT_SPACE_ID
            val slug = buildString {
                var prevDash = false
                for (ch in unprefixed.lowercase()) {
                    if (ch in 'a'..'z' || ch in '0'..'9') {
                        append(ch)
                        prevDash = false
                    } else if (!prevDash && isNotEmpty()) {
                        append('-')
                        prevDash = true
                    }
                }
            }.trim('-')
            return if (slug.isEmpty()) DEFAULT_SPACE_ID else slug
        }

        fun displayNameForSpace(spaceId: String, preferredName: String? = null): String {
            val cleaned = preferredName?.trim().orEmpty()
            if (cleaned.isNotEmpty() && !cleaned.startsWith("space:", ignoreCase = true) && !cleaned.startsWith("space-", ignoreCase = true)) {
                return cleaned
            }
            val normalized = normalizeSpaceId(spaceId)
            return normalized.split("-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
                }
                .ifBlank { DEFAULT_SPACE_NAME }
        }

        fun sessionIdForSpace(spaceId: String): String = "space-${normalizeSpaceId(spaceId)}"

        fun looksLikeUuid(value: String?): Boolean {
            if (value == null || value.length != 36) return false
            for (i in value.indices) {
                val c = value[i]
                if (i == 8 || i == 13 || i == 18 || i == 23) {
                    if (c != '-') return false
                } else {
                    val isHex = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
                    if (!isHex) return false
                }
            }
            return true
        }
    }

    var spaceId: String = normalizeSpaceId(spaceId)
        private set

    var spaceName: String = displayNameForSpace(this.spaceId, spaceName)
        private set

    var summary: String = ""
        private set

    var updatedAt: Long = PlatformSystem.currentTimeMillis()

    val chatMemory: ReveilaChatMemory = ReveilaChatMemory(windowSize)
    val context: MutableMap<String, Any?> = mutableMapOf()

    init {
        context[ID] = sessionId
        context[SPACE_ID] = this.spaceId
        context[SPACE_NAME] = this.spaceName
    }

    open fun assignSpace(spaceId: String?, spaceName: String? = null) {
        this.spaceId = normalizeSpaceId(spaceId)
        this.spaceName = displayNameForSpace(this.spaceId, spaceName)
        context[SPACE_ID] = this.spaceId
        context[SPACE_NAME] = this.spaceName
        updatedAt = PlatformSystem.currentTimeMillis()
    }

    open fun updateSummary(summaryText: String?) {
        val cleaned = summaryText?.trim().orEmpty()
        this.summary = cleaned
        if (cleaned.isNotEmpty()) {
            context[SPACE_SUMMARY] = cleaned
        } else {
            context.remove(SPACE_SUMMARY)
        }
        updatedAt = PlatformSystem.currentTimeMillis()
    }

    open fun addMessage(message: ReveilaMessage) {
        val ts = if (message.timestamp > 0L) message.timestamp else PlatformSystem.currentTimeMillis()
        val msgSpace = message.spaceId?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { normalizeSpaceId(it) }
            ?: this.spaceId
        chatMemory.add(message.copy(spaceId = msgSpace, timestamp = ts))
        updatedAt = ts
    }

    open fun addMessage(role: LlmRole, content: String, spaceId: String? = null, timestamp: Long = 0L) {
        val ts = if (timestamp > 0L) timestamp else PlatformSystem.currentTimeMillis()
        val resolvedSpace = normalizeSpaceId(spaceId ?: this.spaceId)
        chatMemory.add(ReveilaMessage(role = role, content = content, spaceId = resolvedSpace, timestamp = ts))
        updatedAt = ts
    }

    open fun messagesForSpace(targetSpaceId: String = this.spaceId): List<ReveilaMessage> =
        chatMemory.messagesForSpace(targetSpaceId, this.spaceId)

    open fun put(key: String, value: Any?) {
        if (value != null) {
            context[key] = value
        } else {
            context.remove(key)
        }
    }

    open fun get(key: String): Any? = context[key]

    open fun getContextMap(): MutableMap<String, Any?> = context
}
