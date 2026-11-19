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

        // 2. Fallback to neutral repository persistence (with Space hydration)
        return loadSessionEntityFromStorage(id)?.also { restored ->
            sessionStore[id] = restored
        }
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
            val resolvedSpaceId = resolveSessionSpaceId(session, id)
            val resolvedSpaceName = AgentSession.displayNameForSpace(
                resolvedSpaceId,
                session.spaceName.takeIf { it.isNotBlank() }
                    ?: session.get(AgentSession.SPACE_NAME)?.toString()
            )
            if (session.spaceId != resolvedSpaceId || session.spaceName != resolvedSpaceName) {
                session.assignSpace(resolvedSpaceId, resolvedSpaceName)
            }
            val now = PlatformSystem.currentTimeMillis()
            session.updatedAt = now

            val key = mapOf("sessionId" to mapOf("value" to id))

            val serializedMessages = session.chatMemory.messages().map { msg ->
                val msgSpace = AgentSession.normalizeSpaceId(
                    msg.spaceId?.takeIf { it.isNotBlank() } ?: resolvedSpaceId
                )
                val msgTs = if (msg.timestamp > 0L) msg.timestamp else now
                mapOf(
                    "role" to msg.role.name,
                    "content" to msg.content,
                    "spaceId" to msgSpace,
                    "sessionId" to id,
                    "timestamp" to msgTs
                )
            }

            val attrs = mutableMapOf<String, Any>()
            attrs["sessionId"] = id
            attrs["spaceId"] = resolvedSpaceId
            attrs["spaceName"] = resolvedSpaceName
            attrs["summary"] = session.summary
            attrs["parentTraceId"] = session.parentTraceId ?: ""
            attrs["contextJson"] = JsonUtil.toJsonString(session.getContextMap())
            attrs["messagesJson"] = JsonUtil.toJsonString(serializedMessages)
            attrs["messageCount"] = serializedMessages.size
            attrs["updatedAt"] = now

            val entityMap = mapOf(
                "key" to key,
                "attributes" to attrs
            )
            ds.save("AgentSession", entityMap)
            upsertChatSpaceEntity(ds, resolvedSpaceId, resolvedSpaceName, id, session.summary, serializedMessages, now)
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
            val explicitSpace = context[AgentSession.SPACE_ID]?.toString()
            val explicitSpaceName = context[AgentSession.SPACE_NAME]?.toString()
            if (!explicitSpace.isNullOrBlank()) {
                session.assignSpace(explicitSpace, explicitSpaceName)
            }
            context[AgentSession.SPACE_SUMMARY]?.toString()?.takeIf { it.isNotBlank() }?.let {
                session.updateSummary(it)
            }
        }
        saveSession(traceId, session)
    }

    open fun getContext(traceId: String): MutableMap<String, Any?> {
        val session = getSession(traceId)
        return session?.getContextMap() ?: mutableMapOf()
    }

    private fun resolveSessionSpaceId(session: AgentSession, idFallback: String): String {
        val raw = session.spaceId.takeIf { it.isNotBlank() && it != AgentSession.DEFAULT_SPACE_ID }
            ?: session.get(AgentSession.SPACE_ID)?.toString()?.takeIf { it.isNotBlank() }
            ?: session.spaceId.takeIf { it.isNotBlank() }
            ?: if (!AgentSession.looksLikeUuid(idFallback)) idFallback else AgentSession.DEFAULT_SPACE_ID
        return AgentSession.normalizeSpaceId(raw)
    }

    private fun loadSessionEntityFromStorage(id: String): AgentSession? {
        return try {
            val ds = resolveDataService() ?: return null
            val key = mapOf("sessionId" to mapOf("value" to id))
            val entity = ds.findById("AgentSession", key) ?: return null
            restoreEntityToSession(entity)
        } catch (e: Exception) {
            logger.warning("Failed to load session [$id] from DataService: ${e.message}")
            null
        }
    }

    private fun restoreEntityToSession(entity: com.reveila.data.Entity): AgentSession? {
        val attrs = entity.attributes
        val id = attrs["sessionId"]?.toString()?.takeIf { it.isNotBlank() }
            ?: entity.key?.get("sessionId")?.get("value")?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        val parentTraceId = attrs["parentTraceId"]?.toString()?.takeIf { it.isNotBlank() }

        val contextJson = attrs["contextJson"]?.toString()
        val restoredContext: Map<String, Any?> = if (!contextJson.isNullOrBlank()) {
            try {
                JsonUtil.parseJsonStringToMap(contextJson)
            } catch (_: Exception) {
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val rawSpaceId = attrs["spaceId"]?.toString()?.takeIf { it.isNotBlank() }
            ?: restoredContext[AgentSession.SPACE_ID]?.toString()?.takeIf { it.isNotBlank() }
            ?: if (!AgentSession.looksLikeUuid(id)) id else AgentSession.DEFAULT_SPACE_ID
        val resolvedSpaceId = AgentSession.normalizeSpaceId(rawSpaceId)
        val resolvedSpaceName = AgentSession.displayNameForSpace(
            resolvedSpaceId,
            attrs["spaceName"]?.toString()?.takeIf { it.isNotBlank() }
                ?: restoredContext[AgentSession.SPACE_NAME]?.toString()
        )
        val restoredSession = AgentSession(id, parentTraceId, 20, resolvedSpaceId, resolvedSpaceName)

        restoredContext.forEach { (k, v) -> restoredSession.put(k, v) }
        restoredSession.assignSpace(resolvedSpaceId, resolvedSpaceName)

        val restoredSummary = attrs["summary"]?.toString()?.takeIf { it.isNotBlank() }
            ?: restoredContext[AgentSession.SPACE_SUMMARY]?.toString()
        if (!restoredSummary.isNullOrBlank()) {
            restoredSession.updateSummary(restoredSummary)
        }

        val updatedAt = (attrs["updatedAt"] as? Number)?.toLong() ?: 0L
        if (updatedAt > 0L) {
            restoredSession.updatedAt = updatedAt
        }

        val messagesJson = attrs["messagesJson"]?.toString()
        if (!messagesJson.isNullOrBlank()) {
            try {
                val msgList = JsonUtil.parseJsonStringToList(messagesJson)
                for (entry in msgList) {
                    val roleStr = entry["role"]?.toString()?.uppercase() ?: "USER"
                    val role = when (roleStr) {
                        "SYSTEM" -> LlmRole.SYSTEM
                        "ASSISTANT" -> LlmRole.ASSISTANT
                        "TOOL" -> LlmRole.TOOL
                        else -> LlmRole.USER
                    }
                    val content = entry["content"]?.toString() ?: ""
                    val msgSpace = entry["spaceId"]?.toString()?.takeIf { it.isNotBlank() }
                        ?.let { AgentSession.normalizeSpaceId(it) }
                        ?: resolvedSpaceId
                    val msgTs = (entry["timestamp"] as? Number)?.toLong() ?: updatedAt
                    restoredSession.chatMemory.add(
                        ReveilaMessage(
                            role = role,
                            content = content,
                            spaceId = msgSpace,
                            timestamp = msgTs
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warning("Failed to parse persisted messages for session [$id]: ${e.message}")
            }
        }
        return restoredSession
    }

    private fun upsertChatSpaceEntity(
        ds: DataService,
        spaceId: String,
        spaceName: String,
        sessionId: String,
        sessionSummary: String,
        latestMessages: List<Map<String, Any>>,
        updatedAt: Long
    ) {
        val spaceKey = mapOf("spaceId" to mapOf("value" to spaceId))
        val existingSpace = ds.findById("ChatSpace", spaceKey)
        val existingSummary = existingSpace?.attributes?.get("summary")?.toString().orEmpty()
        val effectiveSummary = sessionSummary.takeIf { it.isNotBlank() } ?: existingSummary

        val allSpaceMsgs = getFilteredMessagesForSpace(spaceId, maxMessages = 500)
        val totalCount = maxOf(allSpaceMsgs.size, latestMessages.size)
        val rawPreview = allSpaceMsgs.lastOrNull { it.role == LlmRole.USER || it.role == LlmRole.ASSISTANT }
            ?.content
            ?: latestMessages.lastOrNull()?.get("content")?.toString()
            ?: ""
        val preview = extractReadablePreview(rawPreview)

        val attrs = mutableMapOf<String, Any>()
        attrs["spaceId"] = spaceId
        attrs["spaceName"] = spaceName
        attrs["latestSessionId"] = sessionId
        attrs["summary"] = effectiveSummary
        attrs["messageCount"] = totalCount
        attrs["lastMessagePreview"] = preview
        attrs["updatedAt"] = updatedAt

        ds.save("ChatSpace", mapOf("key" to spaceKey, "attributes" to attrs))
    }

    private fun collectAllMergedSessions(): List<AgentSession> {
        val merged = linkedMapOf<String, AgentSession>()
        try {
            val ds = resolveDataService()
            val allEntities = ds?.getRepository("AgentSession")?.fetchAll().orEmpty()
            for (entity in allEntities) {
                val restored = restoreEntityToSession(entity) ?: continue
                merged[restored.sessionId] = restored
            }
        } catch (e: Exception) {
            logger.warning("Failed to query persisted AgentSession entities: ${e.message}")
        }
        for ((id, memSession) in sessionStore) {
            val existing = merged[id]
            if (existing == null || memSession.updatedAt >= existing.updatedAt) {
                merged[id] = memSession
            }
        }
        return merged.values.toList()
    }

    /**
     * Returns all persisted and in-memory sessions organized under [spaceId], sorted chronologically.
     */
    open fun findSessionsBySpace(spaceId: String): List<AgentSession> {
        val target = AgentSession.normalizeSpaceId(spaceId)
        return collectAllMergedSessions()
            .filter { session ->
                AgentSession.normalizeSpaceId(session.spaceId) == target ||
                    session.chatMemory.messages().any {
                        !it.spaceId.isNullOrBlank() && AgentSession.normalizeSpaceId(it.spaceId) == target
                    }
            }
            .sortedWith(compareBy({ it.updatedAt }, { it.sessionId }))
    }

    /**
     * Returns the most recently updated persisted/in-memory session belonging to [spaceId].
     */
    open fun findLatestSessionForSpace(spaceId: String): AgentSession? {
        return findSessionsBySpace(spaceId).maxByOrNull { it.updatedAt }
    }

    /**
     * Filters persisted & active chat history strictly by [spaceId] in chronological order.
     */
    open fun getFilteredMessagesForSpace(
        spaceId: String,
        excludeSessionId: String? = null,
        maxMessages: Int = 40
    ): List<ReveilaMessage> {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val filtered = mutableListOf<ReveilaMessage>()
        for (session in findSessionsBySpace(target)) {
            if (excludeSessionId != null && session.sessionId == excludeSessionId) continue
            filtered.addAll(session.messagesForSpace(target))
        }
        val ordered = filtered.sortedBy { it.timestamp }
        val lastSummaryIdx = ordered.indexOfLast {
            AgentSession.isConversationSummaryMessage(it.role, it.content)
        }
        val scoped = if (lastSummaryIdx >= 0) ordered.drop(lastSummaryIdx) else ordered
        return if (maxMessages > 0 && scoped.size > maxMessages) {
            if (lastSummaryIdx >= 0 && maxMessages > 1) {
                listOf(scoped.first()) + scoped.drop(1).takeLast(maxMessages - 1)
            } else {
                scoped.takeLast(maxMessages)
            }
        } else {
            scoped
        }
    }

    /**
     * Retrieves the persisted conversation summary for [spaceId], if any.
     */
    open fun getSpaceSummary(spaceId: String): String {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val fromSession = findSessionsBySpace(target)
            .sortedByDescending { it.updatedAt }
            .firstOrNull { it.summary.isNotBlank() }
            ?.summary
        if (!fromSession.isNullOrBlank()) return fromSession

        return try {
            val ds = resolveDataService() ?: return ""
            val spaceKey = mapOf("spaceId" to mapOf("value" to target))
            ds.findById("ChatSpace", spaceKey)?.attributes?.get("summary")?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    open fun saveSpaceSummary(spaceId: String, spaceName: String? = null, summary: String) {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val display = AgentSession.displayNameForSpace(target, spaceName)
        val matching = findSessionsBySpace(target)
        val now = PlatformSystem.currentTimeMillis()
        if (matching.isNotEmpty()) {
            for (sess in matching) {
                if (AgentSession.normalizeSpaceId(sess.spaceId) == target) {
                    sess.updateSummary(summary)
                    saveSession(sess.sessionId, sess)
                }
            }
        }
        try {
            val ds = resolveDataService() ?: return
            val spaceKey = mapOf("spaceId" to mapOf("value" to target))
            val existing = ds.findById("ChatSpace", spaceKey)
            val attrs = mutableMapOf<String, Any>()
            attrs["spaceId"] = target
            attrs["spaceName"] = existing?.attributes?.get("spaceName")?.toString()?.takeIf { it.isNotBlank() } ?: display
            attrs["latestSessionId"] = existing?.attributes?.get("latestSessionId")?.toString() ?: AgentSession.sessionIdForSpace(target)
            attrs["summary"] = summary
            attrs["messageCount"] = (existing?.attributes?.get("messageCount") as? Number)?.toInt() ?: 0
            attrs["lastMessagePreview"] = existing?.attributes?.get("lastMessagePreview")?.toString().orEmpty()
            attrs["updatedAt"] = now
            ds.save("ChatSpace", mapOf("key" to spaceKey, "attributes" to attrs))
        } catch (e: Exception) {
            logger.warning("Failed to persist summary for space [$target]: ${e.message}")
        }
    }

    open fun getSpaceHistoryMaps(spaceId: String, limit: Int = 100): List<Map<String, Any?>> {
        val target = AgentSession.normalizeSpaceId(spaceId)
        return getFilteredMessagesForSpace(target, maxMessages = limit).map { msg ->
            mapOf(
                "role" to msg.role.name,
                "content" to msg.content,
                "spaceId" to (msg.spaceId ?: target),
                "timestamp" to msg.timestamp
            )
        }
    }

    open fun getSpaceHistoryJson(spaceId: String, limit: Int = 100): String {
        return JsonUtil.toJsonString(getSpaceHistoryMaps(spaceId, limit))
    }

    open fun listPersistedSpaces(): List<Map<String, Any?>> {
        val spacesById = linkedMapOf<String, MutableMap<String, Any?>>()
        try {
            val ds = resolveDataService()
            val chatSpaceEntities = ds?.getRepository("ChatSpace")?.fetchAll().orEmpty()
            for (entity in chatSpaceEntities) {
                val attrs = entity.attributes
                val spaceId = attrs["spaceId"]?.toString()?.takeIf { it.isNotBlank() } ?: continue
                val normalized = AgentSession.normalizeSpaceId(spaceId)
                spacesById[normalized] = linkedMapOf(
                    "spaceId" to normalized,
                    "spaceName" to AgentSession.displayNameForSpace(normalized, attrs["spaceName"]?.toString()),
                    "latestSessionId" to (attrs["latestSessionId"]?.toString() ?: AgentSession.sessionIdForSpace(normalized)),
                    "summary" to (attrs["summary"]?.toString() ?: ""),
                    "messageCount" to ((attrs["messageCount"] as? Number)?.toInt() ?: 0),
                    "lastMessagePreview" to (attrs["lastMessagePreview"]?.toString() ?: ""),
                    "updatedAt" to ((attrs["updatedAt"] as? Number)?.toLong() ?: 0L)
                )
            }
        } catch (_: Exception) {
        }

        for (session in collectAllMergedSessions()) {
            val normalized = AgentSession.normalizeSpaceId(session.spaceId)
            val msgs = session.messagesForSpace(normalized)
            val existing = spacesById[normalized]
            val existingUpdated = (existing?.get("updatedAt") as? Number)?.toLong() ?: 0L
            if (existing == null || session.updatedAt > existingUpdated) {
                val rawPreview = msgs.lastOrNull { it.role == LlmRole.USER || it.role == LlmRole.ASSISTANT }
                    ?.content
                    ?: existing?.get("lastMessagePreview")?.toString().orEmpty()
                val preview = extractReadablePreview(rawPreview)
                val existingSummary = existing?.get("summary")?.toString().orEmpty()
                spacesById[normalized] = linkedMapOf(
                    "spaceId" to normalized,
                    "spaceName" to AgentSession.displayNameForSpace(normalized, session.spaceName),
                    "latestSessionId" to session.sessionId,
                    "summary" to session.summary.ifBlank { existingSummary },
                    "messageCount" to msgs.size,
                    "lastMessagePreview" to preview,
                    "updatedAt" to session.updatedAt
                )
            }
        }

        return spacesById.values.sortedByDescending { (it["updatedAt"] as? Number)?.toLong() ?: 0L }
    }

    private fun extractReadablePreview(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val readable = if (trimmed.startsWith("{")) {
            try {
                val map = JsonUtil.parseJsonStringToMap(trimmed)
                val res = map["result"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                val reas = map["reasoning"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                res ?: reas ?: trimmed
            } catch (_: Exception) {
                trimmed
            }
        } else {
            trimmed
        }
        return readable.replace('\n', ' ').replace(Regex("\\s+"), " ").trim().take(140)
    }

    open fun listSpacesJson(): String {
        return JsonUtil.toJsonString(listPersistedSpaces())
    }

    open fun clearSpace(spaceId: String) {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val matching = findSessionsBySpace(target)
        for (session in matching) {
            if (AgentSession.normalizeSpaceId(session.spaceId) == target) {
                clear(session.sessionId)
            } else {
                val remaining = session.chatMemory.messages().filter {
                    it.spaceId == null || AgentSession.normalizeSpaceId(it.spaceId) != target
                }
                session.chatMemory.replaceMessages(remaining)
                saveSession(session.sessionId, session)
            }
        }
        try {
            val ds = resolveDataService()
            ds?.delete("ChatSpace", mapOf("spaceId" to mapOf("value" to target)))
        } catch (e: Exception) {
            logger.warning("Failed to delete ChatSpace [$target]: ${e.message}")
        }
    }
}
