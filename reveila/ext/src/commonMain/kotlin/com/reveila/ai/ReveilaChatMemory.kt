package com.reveila.ai

/**
 * Custom ChatMemory implementation for Reveila LLM interactions.
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

    open fun clear() {
        messages.clear()
    }
}
