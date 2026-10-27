package com.reveila.ai

import com.reveila.error.ExceptionCollection
import com.reveila.error.SecurityException
import com.reveila.safety.FlightRecorder
import com.reveila.safety.GovernancePolicy
import com.reveila.safety.InvocationResult
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecurityPerimeter
import com.reveila.service.DataService
import com.reveila.system.Constants
import com.reveila.system.Plugin
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.system.SystemComponent
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil

/**
 * Agentic AI Runtime Fabric.
 * Implements a cross-platform, sovereign agentic execution engine featuring:
 * 1. Robust multi-turn AI reasoning, tool execution, feedback, and sliding context loop.
 * 2. Real-time guardrail monitoring (prompt inspection, output DLP, Dual-Model consensus).
 * 3. Neutral data access layer integration via DataService / Repository.
 * 4. Multi-agent delegation (Manager-Worker) with global immutable trace lineage.
 */
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
    private var intentValidator: AiIntentValidator? = null
    private var flightRecorder: FlightRecorder? = null
    private var dataService: DataService? = null
    private var governancePolicy: GovernancePolicy = GovernancePolicy.defaultPolicy()

    var aiLoopLimit: Int = 5
    private var showReasoning: Boolean = false

    @Throws(Exception::class)
    override fun onStart() {
        showReasoning = context?.properties?.getProperty("ai.show.reasoning", "false")?.equals("true", ignoreCase = true) == true

        fun <T> resolveRequired(name: String): T {
            val p = context?.getProxy(name) ?: throw IllegalStateException("$name not found.")
            val target = p.getInstance()
            @Suppress("UNCHECKED_CAST")
            return (target as? T) ?: throw IllegalStateException("$name could not be cast.")
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
            // Optional
        }

        try {
            val ivProxy = context?.getProxy("IntentValidator")
            this.intentValidator = (ivProxy?.getInstance() as? AiIntentValidator) ?: ivProxy as? AiIntentValidator
        } catch (e: Exception) {
            // Optional
        }

        try {
            val frProxy = context?.getProxy("FlightRecorder")
            this.flightRecorder = (frProxy?.getInstance() as? FlightRecorder) ?: frProxy as? FlightRecorder
        } catch (e: Exception) {
            // Optional
        }

        try {
            val dsProxy = context?.getProxy("DataService")
            this.dataService = (dsProxy?.getInstance() as? DataService) ?: dsProxy as? DataService
        } catch (e: Exception) {
            // Optional
        }

        // Load Governance Policy from governance-policy.json
        val fs = PlatformFileSystem()
        val home = context?.properties?.getProperty("system.home") ?: "system-home/standard"
        val policyPath = fs.resolve(fs.resolve(home, "configs"), "governance-policy.json")
        this.governancePolicy = if (fs.exists(policyPath)) {
            GovernancePolicy.load(fs, policyPath)
        } else {
            GovernancePolicy.defaultPolicy()
        }

        logger.info("AgenticFabric started with policy [${governancePolicy.policyName}] (aiLoopLimit=$aiLoopLimit).")
    }

    open fun askAgent(userIntent: String, sessionId: String?, systemPrompt: String?): Map<String, Any?> {
        var actualSessionId = sessionId
        if (actualSessionId.isNullOrBlank()) {
            actualSessionId = Uuid.randomUuid()
        }

        var session = orchestrationService?.getSession(actualSessionId)
        if (session == null) {
            session = orchestrationService?.createSession(actualSessionId, actualSessionId)
            if (!systemPrompt.isNullOrBlank()) {
                session?.chatMemory?.add(ReveilaMessage.system("Context carried over from previous session: $systemPrompt"))
            }
        }

        val principal = RolePrincipal("ui-client")
        val subject = Subject()
        subject.principals.add(principal)

        val jsonResponse = if (session != null) {
            processIntent(session, subject, userIntent)
        } else {
            buildErrorResponse("OrchestrationService is not available")
        }
        val interpretation = interpretAiResponse(jsonResponse)
        val uiResponse = mutableMapOf<String, Any?>()
        uiResponse["answer"] = interpretation
        uiResponse["sessionId"] = session?.sessionId ?: actualSessionId

        if (isDebug) {
            logger.info("Final response to UI client: $uiResponse")
        }

        return uiResponse
    }

    open fun interpretAiResponse(jsonResponse: Map<String, Any?>): String {
        var prettyAnswer = ""
        try {
            var status = jsonResponse[STATUS]?.toString() ?: ""
            var reasoning = jsonResponse[REASONING]?.toString() ?: ""
            var result = jsonResponse[RESULT]?.toString() ?: ""

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
            prettyAnswer = "${e::class.simpleName ?: "Exception"}$errMsg\n\nOriginal AI response: $jsonResponse"
        }

        return prettyAnswer
    }

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
            logger.warning("Failed to summarize session: ${e.message}")
            return ""
        }
    }

    /**
     * Executes the comprehensive Agentic AI loop for a given intent and session.
     */
    open fun processIntent(session: AgentSession, subject: Subject, intent: String): Map<String, Any?> {
        val traceId = session.parentTraceId ?: Uuid.randomUuid()
        TraceContextHolder.setTraceId(traceId)
        val dummyPlugin = Plugin(session.sessionId, "AgenticFabric", "default-tenant", traceId)

        logger.info("[Fabric] Processing intent (trace: $traceId): $intent")

        // 1. Subject Principal Authorization
        val principals = subject.principals
        if (principals.isEmpty()) {
            return buildErrorResponse("ERROR: Access Denied. No principals found in subject.")
        }

        var authorized = false
        val allowedRoles = listOf("ui-client", "admin", "system", "autonomous-agent", "manager-agent", "ciso", "developer")
        for (p in principals) {
            if (p is RolePrincipal) {
                if (allowedRoles.any { it.equals(p.name, ignoreCase = true) }) {
                    authorized = true
                    break
                }
            }
        }

        if (!authorized) {
            logger.warning("[Fabric] Access Denied. No authorized role found for principals: $principals")
            recordAuditLog("ACCESS_DENIED", "Unauthorized role for intent: $intent")
            return buildErrorResponse("ERROR: Access Denied. No authorized principals found in subject.")
        }

        // 2. Real-time Prompt Inspection & Guardrail Validation
        val validator = intentValidator
        val promptEvaluation = validator?.evaluatePrompt(intent) ?: governancePolicy.evaluatePrompt(intent)
        flightRecorder?.recordForensicMetadata(
            dummyPlugin,
            mapOf(
                "action" to "PROMPT_GUARDRAIL_INSPECTION",
                "riskScore" to promptEvaluation.riskScore,
                "isBlocked" to promptEvaluation.isBlocked,
                "reasoning" to promptEvaluation.reasoning
            )
        )

        if (promptEvaluation.isBlocked) {
            val breachMsg = "SECURITY_BREACH: Prompt rejected by governance policy - ${promptEvaluation.reasoning}"
            logger.severe("[Fabric] $breachMsg")
            recordAuditLog("SECURITY_BREACH_PROMPT_BLOCKED", breachMsg)
            flightRecorder?.recordStep(dummyPlugin, "prompt_blocked", mapOf("reason" to promptEvaluation.reasoning, "risk" to promptEvaluation.riskScore))
            return buildErrorResponse(breachMsg)
        }

        // If prompt triggers HITL approval threshold immediately
        if (promptEvaluation.requiresHitlApproval) {
            val hitlMsg = "Human approval required for high-risk intent (risk score: ${promptEvaluation.riskScore}): ${promptEvaluation.reasoning}"
            logger.info("[Fabric] $hitlMsg")
            recordAuditLog("HITL_TRIGGERED_PROMPT", hitlMsg)
            val escalateResponse = mutableMapOf<String, Any?>()
            escalateResponse[STATUS] = Constants.AI_STATUS_ESCALATE
            escalateResponse[REASONING] = hitlMsg
            escalateResponse[RESULT] = "Pending human-in-the-loop authorization."
            escalateResponse[CONFIDENCE_SCORE] = promptEvaluation.riskScore
            escalateResponse[TOOL_CALL] = emptyList<Any?>()
            return escalateResponse
        }

        var intentBuffer = intent
        val responseValidator = AiResponseValidator(governancePolicy)

        // 3. The Robust Agentic AI Loop
        for (loopCount in 0 until aiLoopLimit) {
            logger.info("[Fabric] Starting Agentic Loop iteration [${loopCount + 1}/$aiLoopLimit]")

            // Dynamic Context Window & Summarization Management
            enforceSlidingWindow(session)

            var rawResponse: String?
            try {
                rawResponse = askAi(session, intentBuffer)
            } catch (e: LlmException) {
                val errMsg = "ERROR: Failed to get response from LLM: ${e.message}"
                logger.severe(errMsg)
                recordAuditLog("LLM_INVOCATION_FAILURE", errMsg)
                return buildErrorResponse(errMsg)
            }

            // Real-time Response Guardrail & Output DLP Examination
            val validationResult = responseValidator.validateAndSanitize(rawResponse)
            if (validationResult.isBlocked) {
                val breachMsg = "SECURITY_BREACH: LLM output blocked by DLP policy: ${validationResult.reasoning}"
                logger.severe("[Fabric] $breachMsg")
                recordAuditLog("SECURITY_BREACH_RESPONSE_BLOCKED", breachMsg)
                flightRecorder?.recordStep(dummyPlugin, "response_dlp_blocked", mapOf("reason" to validationResult.reasoning))
                return buildErrorResponse(breachMsg)
            }

            if (validationResult.isRepeating) {
                logger.warning("[Fabric] AI entered repetitive hallucination loop. Feeding correction prompt back to agent.")
                intentBuffer = "System Guardrail Warning: You are repeating previous outputs without making progress. Please rethink your reasoning and provide either a valid tool call or final completed answer."
                continue
            }

            val sanitizedResponse = JsonUtil.clean(validationResult.sanitizedContent)

            // Parse response JSON
            val jsonResponse: Map<String, Any?> = try {
                JsonUtil.parseJsonStringToMap(sanitizedResponse)
            } catch (e: Exception) {
                if (!sanitizedResponse.isNullOrBlank()) {
                    mapOf(
                        STATUS to Constants.AI_STATUS_COMPLETED,
                        REASONING to "Generated by sovereign neural model",
                        RESULT to sanitizedResponse,
                        CONFIDENCE_SCORE to 0.95
                    )
                } else {
                    logger.warning("[Fabric] Invalid JSON from AI: $sanitizedResponse")
                    intentBuffer = "Invalid JSON response from AI. Please format your reply strictly as a JSON document containing 'status', 'reasoning', 'result', and 'confidence-score' keys."
                    continue
                }
            }

            val status = jsonResponse[STATUS]?.toString() ?: ""
            val reasoning = jsonResponse[REASONING]?.toString() ?: ""
            flightRecorder?.recordReasoning(dummyPlugin, reasoning)

            // Handle terminal loop statuses
            if (status.equals(Constants.AI_STATUS_COMPLETED, ignoreCase = true)) {
                recordAuditLog("TASK_COMPLETED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "task_completed", jsonResponse.filterValues { it != null }.mapValues { it.value as Any })
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_INSUFFICIENT_CONTEXT, ignoreCase = true)) {
                recordAuditLog("INSUFFICIENT_CONTEXT", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "insufficient_context", mapOf("reason" to reasoning))
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                recordAuditLog("ESCALATED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "escalated_hitl", mapOf("reason" to reasoning))
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_FAILED, ignoreCase = true)) {
                recordAuditLog("TASK_FAILED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "task_failed", mapOf("reason" to reasoning))
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_TOOL_CALL, ignoreCase = true)) {
                val toolCallObj = jsonResponse[TOOL_CALL]
                if (toolCallObj == null) {
                    intentBuffer = "The AI indicated a tool call is needed but did not specify the tool or arguments. Reasoning provided: $reasoning | Please clarify the tool to call and its arguments."
                    recordAuditLog("TOOL_CALL_FAILED", "Missing tool/arguments.")
                } else {
                    val toolResults = StringBuilder()
                    val currentResponse = sanitizedResponse ?: ""
                    var approvalRequired = false
                    var approvalMessage = ""

                    try {
                        if (toolCallObj is List<*>) {
                            for (item in toolCallObj) {
                                if (item is Map<*, *>) {
                                    @Suppress("UNCHECKED_CAST")
                                    val singleToolCall = item as Map<String, Any?>
                                    val result = handleGuardedToolCall(session, singleToolCall, currentResponse, traceId)
                                    toolResults.append("\n- Tool: ").append(singleToolCall["method"]?.toString() ?: "")
                                        .append("\n  Result: ").append(result)
                                }
                            }
                        } else if (toolCallObj is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val singleToolCall = toolCallObj as Map<String, Any?>
                            val result = handleGuardedToolCall(session, singleToolCall, currentResponse, traceId)
                            toolResults.append(result)
                        }

                        intentBuffer = "The AI has requested to call tools with details: $toolCallObj | Reasoning provided: $reasoning | Results from tool execution: $toolResults | Analyze this information and provide the next step or final answer."
                        recordAuditLog("TOOL_CALLED", "Executed tools: $toolCallObj")
                    } catch (secEx: SecurityException) {
                        if (secEx.message?.startsWith("APPROVAL_REQUIRED") == true) {
                            approvalRequired = true
                            approvalMessage = secEx.message ?: "Approval required"
                        } else {
                            intentBuffer = "Security Violation: ${secEx.message} | Reasoning: $reasoning | Please rethink your approach adhering to security perimeters."
                        }
                    } catch (e: Exception) {
                        intentBuffer = "The tool execution failed with error: ${e.message} | Reasoning: $reasoning | Please analyze this error, adjust arguments, or try an alternative approach."
                        recordAuditLog("TOOL_EXECUTION_ERROR", e.message)
                    }

                    if (approvalRequired) {
                        val hitlResponse = mutableMapOf<String, Any?>()
                        hitlResponse[STATUS] = Constants.AI_STATUS_ESCALATE
                        hitlResponse[REASONING] = approvalMessage
                        hitlResponse[RESULT] = "Paused awaiting Human-In-The-Loop approval."
                        hitlResponse[CONFIDENCE_SCORE] = 1.0
                        hitlResponse[TOOL_CALL] = toolCallObj
                        return hitlResponse
                    }
                }
            } else {
                intentBuffer = "The AI provided an unexpected status: $status | Reasoning: $reasoning | Please provide next steps or complete the task."
            }
        }

        recordAuditLog("AI_LOOP_LIMIT_EXCEEDED", "Terminated after $aiLoopLimit iterations.")
        return buildErrorResponse(
            "AI_LOOP_LIMIT_REACHED: The task could not be completed within the execution limit of $aiLoopLimit iteration(s)."
        )
    }

    /**
     * Handles tool execution with Dual-Model consensus verification and security perimeter enforcement.
     */
    private fun handleGuardedToolCall(
        session: AgentSession,
        toolCallObj: Map<String, Any?>,
        intent: String,
        traceId: String
    ): String {
        var pluginId = toolCallObj["plugin"]?.toString() ?: ""
        if (pluginId.isEmpty()) {
            pluginId = toolCallObj["name"]?.toString() ?: ""
        }

        @Suppress("UNCHECKED_CAST")
        val argsMap = (toolCallObj["arguments"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        if (toolCallObj.containsKey("method") && !argsMap.containsKey("method")) {
            argsMap["method"] = toolCallObj["method"]
        }

        // Dual-Model Governance Audit (Slide 5: If ChatGPT is compromised, Gemini catches it)
        val iv = intentValidator
        if (iv != null && governancePolicy.dualModelAuditingEnabled) {
            val safe = iv.performSafetyAudit(pluginId, argsMap.toString(), "Dual-Model Governance Audit Context")
            if (!safe) {
                logger.warning("[Fabric] Dual-Model safety audit rejected invocation of $pluginId with args: $argsMap")
                recordAuditLog("DUAL_MODEL_AUDIT_REJECTED", "Plugin: $pluginId, Args: $argsMap")
                throw SecurityException("Governance audit rejected tool invocation of $pluginId as unsafe.")
            }
        }

        return handleToolCall(session, toolCallObj, intent)
    }

    /**
     * Sliding context window management: compacts or summarizes older messages
     * when message limit is approached, keeping recent turns intact.
     */
    private fun enforceSlidingWindow(session: AgentSession) {
        val worker = llmFactory?.getActiveProvider()
        val isOnDevice = worker?.getName()?.contains("On-Device", ignoreCase = true) == true
        val maxMsgs = if (isOnDevice) 6 else (orchestrationService?.getMaxMessages() ?: 20)
        val messages = session.chatMemory.messages()

        if (messages.size > maxMsgs) {
            try {
                val worker = llmFactory?.getActiveProvider()
                if (worker != null) {
                    val toSummarize = messages.take(messages.size - 6)
                    val historyDump = toSummarize.joinToString("\n") { "${it.role().name}: ${it.content()}" }

                    val summaryReq = LlmRequest.builder()
                        .addMessage(ReveilaMessage.system("Summarize earlier conversation turns into 2-3 concise context bullet points."))
                        .addMessage(ReveilaMessage.user(historyDump))
                        .build()

                    val summary = worker.invoke(summaryReq).content ?: "Context summary preserved."
                    val recentTurns = messages.takeLast(6)

                    session.chatMemory.clear()
                    session.chatMemory.add(ReveilaMessage.system("Earlier conversation summary: $summary"))
                    recentTurns.forEach { session.chatMemory.add(it) }
                    logger.info("[Fabric] Sliding context window activated: compacted ${toSummarize.size} turns into concise summary.")
                }
            } catch (e: Exception) {
                logger.warning("[Fabric] Could not compact context window: ${e.message}")
            }
        }
    }

    open fun delegate(parent: Plugin, targetIntent: String, taskArguments: Map<String, Any?>): Any? {
        val childPluginId = "plugin-" + Uuid.randomUuid().substring(0, 4)
        val child = parent.deriveChild(childPluginId)

        val parentContext = sessionManager?.getContext(parent.traceId) ?: emptyMap()
        sessionManager?.saveContext(child.traceId, parentContext)

        var activePerimeter: SecurityPerimeter? = null
        val manifest = metadataRegistry?.getManifest(childPluginId)
        if (manifest != null) {
            activePerimeter = manifest.defaultPerimeter()
        }

        // Trace Propagation across sub-agent hierarchy
        flightRecorder?.recordStep(
            parent,
            "DELEGATE_TASK",
            mapOf(
                "childPlugin" to childPluginId,
                "childTraceId" to child.traceId,
                "targetIntent" to targetIntent
            )
        )

        val delegateCall = ToolCall()
        delegateCall.functionName = childPluginId
        delegateCall.arguments = taskArguments

        val metaInfo = mutableMapOf<String, Any?>()
        metaInfo["traceId"] = child.traceId
        metaInfo[AgentSession.ID] = parent.sessionId

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

    private fun buildErrorResponse(errorMessage: String): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map[STATUS] = Constants.AI_STATUS_FAILED
        map[REASONING] = errorMessage
        map[RESULT] = ""
        map[CONFIDENCE_SCORE] = 0.0
        map[TOOL_CALL] = emptyList<Any?>()
        return map
    }

    private fun recordAuditLog(action: String, details: String?) {
        try {
            val ds = dataService ?: run {
                val p = context?.getProxy("DataService")
                (p?.getInstance() as? DataService) ?: p as? DataService
            }
            if (ds != null) {
                val log = mutableMapOf<String, Any?>()
                log["action"] = action
                log["details"] = details
                log["timestamp"] = PlatformSystem.currentTimeMillis()
                ds.save("AuditLog", log)
            }
        } catch (e: Exception) {
            logger.warning("Failed to write AuditLog: ${e.message}")
        }
    }

    @Throws(Exception::class)
    private fun handleToolCall(session: AgentSession, toolCallObj: Map<String, Any?>, intent: String): String {
        val bridgeArgs = mutableMapOf<String, Any?>()
        bridgeArgs[AgentSession.ID] = session.sessionId
        bridgeArgs[AgentSession.THOUGHT] = toolCallObj
        bridgeArgs["traceId"] = session.parentTraceId ?: Uuid.randomUuid()

        var pluginId = toolCallObj["plugin"]?.toString() ?: ""
        if (pluginId.isEmpty()) {
            pluginId = toolCallObj["name"]?.toString() ?: ""
        }

        val toolCall = ToolCall()
        toolCall.functionName = pluginId

        @Suppress("UNCHECKED_CAST")
        val argsMap = (toolCallObj["arguments"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        if (toolCallObj.containsKey("method") && !argsMap.containsKey("method")) {
            argsMap["method"] = toolCallObj["method"]
        }
        toolCall.arguments = argsMap

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
            logger.severe(msg)
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
            val toolList = mutableListOf<LlmTool>()
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
            "Reveila Sovereign AI Agent",
            "Available Tools: " +
                tools.joinToString(", ") { it.name ?: "" } +
                "\n\nRelated Documents: " + searchKnowledgeVault(userPrompt),
            "Always follow governance policy boundaries and preserve confidentiality."
        )

        session.chatMemory.add(ReveilaMessage.user(userPrompt))

        val requestBuilder = LlmRequest.builder().tools(tools)
        requestBuilder.addMessage(ReveilaMessage.system(systemInstructions))

        if (isDebug) {
            logger.info("System Prompt used for request: $systemInstructions")
        }

        val isOnDevice = worker.getName().contains("On-Device", ignoreCase = true)
        val history = if (isOnDevice) {
            session.chatMemory.messages().takeLast(6)
        } else {
            session.chatMemory.messages()
        }

        for (msg in history) {
            requestBuilder.addMessage(msg)
        }

        val request = requestBuilder.build()
        var response: String? = null
        try {
            if (isDebug) {
                logger.info("Invoking LLM provider [${worker.getName()}] with prompt: $request")
            }
            response = worker.invoke(request).content
            if (isDebug) {
                logger.info("Received response from LLM provider [${worker.getName()}]: $response")
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
                logger.severe("Error recording response in chat memory: ${e.message}")
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
                if (isDebug) {
                    logger.info("Knowledge Vault search result for [$query]: $snippets")
                }
                return snippets
            }
        } catch (e: Exception) {
            logger.warning("Knowledge Vault search failed: ${e.message}")
        }
        return "No relevant internal documents found."
    }
}
