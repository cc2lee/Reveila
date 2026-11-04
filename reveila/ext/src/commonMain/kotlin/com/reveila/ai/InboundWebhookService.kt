package com.reveila.ai

import com.reveila.safety.FlightRecorder
import com.reveila.safety.InvocationResult
import com.reveila.safety.ManagedInvocation
import com.reveila.system.Plugin
import com.reveila.system.SystemComponent
import com.reveila.util.Uuid

/**
 * Sovereign Service for receiving external task management webhooks.
 */
open class InboundWebhookService : SystemComponent() {

    private var bridge: ManagedInvocation? = null
    private var orchestrationService: OrchestrationService? = null
    private var flightRecorder: FlightRecorder? = null
    private var llmFactory: LlmProviderFactory? = null

    companion object {
        private const val TASK_ID = "task_id"
    }

    private fun <T> resolveService(name: String): T? {
        val p = context?.getProxy(name) ?: return null
        val target = p.getInstance()
        @Suppress("UNCHECKED_CAST")
        return target as? T
    }

    @Throws(Exception::class)
    override fun onStart() {
        this.bridge = resolveService("ManagedInvocation")
        this.orchestrationService = resolveService("OrchestrationService")
        this.flightRecorder = resolveService("FlightRecorder")
        this.llmFactory = resolveService("LlmProviderFactory")
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    open fun ingest(payload: Map<String, Any?>): InvocationResult {
        val source = (payload["trigger_source"] as? String) ?: "unknown"
        val perimeter = (payload["agency_perimeter"] as? String) ?: "default"

        val worker = llmFactory?.getActiveProvider()
        if (worker == null) {
            val msg = "System Error: No active LLM Provider found."
            logger.severe(msg)
            return InvocationResult.error(msg)
        }

        val request = LlmRequest.builder()
            .addMessage(ReveilaMessage.system("You are a Specialized Worker. Map the following context to a Reveila plugin intent. Return JSON."))
            .addMessage(ReveilaMessage.user((payload["context"] ?: "{}").toString()))
            .build()

        try {
            worker.invoke(request).content
        } catch (e: Exception) {
            // Ignore
        }

        val funcName = "webhook-agent-$source"
        val toolCall = ToolCall()
        toolCall.functionName = funcName

        @Suppress("UNCHECKED_CAST")
        val ctxMap = (payload["context"] as? Map<String, Any?>) ?: emptyMap()

        val toolArgs = mutableMapOf<String, Any?>()
        toolArgs["method"] = "external-ingestion"
        toolArgs.putAll(ctxMap)
        toolCall.arguments = toolArgs

        val tenantId = context?.properties?.getProperty("tenant-id", "default-tenant") ?: "default-tenant"

        val plugin = Plugin(
            Uuid.randomUuid(),
            funcName,
            tenantId,
            Uuid.randomUuid()
        )
        val session = orchestrationService?.createSession(plugin.traceId)
        session?.put("ingestion_source", source)
        session?.put("filo_task_id", payload[TASK_ID])

        flightRecorder?.recordStep(
            plugin,
            "filo_handshake_received",
            mapOf(
                TASK_ID to (payload[TASK_ID] ?: "N/A"),
                "perimeter" to perimeter
            )
        )

        val action = (ctxMap["required_action"] as? String) ?: "generic_task"

        var mappedIntent = action
        if ("extract_liabilities" == action) {
            mappedIntent = "doc_extraction.extract"
        }

        val args = mutableMapOf<String, Any?>()
        args[AgentSession.ID] = session?.sessionId
        args["traceId"] = plugin.traceId
        args[AgentSession.THOUGHT] = "Worker processing Filo task: ${payload[TASK_ID]}"
        args["arguments"] = toolCall.arguments

        val currentBridge = bridge
        return currentBridge?.invoke(toolCall, null, mappedIntent, args)
            ?: InvocationResult.error("Bridge is not initialized")
    }
}
