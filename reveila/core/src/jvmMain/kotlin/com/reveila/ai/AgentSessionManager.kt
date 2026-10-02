package com.reveila.ai

import com.reveila.system.SystemComponent
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 5: Stateful Context Persistence.
 * Manages agent sessions and histories with automated size-based eviction.
 */
open class AgentSessionManager : SystemComponent() {

    private val sessionStore: MutableMap<String, AgentSession> = Collections.synchronizedMap(
        LinkedHashMap(16, 0.75f, true)
    )

    private var maxHistorySizeBytes: Long = Long.MAX_VALUE

    @Throws(Exception::class)
    override fun onStart() {
        val historyLimit = context?.properties?.getProperty("ai.optimization.history", "unlimited")
        if (historyLimit != null && !"unlimited".equals(historyLimit, ignoreCase = true)) {
            try {
                if (historyLimit.uppercase().endsWith("MB")) {
                    maxHistorySizeBytes = historyLimit.substring(0, historyLimit.length - 2).toLong() * 1024 * 1024
                } else if (historyLimit.uppercase().endsWith("KB")) {
                    maxHistorySizeBytes = historyLimit.substring(0, historyLimit.length - 2).toLong() * 1024
                } else {
                    maxHistorySizeBytes = historyLimit.toLong()
                }
            } catch (e: NumberFormatException) {
                logger?.warning("Invalid ai.optimization.history format: $historyLimit. Using unlimited.")
            }
        }
    }

    @Throws(Exception::class)
    override fun onStop() {
        sessionStore.clear()
    }

    open fun saveSession(id: String, session: AgentSession) {
        val newSessionSize = estimateSessionSize(session)

        synchronized(sessionStore) {
            var currentTotalSize = calculateTotalSize()

            while (currentTotalSize + newSessionSize > maxHistorySizeBytes && sessionStore.isNotEmpty()) {
                val oldestId = sessionStore.keys.iterator().next()
                val removed = sessionStore.remove(oldestId)
                if (removed != null) {
                    currentTotalSize -= estimateSessionSize(removed)
                    logger?.info("Evicted session $oldestId to respect history size limit.")
                }
            }
            sessionStore[id] = session
        }
    }

    open fun getSession(id: String): AgentSession? = sessionStore[id]

    open fun clear(id: String) {
        sessionStore.remove(id)
    }

    private fun calculateTotalSize(): Long {
        return sessionStore.values.sumOf { estimateSessionSize(it) }
    }

    private fun estimateSessionSize(session: AgentSession?): Long {
        if (session == null) return 0

        var size: Long = 0
        val chatMem = session.chatMemory
        for (msg in chatMem.messages()) {
            val text = msg.content
            size += text.length * 2L
        }

        for (entry in session.getContextMap().entries) {
            size += entry.key.length * 2L
            val value = entry.value
            if (value is String) {
                size += value.length * 2L
            } else {
                size += 128
            }
        }
        return size
    }

    open fun saveContext(traceId: String, context: Map<String, Any?>?) {
        val session = AgentSession(traceId, traceId)
        if (context != null) {
            context.forEach { (k, v) -> session.put(k, v) }
        }
        saveSession(traceId, session)
    }

    open fun getContext(traceId: String): MutableMap<String, Any?> {
        val session = getSession(traceId)
        return session?.getContextMap() ?: ConcurrentHashMap()
    }
}
