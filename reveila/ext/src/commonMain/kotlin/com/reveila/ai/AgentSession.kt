package com.reveila.ai

/**
 * The AgentSession represents a stateful container for shared context
 * and conversation history within a single agentic workflow.
 */
open class AgentSession(
    val sessionId: String,
    val parentTraceId: String?,
    windowSize: Int = 20
) {
    companion object {
        const val ID: String = "_session_id"
        const val THOUGHT: String = "_thought"
    }

    val chatMemory: ReveilaChatMemory = ReveilaChatMemory(windowSize)
    val context: MutableMap<String, Any?> = mutableMapOf()

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
