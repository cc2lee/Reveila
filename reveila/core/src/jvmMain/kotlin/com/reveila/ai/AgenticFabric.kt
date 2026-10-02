package com.reveila.ai

import com.reveila.error.ExceptionCollection
import com.reveila.error.SecurityException
import com.reveila.safety.InvocationResult
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecurityPerimeter
import com.reveila.system.Constants
import com.reveila.system.Plugin
import com.reveila.system.RolePrincipal
import com.reveila.system.SystemComponent
import com.reveila.system.SystemProxy
import com.reveila.util.json.JsonUtil
import org.json.JSONArray
import org.json.JSONObject
import java.security.Principal
import java.util.Objects
import java.util.UUID
import java.util.logging.Level

open class AgenticFabric : SystemComponent() {

    companion object {
        const val COMPONENT_NAME: String = "AgenticFabric"

        private const val STATUS = "status"
        private const val REASONING = "reasoning"
        private const val RESULT = "result"
        private const val CONFIDENCE_SCORE = "confidence-score"
        private const val TOOL_CALL = "tool-call"
    }

    private var bridge: ManagedInvocation? = null
    private var sessionManager: AgentSessionManager? = null
    private var orchestrationService: OrchestrationService? = null
    private var metadataRegistry: MetadataRegistry? = null
    private var llmFactory: LlmProviderFactory? = null
    private var toolProvider: DynamicToolProvider? = null
    var aiLoopLimit: Int = 5
    private var showReasoning: Boolean = false

    @Throws(Exception::class)
    override fun onStart() {
        showReasoning = context?.properties?.getProperty("ai.show.reasoning", "false")?.equals("true", ignoreCase = true) == true

        fun <T> resolveRequired(name: String): T {
            val p = context?.getProxy(name) ?: throw IllegalStateException("$name not found.")
            val target = p.getInstance()
            @Suppress("UNCHECKED_CAST")
            return target as? T ?: throw IllegalStateException("$name could not be cast.")
        }

        this.bridge = resolveRequired("ManagedInvocation")
        this.sessionManager = resolveRequired("AgentSessionManager")
        this.orchestrationService = resolveRequired("OrchestrationService")
        this.metadataRegistry = resolveRequired("MetadataRegistry")
        this.llmFactory = resolveRequired("LlmProviderFactory")

        try {
            val p = context?.getProxy("DynamicToolProvider")
            if (p != null) {
                this.toolProvider = p.getInstance() as? DynamicToolProvider
            }
        } catch (e: Exception) {
            // Optional for now
        }
    }

    /**
     * Exposes a simple entry point for UI clients to talk to the agent.
     *
     * @param userIntent   The user's prompt.
     * @param sessionId    Optional session ID to continue a conversation.
     * @param systemPrompt Optional initial system prompt (e.g., summary from previous session).
     * @return A JSON object containing the result and the session id.
     */
    open fun askAgent(userIntent: String, sessionId: String?, systemPrompt: String?): JSONObject {
        var actualSessionId = sessionId
        if (actualSessionId.isNullOrBlank()) {
            actualSessionId = UUID.randomUUID().toString()
        }

        var session = orchestrationService?.getSession(actualSessionId)
        if (session == null) {
            session = orchestrationService?.createSession(actualSessionId, actualSessionId)
            if (!systemPrompt.isNullOrBlank()) {
                session?.chatMemory?.add(ReveilaMessage.system("Context carried over from previous session: $systemPrompt"))
            }
        }

        val principal = RolePrincipal("ui-client")
        val subject = javax.security.auth.Subject()
        subject.principals.add(principal)

        val jsonResponse = if (session != null) {
            processIntent(session, subject, userIntent)
        } else {
            buildErrorResponse("OrchestrationService is not available")
        }
        val interpretation = interpretAiResponse(jsonResponse)
        val uiResponse = JSONObject()
        uiResponse.put("answer", interpretation)
        uiResponse.put("sessionId", session?.sessionId ?: actualSessionId)

        if (debug) {
            logger?.log(Level.INFO, "Final response to UI client: {0}", uiResponse)
        }

        return uiResponse
    }

    open fun interpretAiResponse(jsonResponse: JSONObject): String {
        var prettyAnswer = ""
        try {
            var status = jsonResponse.optString(STATUS, "")
            var reasoning = jsonResponse.optString(REASONING, "")
            var result = jsonResponse.optString(RESULT, "")

            if (reasoning == "null") reasoning = ""
            if (result == "null") result = ""

            if (status.equals(Constants.AI_STATUS_COMPLETED, ignoreCase = true) ||
                status.equals(Constants.AI_STATUS_INSUFFICIENT_CONTEXT, ignoreCase = true)
            ) {
                if (showReasoning && reasoning.isNotBlank()) {
                    prettyAnswer = if (result.isNotBlank()) {
                        "$result\n\nReasoning: $reasoning"
                    } else {
                        reasoning
                    }
                } else if (result.isNotBlank()) {
                    prettyAnswer = result
                } else if (reasoning.isNotBlank()) {
                    prettyAnswer = reasoning
                } else {
                    throw IllegalStateException("AI indicated completion but did not provide reasoning or result.")
                }
            } else if (status.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                prettyAnswer = "I need authorization to proceed."
                if (showReasoning && reasoning.isNotBlank()) {
                    prettyAnswer = "$prettyAnswer\n\nReasoning: $reasoning"
                }
            } else if (status.equals(Constants.AI_STATUS_FAILED, ignoreCase = true)) {
                if (reasoning.isNotBlank()) {
                    prettyAnswer = reasoning
                } else {
                    throw IllegalStateException("The AI indicated failure but did not provide reasoning.")
                }
            } else if (status.equals(Constants.AI_STATUS_TOOL_CALL, ignoreCase = true)) {
                prettyAnswer = "Performing background actions..."
                if (showReasoning && reasoning.isNotBlank()) {
                    prettyAnswer = "$prettyAnswer\n\nReasoning: $reasoning"
                }
            } else {
                throw IllegalStateException("Unexpected status from AI response: $status")
            }
        } catch (e: Exception) {
            val errMsg = if (e.message == null) "" else ": ${e.message}"
            prettyAnswer = "${e.javaClass.name}$errMsg\n\nOriginal AI response: $jsonResponse"
        }

        return prettyAnswer
    }

    /**
     * Summarizes a session history for carry-over.
     */
    open fun summarizeSession(sessionId: String): String {
        try {
            val session = orchestrationService?.getSession(sessionId) ?: return ""
            val worker = llmFactory?.getActiveProvider() ?: return ""

            val historyDump = session.chatMemory.messages().joinToString("\n") { m ->
                "${m.role().name}: ${m.content()}"
            }

            val request = LlmRequest.builder()
                .addMessage(
                    ReveilaMessage.system(
                        "Summarize the following chat history briefly for context preservation in a new session."
                    )
                )
                .addMessage(ReveilaMessage.user(historyDump))
                .build()

            return worker.invoke(request).content ?: ""
        } catch (e: Exception) {
            logger?.warning("Failed to summarize session: ${e.message}")
            return ""
        }
    }

    open fun processIntent(session: AgentSession, subject: javax.security.auth.Subject, intent: String): JSONObject {
        Objects.requireNonNull(session, "AgentSession cannot be null.")
        Objects.requireNonNull(subject, "Subject cannot be null.")
        Objects.requireNonNull(intent, "Intent cannot be null.")

        logger?.info("Processing intent: $intent")

        val principals = subject.principals
        if (principals == null || principals.isEmpty()) {
            return buildErrorResponse("ERROR: Access Denied. No principals found in subject.")
        }

        var authorized = false
        for (p in principals) {
            if (p is RolePrincipal) {
                val roleName = p.name
                if ("ui-client".equals(roleName, ignoreCase = true) ||
                    "admin".equals(roleName, ignoreCase = true)
                ) {
                    authorized = true
                    break
                }
            }
        }

        if (!authorized) {
            logger?.info("Access Denied. No authorized principals found in subject.")
            return buildErrorResponse("ERROR: Access Denied. No authorized principals found in subject.")
        }

        var intentBuffer = intent

        // Step 1: Execute initial LLM reasoning
        var response: String?
        try {
            response = askAi(session, intentBuffer)
        } catch (e: LlmException) {
            val errMsg = "ERROR: Failed to get response from LLM: ${e.message}"
            logger?.severe(errMsg)
            return buildErrorResponse(errMsg)
        }

        // Step 2: The "AI Loop"
        response = JsonUtil.clean(response)
        val validator = AiResponseValidator()

        for (loopCount in 0 until aiLoopLimit) {
            if (validator.getMessage(response) == null) {
                intentBuffer = "Invalid response from AI: $response"
                try {
                    response = askAi(session, intentBuffer)
                    continue
                } catch (e: LlmException) {
                    val errMsg = "ERROR: Failed to get response from LLM: ${e.message}"
                    logger?.severe(errMsg)
                    return buildErrorResponse(errMsg)
                }
            }

            try {
                // Step 3: Handle terminal statuses defined in Prompt
                val jsonResponse = JSONObject(response)
                val status = jsonResponse.optString(STATUS, "")
                val reasoning = jsonResponse.optString(REASONING, "")

                if (status.equals(Constants.AI_STATUS_COMPLETED, ignoreCase = true)) {
                    recordAuditLog("TASK_COMPLETED", reasoning)
                    return jsonResponse
                } else if (status.equals(Constants.AI_STATUS_INSUFFICIENT_CONTEXT, ignoreCase = true)) {
                    recordAuditLog("INSUFFICIENT_CONTEXT", reasoning)
                    return jsonResponse
                } else if (status.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                    recordAuditLog("ESCALATED", reasoning)
                    return jsonResponse
                } else if (status.equals(Constants.AI_STATUS_FAILED, ignoreCase = true)) {
                    recordAuditLog("TASK_FAILED", reasoning)
                    return jsonResponse
                } else if (status.equals(Constants.AI_STATUS_TOOL_CALL, ignoreCase = true)) {
                    try {
                        val toolCallObj = jsonResponse.opt(TOOL_CALL)
                        if (toolCallObj == null) {
                            intentBuffer = "The AI indicated a tool call is needed but did not specify the tool or arguments. Reasoning provided: $reasoning | Please clarify the tool to call and its arguments."
                            recordAuditLog("TOOL_CALL_FAILED", "Missing tool/arguments.")
                        } else {
                            val toolResults = StringBuilder()
                            val currentResponse = response ?: ""
                            if (toolCallObj is JSONArray) {
                                for (i in 0 until toolCallObj.length()) {
                                    val singleToolCall = toolCallObj.getJSONObject(i)
                                    val result = handleToolCall(session, singleToolCall, currentResponse)
                                    toolResults.append("\n- Tool: ").append(singleToolCall.optString("method"))
                                        .append("\n  Result: ").append(result)
                                }
                            } else if (toolCallObj is JSONObject) {
                                val result = handleToolCall(session, toolCallObj, currentResponse)
                                toolResults.append(result)
                            }

                            intentBuffer = "The AI has requested to call tools with the following details: $toolCallObj | Reasoning provided: $reasoning | Here are the results from the tool execution: $toolResults | Please analyze this information and provide the next step or final answer."
                            recordAuditLog("TOOL_CALLED", "Called tools: $toolCallObj")
                        }
                    } catch (e: Exception) {
                        intentBuffer = "The AI requested a tool call, but the call could not be completed: ${e.message} | Reasoning provided: $reasoning | Please clarify the tool to call and its arguments."
                    }
                } else {
                    intentBuffer = "The AI provided an unexpected status: $status | Reasoning provided: $reasoning | Please analyze this information and provide the next step or final answer."
                }

                try {
                    response = askAi(session, intentBuffer)
                } catch (e: LlmException) {
                    val errMsg = "ERROR: Failed to get response from LLM: ${e.message}"
                    logger?.severe(errMsg)
                    return buildErrorResponse(errMsg)
                }
            } catch (t: Throwable) {
                val tMsg = if (!t.message.isNullOrBlank()) t.message else t.toString()
                intentBuffer = "Exception occurred while processing response: $tMsg\nPlease analyze this information and provide the next step or final answer.\nOriginal response: $response"
                recordAuditLog("EXCEPTION", t.message)
                try {
                    response = askAi(session, intentBuffer)
                } catch (e: LlmException) {
                    val errMsg = "ERROR: Failed to get response from LLM: ${e.message}"
                    logger?.severe(errMsg)
                    return buildErrorResponse(errMsg)
                }
            }
        }

        return buildErrorResponse(
            "AI_LOOP_LIMIT_REACHED: The task could not be completed within the execution limit of $aiLoopLimit iteration(s)."
        )
    }

    /**
     * Allows a Manager agent to delegate tasks to Worker agents.
     * Implements the Agent-to-Agent (A2A) Bridge via recursive invocation.
     *
     * @param parent        The calling agent principal.
     * @param targetIntent  The intent for the worker agent.
     * @param taskArguments The task-specific arguments.
     * @return The result of the delegated task.
     */
    open fun delegate(parent: Plugin, targetIntent: String, taskArguments: Map<String, Any?>): Any? {
        val childPluginId = "plugin-" + UUID.randomUUID().toString().substring(0, 4)
        val child = parent.deriveChild(childPluginId)

        val parentContext = sessionManager?.getContext(parent.traceId) ?: emptyMap()
        sessionManager?.saveContext(child.traceId, parentContext)

        var activePerimeter: SecurityPerimeter? = null
        val manifest = metadataRegistry?.getManifest(childPluginId)
        if (manifest != null) {
            activePerimeter = manifest.defaultPerimeter()
        }

        val delegateCall = ToolCall()
        delegateCall.functionName = childPluginId
        delegateCall.arguments = taskArguments

        val metaInfo = HashMap<String, Any?>()
        metaInfo["traceId"] = child.traceId
        metaInfo[AgentSession.ID] = parent.sessionId.toString()

        val result = bridge?.invoke(delegateCall, activePerimeter, targetIntent, metaInfo)
            ?: return "DELEGATION_FAILED: Bridge not available"

        if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            return "DELEGATION_PAUSED: ${result.message()} Approval required at: ${result.callbackUrl()}"
        }

        return result.data()
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    private fun buildErrorResponse(errorMessage: String): JSONObject {
        val json = JSONObject()
        json.put(STATUS, Constants.AI_STATUS_FAILED)
        json.put(REASONING, errorMessage)
        json.put(RESULT, "")
        json.put(CONFIDENCE_SCORE, 0.0)
        json.put(TOOL_CALL, JSONArray())
        return json
    }

    private fun recordAuditLog(action: String, details: String?) {
        try {
            val p = context?.getProxy("DataService")
            if (p != null) {
                val log = mutableMapOf<String, Any?>()
                log["action"] = action
                log["details"] = details
                log["timestamp"] = System.currentTimeMillis()
                p.invoke("save", arrayOf<Any?>("AuditLog", log))
            }
        } catch (e: Exception) {
            logger?.warning("Failed to write AuditLog: ${e.message}")
        }
    }

    @Throws(Exception::class)
    private fun handleToolCall(session: AgentSession, toolCallObj: JSONObject, intent: String): String {
        val bridgeArgs = HashMap<String, Any?>()
        bridgeArgs[AgentSession.ID] = session.sessionId
        bridgeArgs[AgentSession.THOUGHT] = toolCallObj
        bridgeArgs["traceId"] = session.parentTraceId ?: UUID.randomUUID().toString()

        var pluginId = toolCallObj.optString("plugin", "")
        if (pluginId.isEmpty()) {
            pluginId = toolCallObj.optString("name", "")
        }

        val toolCall = ToolCall()
        toolCall.functionName = pluginId

        val argsJson = toolCallObj.optJSONObject("arguments")
        if (argsJson != null) {
            val argsMap = argsJson.toMap()
            if (toolCallObj.has("method") && !argsMap.containsKey("method")) {
                argsMap["method"] = toolCallObj.optString("method")
            }
            toolCall.arguments = argsMap
        } else {
            toolCall.arguments = HashMap<String, Any?>()
        }

        bridgeArgs["arguments"] = toolCall.arguments

        var activePerimeter: SecurityPerimeter? = null
        val manifest = metadataRegistry?.getManifest(pluginId)
        if (manifest != null) {
            activePerimeter = manifest.defaultPerimeter()
        }

        val result = bridge?.invoke(toolCall, activePerimeter, intent, bridgeArgs)
            ?: throw RuntimeException("Bridge is not available")

        if (result.status() == InvocationResult.Status.SUCCESS) {
            val toolResult = if (result.data() != null) result.data().toString() else "Action completed successfully."
            session.chatMemory.add(ReveilaMessage.assistant("Reasoning: Action required. Initiating tool execution."))
            session.chatMemory.add(ReveilaMessage.tool("The tool has returned: $toolResult"))
            return toolResult
        } else if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            session.chatMemory.add(ReveilaMessage.assistant("Task requires approval."))
            session.put("pendingApproval", result.callbackUrl())
            val dataStr = if (result.data() != null) result.data().toString() else ""
            throw SecurityException("APPROVAL_REQUIRED|${result.message()}|$dataStr")
        } else {
            throw RuntimeException("Tool execution failed: ${result.message()}")
        }
    }

    @Throws(LlmException::class)
    private fun askAi(session: AgentSession, userPrompt: String): String {
        val worker = llmFactory?.getActiveProvider()
        if (worker == null) {
            val msg = "System Error: No active LLM Provider found."
            logger?.severe(msg)
            return msg
        }

        val tools: List<LlmTool>
        val provider = toolProvider
        if (provider != null) {
            tools = provider.provideTools(userPrompt)
        } else {
            val mcpData = metadataRegistry?.exportToMCP()
            @Suppress("UNCHECKED_CAST")
            val mcpTools = mcpData?.get("tools") as? List<Map<String, Any?>>
            val toolList = ArrayList<LlmTool>()
            if (mcpTools != null) {
                for (toolMap in mcpTools) {
                    val tool = LlmTool()
                    tool.name = toolMap["name"] as? String
                    tool.description = toolMap["description"] as? String
                    val inputSchema = toolMap["inputSchema"]
                    if (inputSchema is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        tool.parameterSchema = inputSchema as Map<String, Any?>
                    }
                    toolList.add(tool)
                }
            }
            tools = toolList
        }

        val systemInstructions = Prompt.getSystemPrompt(
            "Reveila AI Agent",
            "Available Tools: " +
                tools.joinToString(", ") { it.name ?: "" } +
                "\n\nRelated Documents: " + searchKnowledgeVault(userPrompt),
            ""
        )

        if ("cost".equals(orchestrationService?.optimizationPriority, ignoreCase = true)) {
            val historySize = session.chatMemory.messages().size
            if (historySize > 10) {
                val historyDump = session.chatMemory.messages().toString()
                val summaryRequest = LlmRequest.builder()
                    .addMessage(ReveilaMessage.system("System"))
                    .addMessage(ReveilaMessage.user("Summarize the following chat history for context preservation: $historyDump"))
                    .build()

                val summary = try {
                    worker.invoke(summaryRequest).content
                } catch (e: Exception) {
                    "ERROR summarising context: ${e.message}"
                }

                session.chatMemory.clear()
                session.chatMemory.add(ReveilaMessage.system("Summary of previous conversation: $summary"))
            }
        }

        session.chatMemory.add(ReveilaMessage.user(userPrompt))

        val requestBuilder = LlmRequest.builder().tools(tools)
        requestBuilder.addMessage(ReveilaMessage.system(systemInstructions))

        if (debug) {
            logger?.info("System Prompt used for request: $systemInstructions")
        }

        for (msg in session.chatMemory.messages()) {
            requestBuilder.addMessage(msg)
        }

        val request = requestBuilder.build()
        var response: String? = null
        try {
            if (debug) {
                logger?.info("Invoking LLM provider [${worker.getName()}] with prompt: $request")
            }
            response = worker.invoke(request).content
            if (debug) {
                logger?.info("Received response from LLM provider [${worker.getName()}]: $response")
            }
        } catch (e: Exception) {
            val ec = ExceptionCollection()
            ec.addException(e)
            var t = e.cause
            while (t != null) {
                ec.addException(t)
                t = t.cause
            }
            throw LlmException("LLM Invocation Failure, caused by: $ec")
        } finally {
            try {
                session.chatMemory.add(ReveilaMessage.assistant(response ?: "No response received from LLM."))
            } catch (e: Exception) {
                logger?.severe("Error recording response in chat memory: ${e.message}")
            }
        }

        return response ?: "No response received from LLM."
    }

    private fun searchKnowledgeVault(query: String): String {
        try {
            val p = context?.getProxy("KnowledgeVault")
            if (p != null) {
                val result = p.invoke("search", arrayOf<Any?>(query, 3))
                val snippets = result?.toString() ?: "No relevant internal documents found."
                if (debug) {
                    logger?.info("Knowledge Vault search result for [$query]: $snippets")
                }
                return snippets
            }
        } catch (e: Exception) {
            logger?.warning("Knowledge Vault search failed: ${e.message}")
        }
        return "No relevant internal documents found."
    }
}
