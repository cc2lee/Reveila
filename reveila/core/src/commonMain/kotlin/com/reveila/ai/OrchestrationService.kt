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
        val p = context?.getProxy(AgenticFabric.COMPONENT_NAME)
        this.agenticFabric = (p?.getInstance() as? AgenticFabric) ?: p as? AgenticFabric
    }

    @Throws(Exception::class)
    override fun onStop() {
        sessions.clear()
        agenticFabric = null
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

    open fun createSession(sessionId: String?, parentTraceId: String?): AgentSession {
        val id = if (sessionId.isNullOrBlank()) Uuid.randomUuid() else sessionId
        val session = AgentSession(id, parentTraceId, getMaxMessages())
        sessions[id] = session
        return session
    }

    open fun createSession(parentTraceId: String?): AgentSession {
        return createSession(null, parentTraceId)
    }

    open fun getSession(sessionId: String): AgentSession? = sessions[sessionId]

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
                        content = agenticFabric?.interpretAiResponse(JsonUtil.parseJsonStringToMap(cleaned)) ?: content
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
