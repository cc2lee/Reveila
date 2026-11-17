package com.reveila.ai

/**
 * Custom ChatMemory implementation for Reveila LLM interactions with Space-aware filtering.
 */
open class ReveilaChatMemory(private val maxMessages: Int) {
    private val messages: MutableList<ReveilaMessage> = mutableListOf()

    open fun add(message: ReveilaMessage) {
        messages.add(message)
        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    open fun messages(): List<ReveilaMessage> {
        return messages.toList()
    }

    /**
     * Returns only messages belonging to the requested [spaceId].
     * Explicitly tagged messages from other spaces are always excluded.
     * Untagged messages (spaceId == null) are only included when [defaultSessionSpaceId] matches [spaceId].
     */
    open fun messagesForSpace(
        spaceId: String,
        defaultSessionSpaceId: String? = null
    ): List<ReveilaMessage> {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val fallbackMatches = defaultSessionSpaceId != null &&
            AgentSession.normalizeSpaceId(defaultSessionSpaceId) == target
        return messages.filter { msg ->
            val msgSpace = msg.spaceId?.trim()?.takeIf { it.isNotEmpty() }
            if (msgSpace != null) {
                AgentSession.normalizeSpaceId(msgSpace) == target
            } else {
                fallbackMatches
            }
        }
    }

    open fun replaceMessages(newMessages: List<ReveilaMessage>) {
        messages.clear()
        val tail = if (newMessages.size > maxMessages) {
            newMessages.takeLast(maxMessages)
        } else {
            newMessages
        }
        messages.addAll(tail)
    }

    open fun clear() {
        messages.clear()
    }
}
