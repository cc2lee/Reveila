package com.reveila.ai

import java.util.ArrayList
import java.util.Collections

/**
 * Custom ChatMemory implementation for Reveila LLM interactions.
 */
open class ReveilaChatMemory(private val maxMessages: Int) {
    private val messages: MutableList<ReveilaMessage> = Collections.synchronizedList(ArrayList())

    open fun add(message: ReveilaMessage) {
        messages.add(message)
        while (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    open fun messages(): List<ReveilaMessage> {
        return ArrayList(messages)
    }

    open fun clear() {
        messages.clear()
    }
}
