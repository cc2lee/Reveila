package com.reveila.ai

import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy
import com.reveila.util.json.JsonUtil
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * OrchestrationService manages AgentSessions and coordinates multi-agent
 * workflows. Optimized to handle context windows based on system configuration.
 *
 * @author CL
 */
open class OrchestrationService : SystemComponent() {

    private val sessions: MutableMap<String, AgentSession> = ConcurrentHashMap()
    var optimizationPriority: String = "cost"
        private set
    private var agenticFabric: AgenticFabric? = null

    @Throws(Exception::class)
    override fun onStart() {
        this.optimizationPriority = context?.properties?.getProperty("ai.optimization.priority", "cost") ?: "cost"
        val p = context?.getProxy(AgenticFabric.COMPONENT_NAME)
        this.agenticFabric = if (p is SystemProxy) p.getInstance() as? AgenticFabric else p as? AgenticFabric
    }

    @Throws(Exception::class)
    override fun onStop() {
        sessions.clear()
        agenticFabric = null
    }

    /**
     * Returns the maximum number of messages allowed in a single session.
     */
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

    /**
     * Creates a new AgentSession with settings derived from system properties.
     *
     * @param sessionId     Optional preferred session ID. If null, a random UUID will be generated.
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    open fun createSession(sessionId: String?, parentTraceId: String?): AgentSession {
        val id = if (sessionId.isNullOrBlank()) UUID.randomUUID().toString() else sessionId
        val session = AgentSession(id, parentTraceId, getMaxMessages())
        sessions[id] = session
        return session
    }

    /**
     * Creates a new AgentSession with a random UUID.
     *
     * @param parentTraceId The trace_id of the parent task.
     * @return The newly created AgentSession.
     */
    open fun createSession(parentTraceId: String?): AgentSession {
        return createSession(null, parentTraceId)
    }

    /**
     * Retrieves an existing AgentSession.
     *
     * @param sessionId The ID of the session to retrieve.
     * @return The AgentSession, or null if not found.
     */
    open fun getSession(sessionId: String): AgentSession? = sessions[sessionId]

    /**
     * Closes and removes an AgentSession.
     *
     * @param sessionId The ID of the session to close.
     */
    open fun closeSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    /**
     * Returns a list of active sessions for the dashboard.
     */
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

    /**
     * Returns the chat history for a specific session.
     */
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
                        content = agenticFabric?.interpretAiResponse(JSONObject(cleaned)) ?: content
                    }
                } catch (e: Exception) {
                    // Not JSON or parse failed, keep original content
                }
            }
            map["content"] = content
            map
        }
    }
}
