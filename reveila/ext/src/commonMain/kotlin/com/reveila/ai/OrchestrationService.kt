package com.reveila.ai

import com.reveila.system.SystemComponent
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil

/**
 * OrchestrationService manages AgentSessions and coordinates multi-agent
 * workflows. Optimized to handle context windows based on system configuration.
 */
open class OrchestrationService : SystemComponent() {

    private val sessions: MutableMap<String, AgentSession> = mutableMapOf()
    var optimizationPriority: String = "cost"
        private set
    private var agenticFabric: AgenticFabric? = null

    @Throws(Exception::class)
    override fun onStart() {
        this.optimizationPriority = context?.properties?.getProperty("ai.optimization.priority", "cost") ?: "cost"
    }

    private fun getAgenticFabric(): AgenticFabric? {
        if (agenticFabric == null) {
            val p = context?.getProxy(AgenticFabric.COMPONENT_NAME)
            agenticFabric = (p?.getInstance() as? AgenticFabric) ?: p as? AgenticFabric
        }
        return agenticFabric
    }

    @Throws(Exception::class)
    override fun onStop() {
        sessions.clear()
        agenticFabric = null
    }

    private var suspended: Boolean = false

    open fun isSuspended(): Boolean = suspended

    open fun toggleSuspend(value: Boolean): Boolean {
        this.suspended = value
        try {
            val runtime = context?.getProxy("GuardedRuntime")
            if (value) {
                runtime?.invoke("suspend", null)
            } else {
                runtime?.invoke("resume", null)
            }
        } catch (e: Exception) {
            logger.warning("Could not toggle suspend on GuardedRuntime: ${e.message}")
        }
        logger.info("OrchestrationService suspended state toggled to: $suspended")
        return suspended
    }

    open fun triggerVaultScan(): Boolean {
        logger.info("Triggered vault scanner across local secure stores.")
        return true
    }

    open fun resetApplication(): Boolean {
        sessions.clear()
        logger.info("OrchestrationService sessions reset.")
        return true
    }

    open fun getMaxMessages(): Int {
        val max = context?.properties?.getProperty("ai.session.maxMessages")
        if (!max.isNullOrBlank()) {
            try {
                return max.toInt()
            } catch (e: Exception) {
            }
        }
        return if ("cost".equals(optimizationPriority, ignoreCase = true)) 10 else 50
    }

    private fun resolveSessionManager(): AgentSessionManager? {
        val proxy = context?.getProxy("AgentSessionManager") ?: return null
        return (proxy.getInstance() as? AgentSessionManager) ?: (proxy as? AgentSessionManager)
    }

    @kotlin.jvm.JvmOverloads
    open fun createSession(
        sessionId: String?,
        parentTraceId: String?,
        spaceId: String? = null,
        spaceName: String? = null
    ): AgentSession {
        val resolvedSpaceId = AgentSession.normalizeSpaceId(
            spaceId?.takeIf { it.isNotBlank() }
                ?: sessionId?.takeIf { it.isNotBlank() && !AgentSession.looksLikeUuid(it) }
                ?: AgentSession.DEFAULT_SPACE_ID
        )
        val resolvedSpaceName = AgentSession.displayNameForSpace(
            resolvedSpaceId,
            spaceName?.takeIf { it.isNotBlank() }
                ?: sessionId?.takeIf { it.isNotBlank() && !AgentSession.looksLikeUuid(it) }
        )
        val id = if (sessionId.isNullOrBlank()) {
            if (!spaceId.isNullOrBlank()) AgentSession.sessionIdForSpace(resolvedSpaceId) else Uuid.randomUuid()
        } else {
            sessionId
        }
        val session = AgentSession(id, parentTraceId, getMaxMessages(), resolvedSpaceId, resolvedSpaceName)
        sessions[id] = session
        return session
    }

    open fun createSession(parentTraceId: String?): AgentSession {
        return createSession(null, parentTraceId, null, null)
    }

    open fun getSession(sessionId: String): AgentSession? {
        sessions[sessionId]?.let { return it }
        val persisted = resolveSessionManager()?.getSession(sessionId)
        if (persisted != null) {
            sessions[sessionId] = persisted
            return persisted
        }
        return null
    }

    @kotlin.jvm.JvmOverloads
    open fun getOrCreateSpaceSession(
        spaceId: String,
        spaceName: String? = null,
        preferredSessionId: String? = null
    ): AgentSession {
        val normalizedSpaceId = AgentSession.normalizeSpaceId(spaceId)
        val resolvedSpaceName = AgentSession.displayNameForSpace(normalizedSpaceId, spaceName ?: spaceId)
        val candidateId = preferredSessionId?.trim()?.takeIf { it.isNotEmpty() }
            ?: AgentSession.sessionIdForSpace(normalizedSpaceId)

        getSession(candidateId)?.let { existing ->
            existing.assignSpace(normalizedSpaceId, resolvedSpaceName)
            return existing
        }

        resolveSessionManager()?.findLatestSessionForSpace(normalizedSpaceId)?.let { persistedForSpace ->
            persistedForSpace.assignSpace(normalizedSpaceId, resolvedSpaceName)
            sessions[persistedForSpace.sessionId] = persistedForSpace
            return persistedForSpace
        }

        val created = createSession(candidateId, null, normalizedSpaceId, resolvedSpaceName)
        resolveSessionManager()?.saveSession(created.sessionId, created)
        return created
    }

    open fun getSpaceSessions(spaceId: String): List<AgentSession> {
        val sm = resolveSessionManager()
        if (sm != null) {
            return sm.findSessionsBySpace(spaceId)
        }
        val target = AgentSession.normalizeSpaceId(spaceId)
        return sessions.values.filter { AgentSession.normalizeSpaceId(it.spaceId) == target }
    }

    @kotlin.jvm.JvmOverloads
    open fun getSpaceHistory(spaceId: String, limit: Int = 100): List<Map<String, Any?>> {
        val sm = resolveSessionManager()
        if (sm != null) {
            return sm.getSpaceHistoryMaps(spaceId, limit)
        }
        val target = AgentSession.normalizeSpaceId(spaceId)
        return sessions.values
            .filter { AgentSession.normalizeSpaceId(it.spaceId) == target }
            .flatMap { it.messagesForSpace(target) }
            .takeLast(limit)
            .map { msg ->
                mapOf(
                    "role" to msg.role.name,
                    "content" to msg.content,
                    "spaceId" to (msg.spaceId ?: target),
                    "timestamp" to msg.timestamp
                )
            }
    }

    @kotlin.jvm.JvmOverloads
    open fun getSpaceHistoryJson(spaceId: String, limit: Int = 100): String {
        val sm = resolveSessionManager()
        return if (sm != null) {
            sm.getSpaceHistoryJson(spaceId, limit)
        } else {
            JsonUtil.toJsonString(getSpaceHistory(spaceId, limit))
        }
    }

    open fun getSpaceHistoryJson(spaceId: String, limitText: String): String {
        return getSpaceHistoryJson(spaceId, limitText.trim().toIntOrNull() ?: 100)
    }

    open fun listSpacesJson(): String {
        val sm = resolveSessionManager()
        return sm?.listSpacesJson() ?: JsonUtil.toJsonString(emptyList<Map<String, Any?>>())
    }

    open fun clearSpace(spaceId: String) {
        val target = AgentSession.normalizeSpaceId(spaceId)
        val toRemove = sessions.values
            .filter { AgentSession.normalizeSpaceId(it.spaceId) == target }
            .map { it.sessionId }
        toRemove.forEach { sessions.remove(it) }
        resolveSessionManager()?.clearSpace(target)
    }

    open fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    open fun getActiveSessions(): List<Map<String, Any?>> {
        return sessions.values
            .filter { it.chatMemory.messages().isNotEmpty() }
            .map { session ->
                val map = mutableMapOf<String, Any?>()
                map["id"] = session.sessionId

                var title = "Session"
                for (msg in session.chatMemory.messages()) {
                    if (LlmRole.USER == msg.role()) {
                        title = msg.content()
                        if (title.length > 30) {
                            title = title.substring(0, 30) + "..."
                        }
                        break
                    }
                }
                map["title"] = title
                map["messageCount"] = session.chatMemory.messages().size
                map
            }
    }

    open fun getSessionHistory(sessionId: String): List<Map<String, String>> {
        val session = getSession(sessionId) ?: return emptyList()

        return session.chatMemory.messages().map { msg ->
            val map = mutableMapOf<String, String>()
            map["role"] = msg.role().name
            var content = msg.content()
            if (LlmRole.ASSISTANT == msg.role()) {
                try {
                    val cleaned = JsonUtil.clean(content)
                    if (cleaned != null && cleaned.startsWith("{")) {
                        content = getAgenticFabric()?.interpretAiResponse(JsonUtil.parseJsonStringToMap(cleaned)) ?: content
                    }
                } catch (e: Exception) {
                    // Keep original content
                }
            }
            map["content"] = content
            map
        }
    }
}
