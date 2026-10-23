package com.reveila.ai

import com.reveila.service.DataService
import com.reveila.system.SystemComponent
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.json.JsonUtil

/**
 * Phase 5: Stateful Context Persistence & Episodic Memory.
 * Manages agent sessions and histories with automated size-based eviction
 * and neutral persistence through the DataService repository abstraction.
 */
open class AgentSessionManager : SystemComponent() {

    private val sessionStore: MutableMap<String, AgentSession> = mutableMapOf()
    private var maxHistorySizeBytes: Long = Long.MAX_VALUE
    private var dataService: DataService? = null

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
                logger.warning("Invalid ai.optimization.history format: $historyLimit. Using unlimited.")
            }
        }
        resolveDataService()
    }

    @Throws(Exception::class)
    override fun onStop() {
        sessionStore.clear()
        dataService = null
    }

    private fun resolveDataService(): DataService? {
        if (dataService == null) {
            try {
                val p = context?.getProxy("DataService")
                dataService = (p?.getInstance() as? DataService) ?: p as? DataService
            } catch (e: Exception) {
                // Optional
            }
        }
        return dataService
    }

    open fun saveSession(id: String, session: AgentSession) {
        val newSessionSize = estimateSessionSize(session)
        var currentTotalSize = calculateTotalSize()

        // Size-aware LRU eviction in-memory
        while (currentTotalSize + newSessionSize > maxHistorySizeBytes && sessionStore.isNotEmpty()) {
            val oldestId = sessionStore.keys.iterator().next()
            val removed = sessionStore.remove(oldestId)
            if (removed != null) {
                currentTotalSize -= estimateSessionSize(removed)
                logger.info("Evicted session $oldestId from in-memory cache to respect history size limit.")
            }
        }
        sessionStore[id] = session

        // Persist episodic snapshot via neutral DataService repository
        persistSessionToStorage(id, session)
    }

    open fun getSession(id: String): AgentSession? {
        // 1. Check in-memory store
        sessionStore[id]?.let { return it }

        // 2. Fallback to neutral repository persistence
        return loadSessionFromStorage(id)
    }

    open fun clear(id: String) {
        sessionStore.remove(id)
        try {
            val ds = resolveDataService()
            if (ds != null) {
                val key = mapOf("sessionId" to mapOf("value" to id))
                ds.delete("AgentSession", key)
            }
        } catch (e: Exception) {
            logger.warning("Could not delete session from storage: ${e.message}")
        }
    }

    private fun persistSessionToStorage(id: String, session: AgentSession) {
        try {
            val ds = resolveDataService() ?: return
            val key = mapOf("sessionId" to mapOf("value" to id))

            val serializedMessages = session.chatMemory.messages().map { msg ->
                mapOf(
                    "role" to msg.role.name,
                    "content" to msg.content
                )
            }

            val attrs = mutableMapOf<String, Any>()
            attrs["sessionId"] = id
            attrs["parentTraceId"] = session.parentTraceId ?: ""
            attrs["contextJson"] = JsonUtil.toJsonString(session.getContextMap())
            attrs["messagesJson"] = JsonUtil.toJsonString(serializedMessages)
            attrs["updatedAt"] = PlatformSystem.currentTimeMillis()

            val entityMap = mapOf(
                "key" to key,
                "attributes" to attrs
            )
            ds.save("AgentSession", entityMap)
        } catch (e: Exception) {
            logger.warning("Failed to persist session to DataService: ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadSessionFromStorage(id: String): AgentSession? {
        try {
            val ds = resolveDataService() ?: return null
            val key = mapOf("sessionId" to mapOf("value" to id))
            val entity = ds.findById("AgentSession", key) ?: return null

            val attrs = entity.attributes
            val parentTraceId = attrs["parentTraceId"] as? String
            val restoredSession = AgentSession(id, parentTraceId)

            val contextJson = attrs["contextJson"] as? String
            if (!contextJson.isNullOrBlank()) {
                val contextMap = JsonUtil.parseJsonStringToMap(contextJson)
                contextMap.forEach { (k, v) -> restoredSession.put(k, v) }
            }

            val messagesJson = attrs["messagesJson"] as? String
            if (!messagesJson.isNullOrBlank()) {
                val msgList = JsonUtil.parseJsonStringToList(messagesJson)
                for (item in msgList) {
                    val roleStr = item["role"]?.toString() ?: "USER"
                    val content = item["content"]?.toString() ?: ""
                    val role = try {
                        LlmRole.valueOf(roleStr.uppercase())
                    } catch (e: Exception) {
                        LlmRole.USER
                    }
                    restoredSession.chatMemory.add(ReveilaMessage(role, content))
                }
            }

            sessionStore[id] = restoredSession
            return restoredSession
        } catch (e: Exception) {
            logger.warning("Failed to restore session from DataService: ${e.message}")
            return null
        }
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
        return session?.getContextMap() ?: mutableMapOf()
    }
}
