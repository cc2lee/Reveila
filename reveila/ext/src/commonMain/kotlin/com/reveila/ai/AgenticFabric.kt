package com.reveila.ai

import com.reveila.error.ExceptionCollection
import com.reveila.error.SecurityException
import com.reveila.safety.CommandChainAudit
import com.reveila.safety.FlightRecorder
import com.reveila.safety.GovernancePolicy
import com.reveila.safety.InvocationResult
import com.reveila.safety.ManagedInvocation
import com.reveila.safety.MetadataRegistry
import com.reveila.safety.SecretManager
import com.reveila.safety.SecurityPerimeter
import com.reveila.safety.TraceContextHolder
import com.reveila.safety.TraceContextSnapshot
import com.reveila.service.DataService
import com.reveila.system.Constants
import com.reveila.system.Plugin
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.system.SystemComponent
import com.reveila.system.UserPrincipal
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem
import com.reveila.tools.ToolActivityHub
import com.reveila.tools.download.ResumableDownloadManager
import com.reveila.util.Uuid
import com.reveila.util.json.JsonUtil
import kotlin.jvm.JvmOverloads

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
    private var secretManager: SecretManager? = null
    private var toolCoordinator: ReveilaToolCoordinator? = null
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

        try {
            val smProxy = context?.getProxy("SecretManager")
            this.secretManager = (smProxy?.getInstance() as? SecretManager) ?: smProxy as? SecretManager
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

        val coordinator = ReveilaToolCoordinator(
            systemHome = home,
            secretManagerProvider = {
                secretManager ?: try {
                    val p = context?.getProxy("SecretManager")
                    ((p?.getInstance() as? SecretManager) ?: p as? SecretManager)?.also { secretManager = it }
                } catch (_: Exception) {
                    null
                }
            },
            downloadManagerProvider = {
                try {
                    val p = context?.getProxy("ResumableDownloadManager")
                    (p?.getInstance() as? ResumableDownloadManager) ?: p as? ResumableDownloadManager
                } catch (_: Exception) {
                    null
                }
            },
            knowledgeVaultProvider = {
                try {
                    val p = context?.getProxy("KnowledgeVault")
                    (p?.getInstance() as? KnowledgeVault) ?: p as? KnowledgeVault
                } catch (_: Exception) {
                    null
                }
            },
            auditLogger = { action, details -> recordAuditLog(action, details) }
        )
        coordinator.registerManifests(metadataRegistry)
        this.toolCoordinator = coordinator

        logger.info("AgenticFabric started with policy [${governancePolicy.policyName}] (aiLoopLimit=$aiLoopLimit, tools=5).")
    }

    open fun getToolCoordinator(): ReveilaToolCoordinator? = toolCoordinator

    open fun askAgent(userIntent: String, sessionId: String?, systemPrompt: String?): Map<String, Any?> {
        return askAgentWithMode(userIntent, sessionId, systemPrompt, assistantMode = null)
    }

    open fun askAgentWithMode(
        userIntent: String,
        sessionId: String?,
        systemPrompt: String?,
        assistantMode: String?
    ): Map<String, Any?> {
        var actualSessionId = sessionId
        if (actualSessionId.isNullOrBlank()) {
            actualSessionId = Uuid.randomUuid()
        }

        var session = orchestrationService?.getSession(actualSessionId)
        if (session == null) {
            session = orchestrationService?.createSession(actualSessionId, actualSessionId)
            if (!systemPrompt.isNullOrBlank()) {
                session?.addMessage(
                    ReveilaMessage.system(
                        "Context carried over from previous session: $systemPrompt",
                        spaceId = session.spaceId
                    )
                )
            }
        }

        val principal = RolePrincipal("ui-client")
        val subject = Subject()
        subject.principals.add(principal)

        val modeOverride = assistantMode?.trim()?.takeIf { it.isNotEmpty() }
        val jsonResponse = if (session != null) {
            processIntent(session, subject, userIntent, preferredProviderName = modeOverride)
        } else {
            buildErrorResponse("OrchestrationService is not available")
        }
        val interpretation = interpretAiResponse(jsonResponse)
        val uiResponse = mutableMapOf<String, Any?>()
        uiResponse["answer"] = interpretation
        uiResponse["status"] = jsonResponse[STATUS] ?: Constants.AI_STATUS_COMPLETED
        if (jsonResponse["toolAttachment"] != null) {
            uiResponse["toolAttachment"] = jsonResponse["toolAttachment"]
        }
        uiResponse["waitingForUser"] = (jsonResponse[STATUS]?.toString()?.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true) == true) ||
            ToolActivityHub.getSnapshot().waitingForUserInput
        uiResponse["sessionId"] = session?.sessionId ?: actualSessionId
        uiResponse["spaceId"] = session?.spaceId ?: AgentSession.DEFAULT_SPACE_ID
        uiResponse["spaceName"] = session?.spaceName ?: AgentSession.DEFAULT_SPACE_NAME

        if (isDebug) {
            logger.info("Final response to UI client: $uiResponse")
        }

        return uiResponse
    }

    /**
     * Prepares a multi-turn session, injects KnowledgeVault RAG context, and formats the chat prompt
     * for in-process native streaming while preserving session continuity.
     */
    open fun prepareStreamingTurn(
        userIntent: String,
        sessionId: String?,
        systemPrompt: String?
    ): Map<String, String> {
        return prepareStreamingTurnWithMode(userIntent, sessionId, systemPrompt, assistantMode = null)
    }

    open fun prepareStreamingTurnWithMode(
        userIntent: String,
        sessionId: String?,
        systemPrompt: String?,
        assistantMode: String?
    ): Map<String, String> {
        val systemHome = context?.properties?.getProperty("system.home")
        val assessment = TaskCapabilityRouter.assessTask(
            intent = "auto",
            prompt = userIntent,
            executionTarget = "auto",
            systemHome = systemHome,
            localProvider = llmFactory?.getProvider("On-Device Model")
        )
        val govSel = llmFactory?.resolveGovernanceProviderForIntent(assessment.taskCategory)
        if (govSel != null && !govSel.isCapable) {
            return mapOf(
                "error" to "⚠️ ${govSel.errorMessage}",
                "sessionId" to (sessionId ?: Uuid.randomUuid())
            )
        }
        val effectiveMode = assistantMode?.trim()?.takeIf { it.isNotEmpty() }
            ?: llmFactory?.getConfiguredAssistantName()
            ?: "Auto"
        val workerSel = when {
            effectiveMode.equals("Auto", ignoreCase = true) ->
                llmFactory?.resolveWorkerProviderForIntent(assessment.taskCategory, forceAuto = true)
            else ->
                llmFactory?.resolveExplicitProviderForIntent(effectiveMode, assessment.taskCategory)
        }
        if (workerSel != null && !workerSel.isCapable) {
            return mapOf(
                "error" to "⚠️ ${workerSel.errorMessage}",
                "sessionId" to (sessionId ?: Uuid.randomUuid())
            )
        }
        if (workerSel != null && !workerSel.providerName.equals("On-Device Model", ignoreCase = true)) {
            // Auto mode or explicit Cloud selection routed to a Cloud LLM -> signal bridge to use askAgent instead of local GGUF stream
            return mapOf(
                "routeToCloud" to "true",
                "providerName" to workerSel.providerName,
                "taskCategory" to assessment.taskCategory,
                "sessionId" to (sessionId ?: Uuid.randomUuid())
            )
        }
        val extraKeywords = metadataRegistry?.getLocalRoutingKeywords().orEmpty()
        if (toolCoordinator?.shouldRouteToAgenticLoop(userIntent, extraKeywords) == true) {
            return mapOf(
                "routeToAgenticLoop" to "true",
                "taskCategory" to assessment.taskCategory,
                "sessionId" to (sessionId ?: Uuid.randomUuid())
            )
        }

        val actualSessionId = if (sessionId.isNullOrBlank()) Uuid.randomUuid() else sessionId
        var session = orchestrationService?.getSession(actualSessionId)
        if (session == null) {
            session = orchestrationService?.createSession(actualSessionId, actualSessionId)
            if (!systemPrompt.isNullOrBlank()) {
                session?.addMessage(
                    ReveilaMessage.system(
                        "Context carried over from previous session: $systemPrompt",
                        spaceId = session.spaceId
                    )
                )
            }
        }
        if (session != null) {
            enforceSlidingWindow(session)
        }

        val ragSnippets = searchKnowledgeVault(userIntent)
        val activeSpaceName = session?.spaceName?.takeIf { it.isNotBlank() } ?: AgentSession.DEFAULT_SPACE_NAME
        val rawWorker = unwrapProvider(workerSel?.provider ?: llmFactory?.getActiveProvider())
        val activeModel = (rawWorker as? BaseLlmProvider)?.model
            ?.takeIf { it.isNotBlank() }
            ?: context?.properties?.getProperty("ai.llm.model.name")?.takeIf { it.isNotBlank() }
            ?: LocalLlamaProvider.nativeDelegate?.getLoadedModelPath()

        val rendered = Prompt.buildSystemPrompt(
            role = "Aegin, a sovereign on-device AI assistant in the '$activeSpaceName' Space",
            spaceName = activeSpaceName,
            tools = emptyList(),
            vaultContext = ragSnippets,
            constraints = null,
            modelName = activeModel,
            systemHome = systemHome,
            mode = PromptMode.CONVERSATIONAL
        )
        val historyWindow = rendered.resolvedTemplate.maxHistoryMessages ?: 6

        val messages = mutableListOf<ReveilaMessage>()
        messages.add(ReveilaMessage.system(rendered.systemPrompt))
        if (session != null) {
            messages.addAll(buildSpaceContextMessages(session, maxHistoryMessages = historyWindow))
        }
        messages.add(ReveilaMessage.user(userIntent, spaceId = session?.spaceId))

        val (formattedPrompt, stopToken) = LocalLlamaProvider.formatMessages(messages, activeModel, systemHome)
        return mapOf(
            "sessionId" to (session?.sessionId ?: actualSessionId),
            "spaceId" to (session?.spaceId ?: AgentSession.DEFAULT_SPACE_ID),
            "spaceName" to activeSpaceName,
            "formattedPrompt" to formattedPrompt,
            "stopToken" to stopToken,
            "templateId" to rendered.resolvedTemplate.templateId
        )
    }

    @kotlin.jvm.JvmOverloads
    open fun askAgentInSpace(
        userIntent: String,
        spaceId: String,
        spaceName: String? = null,
        sessionId: String? = null,
        systemPrompt: String? = null,
        assistantMode: String? = null
    ): Map<String, Any?> {
        val normalizedSpaceId = AgentSession.normalizeSpaceId(spaceId)
        val resolvedSpaceName = AgentSession.displayNameForSpace(normalizedSpaceId, spaceName ?: spaceId)
        val session = orchestrationService?.getOrCreateSpaceSession(
            spaceId = normalizedSpaceId,
            spaceName = resolvedSpaceName,
            preferredSessionId = sessionId
        )
        if (session != null && !systemPrompt.isNullOrBlank() && session.summary.isBlank() && session.chatMemory.messages().isEmpty()) {
            session.updateSummary(systemPrompt)
        }
        val response = askAgentWithMode(
            userIntent,
            session?.sessionId ?: sessionId ?: AgentSession.sessionIdForSpace(normalizedSpaceId),
            systemPrompt,
            assistantMode
        ).toMutableMap()
        response["spaceId"] = normalizedSpaceId
        response["spaceName"] = resolvedSpaceName
        return response
    }

    @kotlin.jvm.JvmOverloads
    open fun prepareStreamingTurnInSpace(
        userIntent: String,
        spaceId: String,
        spaceName: String? = null,
        sessionId: String? = null,
        systemPrompt: String? = null,
        assistantMode: String? = null
    ): Map<String, Any?> {
        val normalizedSpaceId = AgentSession.normalizeSpaceId(spaceId)
        val resolvedSpaceName = AgentSession.displayNameForSpace(normalizedSpaceId, spaceName ?: spaceId)
        val session = orchestrationService?.getOrCreateSpaceSession(
            spaceId = normalizedSpaceId,
            spaceName = resolvedSpaceName,
            preferredSessionId = sessionId
        )
        if (session != null && !systemPrompt.isNullOrBlank() && session.summary.isBlank() && session.chatMemory.messages().isEmpty()) {
            session.updateSummary(systemPrompt)
        }
        return prepareStreamingTurnWithMode(
            userIntent,
            session?.sessionId ?: sessionId ?: AgentSession.sessionIdForSpace(normalizedSpaceId),
            systemPrompt,
            assistantMode
        )
    }

    /**
     * Commits a completed streaming turn into the session's chat memory and persists the session.
     */
    @kotlin.jvm.JvmOverloads
    open fun commitStreamingTurn(
        sessionId: String,
        userIntent: String,
        assistantAnswer: String,
        spaceId: String? = null,
        spaceName: String? = null
    ): Boolean {
        var session = orchestrationService?.getSession(sessionId)
        if (session == null && !spaceId.isNullOrBlank()) {
            session = orchestrationService?.getOrCreateSpaceSession(spaceId, spaceName, sessionId)
        }
        if (session == null) return false
        if (!spaceId.isNullOrBlank()) {
            session.assignSpace(spaceId, spaceName)
        }
        session.addMessage(ReveilaMessage.user(userIntent, spaceId = session.spaceId))
        session.addMessage(ReveilaMessage.assistant(assistantAnswer, spaceId = session.spaceId))
        enforceSlidingWindow(session)
        sessionManager?.saveSession(session.sessionId, session)
        return true
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
        return processIntent(session, subject, intent, preferredProviderName = null)
    }

    open fun processIntent(
        session: AgentSession,
        subject: Subject,
        intent: String,
        preferredProviderName: String?
    ): Map<String, Any?> {
        val existingCtx = TraceContextHolder.getContext()
        val traceId = session.parentTraceId?.takeIf { it.isNotBlank() }
            ?: existingCtx?.traceId?.takeIf { it.isNotBlank() }
            ?: Uuid.randomUuid()
        val rootSpanId = Uuid.randomUuid()
        val parentSpanId = existingCtx?.takeIf { it.traceId == traceId }?.spanId
        val actorId = subject.principals.filterIsInstance<UserPrincipal>().firstOrNull()?.name
            ?: existingCtx?.actorId?.takeIf { it.isNotBlank() }
            ?: "local-user"
        val tenantId = context?.properties?.getProperty("tenant-id", "default-tenant") ?: "default-tenant"

        TraceContextHolder.setContext(
            TraceContextSnapshot(
                traceId = traceId,
                spanId = rootSpanId,
                parentSpanId = parentSpanId,
                actorId = actorId,
                actorType = "USER"
            )
        )
        val dummyPlugin = Plugin(
            sessionId = session.sessionId,
            name = "AgenticFabric",
            tenantId = tenantId,
            traceId = traceId,
            spanId = rootSpanId,
            parentSpanId = parentSpanId,
            actorId = actorId,
            actorType = "USER"
        )
        flightRecorder?.recordCommandEvent(
            plugin = dummyPlugin,
            action = "PROMPT_ISSUED",
            details = intent.take(240),
            actorId = actorId,
            actorType = "USER"
        )

        logger.info("[Fabric] Processing intent (trace: $traceId, actor=$actorId, preferredProvider=${preferredProviderName ?: "default"}): $intent")

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

        // 2b. Check if the root agent prompt is requesting creation of a repeating / scheduled background task
        val isAutonomousCaller = principals.any { it is RolePrincipal && it.name.equals("autonomous-agent", ignoreCase = true) }
        if (!isAutonomousCaller) {
            val taskCreatedResponse = tryHandleTaskCreationIntent(session, intent)
            if (taskCreatedResponse != null) {
                return taskCreatedResponse
            }
        }

        // 2c. Resolve Worker & Governance LLM capability against the 6-category taxonomy
        val factory = llmFactory
        val effectivePreferredProvider: String? = if (factory != null) {
            val systemHome = context?.properties?.getProperty("system.home")
            val assessment = TaskCapabilityRouter.assessTask(
                intent = "auto",
                prompt = intent,
                executionTarget = "auto",
                systemHome = systemHome,
                localProvider = factory.getProvider("On-Device Model")
            )

            val govSel = factory.resolveGovernanceProviderForIntent(assessment.taskCategory)
            if (!govSel.isCapable) {
                val alertMsg = "⚠️ ${govSel.errorMessage}"
                recordAuditLog("GOVERNANCE_LLM_INCOMPATIBLE", alertMsg)
                return buildErrorResponse(alertMsg)
            }

            if (!preferredProviderName.isNullOrBlank()) {
                val explicitSel = if (preferredProviderName.equals("Auto", ignoreCase = true)) {
                    factory.resolveWorkerProviderForIntent(assessment.taskCategory, forceAuto = true)
                } else {
                    factory.resolveExplicitProviderForIntent(preferredProviderName, assessment.taskCategory)
                }
                if (!explicitSel.isCapable) {
                    val alertMsg = "⚠️ ${explicitSel.errorMessage}"
                    recordAuditLog("ASSISTANT_LLM_UNAVAILABLE", alertMsg)
                    return buildErrorResponse(alertMsg)
                }
                explicitSel.providerName
            } else {
                val defaultMode = if (isAutonomousCaller) {
                    null
                } else {
                    factory.getConfiguredAssistantName()
                }
                val workerSel = when {
                    defaultMode.isNullOrBlank() -> factory.resolveWorkerProviderForIntent(assessment.taskCategory)
                    defaultMode.equals("Auto", ignoreCase = true) -> factory.resolveWorkerProviderForIntent(assessment.taskCategory, forceAuto = true)
                    else -> factory.resolveExplicitProviderForIntent(defaultMode, assessment.taskCategory)
                }
                if (!workerSel.isCapable) {
                    val alertMsg = "⚠️ ${workerSel.errorMessage}"
                    recordAuditLog("WORKER_LLM_INCOMPATIBLE", alertMsg)
                    return buildErrorResponse(alertMsg)
                }
                workerSel.providerName
            }
        } else {
            preferredProviderName
        }

        var intentBuffer = intent
        val responseValidator = AiResponseValidator(governancePolicy)
        return executeAgenticLoop(
            session = session,
            initialIntentBuffer = intentBuffer,
            effectivePreferredProvider = effectivePreferredProvider,
            traceId = traceId,
            actorId = actorId,
            dummyPlugin = dummyPlugin,
            responseValidator = responseValidator
        )
    }

    private fun executeAgenticLoop(
        session: AgentSession,
        initialIntentBuffer: String,
        effectivePreferredProvider: String?,
        traceId: String,
        actorId: String,
        dummyPlugin: Plugin,
        responseValidator: AiResponseValidator,
        persistInitialUserTurn: Boolean = true
    ): Map<String, Any?> {
        var intentBuffer = initialIntentBuffer
        val browserUpdatedBefore = ToolActivityHub.getSnapshot().browser.updatedAtMs
        var browserUsedInLoop = false
        // 3. The Robust Agentic AI Loop
        enforceSlidingWindow(session)
        for (loopCount in 0 until aiLoopLimit) {
            logger.info("[Fabric] Starting Agentic Loop iteration [${loopCount + 1}/$aiLoopLimit]")

            var rawResponse: String?
            try {
                rawResponse = askAi(
                    session = session,
                    userPrompt = intentBuffer,
                    preferredProviderName = effectivePreferredProvider,
                    recordUserTurn = (loopCount == 0 && persistInitialUserTurn)
                )
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

            val rawSanitized = validationResult.sanitizedContent
            val sanitizedResponse = JsonUtil.clean(rawSanitized)

            // Parse response JSON only when it carries the agentic status/result envelope;
            // plain conversational replies (even if they contain '{...}' snippets) pass through intact.
            val jsonResponse: Map<String, Any?> = try {
                val parsed = JsonUtil.parseJsonStringToMap(sanitizedResponse)
                if (parsed.containsKey(STATUS) || parsed.containsKey(RESULT) || parsed.containsKey(TOOL_CALL) || parsed.containsKey("tool_call")) {
                    parsed
                } else if (rawSanitized.isNotBlank()) {
                    mapOf(
                        STATUS to Constants.AI_STATUS_COMPLETED,
                        REASONING to "Generated by sovereign neural model",
                        RESULT to rawSanitized,
                        CONFIDENCE_SCORE to 0.95
                    )
                } else {
                    throw IllegalStateException("Empty agentic JSON envelope")
                }
            } catch (e: Exception) {
                if (rawSanitized.isNotBlank()) {
                    mapOf(
                        STATUS to Constants.AI_STATUS_COMPLETED,
                        REASONING to "Generated by sovereign neural model",
                        RESULT to rawSanitized,
                        CONFIDENCE_SCORE to 0.95
                    )
                } else {
                    logger.warning("[Fabric] Invalid JSON from AI: $sanitizedResponse")
                    intentBuffer = "Invalid JSON response from AI. Please format your reply strictly as a JSON document containing 'status', 'reasoning', 'result', and 'confidence-score' keys."
                    continue
                }
            }

            val rawStatus = jsonResponse[STATUS]?.toString() ?: ""
            val reasoning = jsonResponse[REASONING]?.toString() ?: ""
            val rawResult = jsonResponse[RESULT]?.toString().orEmpty()
            val rawToolCallObj = jsonResponse[TOOL_CALL] ?: jsonResponse["tool_call"]
            val hasNonEmptyToolCall = (rawToolCallObj is Map<*, *> && rawToolCallObj.isNotEmpty()) ||
                (rawToolCallObj is List<*> && rawToolCallObj.isNotEmpty())
            val status = if (hasNonEmptyToolCall &&
                (rawStatus.isBlank() || (rawStatus.equals(Constants.AI_STATUS_COMPLETED, ignoreCase = true) && rawResult.isBlank()))
            ) {
                Constants.AI_STATUS_TOOL_CALL
            } else {
                rawStatus
            }
            flightRecorder?.recordReasoning(dummyPlugin, reasoning)

            // Handle terminal loop statuses
            if (status.equals(Constants.AI_STATUS_COMPLETED, ignoreCase = true)) {
                recordAuditLog("TASK_COMPLETED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "task_completed", jsonResponse.filterValues { it != null }.mapValues { it.value as Any })
                val browserSnap = ToolActivityHub.getSnapshot().browser
                val enrichedResponse = jsonResponse.toMutableMap()
                if ((browserUsedInLoop || browserSnap.updatedAtMs > browserUpdatedBefore) && browserSnap.url.isNotBlank()) {
                    val isSynthesizedFeed = browserSnap.rawHtml.contains("<!-- reveila-feed-html -->", ignoreCase = true)
                    val attachmentMap = mapOf(
                        "tool" to "virtual_browser",
                        "url" to browserSnap.url,
                        "title" to browserSnap.title,
                        "statusCode" to browserSnap.statusCode,
                        "lastAction" to browserSnap.lastAction,
                        "contentPreview" to browserSnap.contentPreview.take(1500),
                        "rawHtml" to if (isSynthesizedFeed) browserSnap.rawHtml.take(12_000) else ""
                    )
                    enrichedResponse["toolAttachment"] = attachmentMap
                    attachToolMetadataToLastAssistantMessage(session, enrichedResponse, attachmentMap)
                }
                enforceSlidingWindow(session)
                sessionManager?.saveSession(session.sessionId, session)
                return enrichedResponse
            } else if (status.equals(Constants.AI_STATUS_INSUFFICIENT_CONTEXT, ignoreCase = true)) {
                recordAuditLog("INSUFFICIENT_CONTEXT", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "insufficient_context", mapOf("reason" to reasoning))
                enforceSlidingWindow(session)
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true)) {
                recordAuditLog("ESCALATED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "escalated_hitl", mapOf("reason" to reasoning))
                enforceSlidingWindow(session)
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_FAILED, ignoreCase = true)) {
                recordAuditLog("TASK_FAILED", reasoning)
                flightRecorder?.recordStep(dummyPlugin, "task_failed", mapOf("reason" to reasoning))
                enforceSlidingWindow(session)
                sessionManager?.saveSession(session.sessionId, session)
                return jsonResponse
            } else if (status.equals(Constants.AI_STATUS_TOOL_CALL, ignoreCase = true)) {
                val toolCallObj = jsonResponse[TOOL_CALL] ?: jsonResponse["tool_call"]
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
                            val callsList = toolCallObj.filterIsInstance<Map<String, Any?>>()
                            for ((idx, singleToolCall) in callsList.withIndex()) {
                                val remaining = if (idx + 1 < callsList.size) callsList.drop(idx + 1) else emptyList()
                                val result = handleGuardedToolCall(
                                    session = session,
                                    toolCallObj = singleToolCall,
                                    intent = currentResponse,
                                    traceId = traceId,
                                    actorId = actorId,
                                    preferredProviderName = effectivePreferredProvider,
                                    remainingCalls = remaining,
                                    accumulatedResults = toolResults.toString(),
                                    originalReasoning = reasoning
                                )
                                val label = singleToolCall["name"]?.toString()
                                    ?: singleToolCall["plugin"]?.toString()
                                    ?: singleToolCall["method"]?.toString()
                                    ?: "tool"
                                toolResults.append("\n- Tool: ").append(label)
                                    .append("\n  Result: ").append(result)
                            }
                        } else if (toolCallObj is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val singleToolCall = toolCallObj as Map<String, Any?>
                            val result = handleGuardedToolCall(
                                session = session,
                                toolCallObj = singleToolCall,
                                intent = currentResponse,
                                traceId = traceId,
                                actorId = actorId,
                                preferredProviderName = effectivePreferredProvider,
                                remainingCalls = emptyList(),
                                accumulatedResults = "",
                                originalReasoning = reasoning
                            )
                            toolResults.append(result)
                        }

                        if (ToolActivityHub.getSnapshot().browser.updatedAtMs > browserUpdatedBefore) {
                            browserUsedInLoop = true
                        }
                        if (ToolActivityHub.getSnapshot().browser.waitingForUser) {
                            sessionManager?.saveSession(session.sessionId, session)
                            val waitingSnap = ToolActivityHub.getSnapshot().browser
                            return mapOf(
                                STATUS to Constants.AI_STATUS_ESCALATE,
                                REASONING to waitingSnap.takeoverMessage.ifBlank { "Browser requires user takeover (${waitingSnap.takeoverReason})." },
                                RESULT to "Browser paused at ${waitingSnap.url} waiting for user input. Tap the Live Browser window to complete verification and return control.",
                                CONFIDENCE_SCORE to 1.0,
                                TOOL_CALL to toolCallObj
                            )
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
                        hitlResponse[RESULT] = ToolActivityHub.getSnapshot().pendingAuthorization?.reason
                            ?: "Paused awaiting Human-In-The-Loop authorization."
                        hitlResponse[CONFIDENCE_SCORE] = 1.0
                        hitlResponse[TOOL_CALL] = toolCallObj
                        sessionManager?.saveSession(session.sessionId, session)
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
     * Handles tool execution with Dual-Model consensus verification, secret/permission gating, and security perimeter enforcement.
     */
    private fun handleGuardedToolCall(
        session: AgentSession,
        toolCallObj: Map<String, Any?>,
        intent: String,
        traceId: String,
        actorId: String = TraceContextHolder.getContext()?.actorId.orEmpty().ifBlank { "local-user" },
        preferredProviderName: String? = null,
        remainingCalls: List<Map<String, Any?>> = emptyList(),
        accumulatedResults: String = "",
        originalReasoning: String = ""
    ): String {
        val coordinator = toolCoordinator
        var pluginId = coordinator?.extractToolName(toolCallObj).orEmpty()
        if (pluginId.isEmpty()) {
            pluginId = toolCallObj["plugin"]?.toString()
                ?: toolCallObj["name"]?.toString()
                ?: ""
        }

        @Suppress("UNCHECKED_CAST")
        val argsMap = coordinator?.extractArguments(toolCallObj)
            ?: ((toolCallObj["arguments"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()).also { m ->
                if (toolCallObj.containsKey("method") && !m.containsKey("method")) {
                    m["method"] = toolCallObj["method"]
                }
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

        if (coordinator != null && coordinator.isRegisteredTool(pluginId)) {
            val result = coordinator.authorizeAndExecute(
                toolCallObj = toolCallObj,
                session = session,
                actorId = actorId,
                preferredProviderName = preferredProviderName,
                requireInteractiveAuth = true,
                remainingCalls = remainingCalls,
                accumulatedResults = accumulatedResults,
                originalReasoning = originalReasoning
            )
            session.chatMemory.add(ReveilaMessage.assistant("Reasoning: Executed tool [$pluginId].", spaceId = session.spaceId))
            session.chatMemory.add(ReveilaMessage.tool("Tool [$pluginId] returned: $result", spaceId = session.spaceId))
            return result
        }

        return handleToolCall(session, toolCallObj, intent)
    }

    // =========================================================================
    // Live Tool Activity, User Takeover & Interactive Authorization Endpoints
    // =========================================================================

    private fun attachToolMetadataToLastAssistantMessage(
        session: AgentSession,
        responseMap: Map<String, Any?>,
        attachmentMap: Map<String, Any?>
    ) {
        val allMsgs = session.chatMemory.messages().toMutableList()
        val lastAssistantIdx = allMsgs.indexOfLast { it.role == LlmRole.ASSISTANT }
        if (lastAssistantIdx < 0) return
        val existing = allMsgs[lastAssistantIdx]
        val interpreted = interpretAiResponse(responseMap).ifBlank { existing.content }
        val wrapped = JsonUtil.toJsonString(
            mapOf(
                "answer" to interpreted,
                "status" to (responseMap[STATUS] ?: Constants.AI_STATUS_COMPLETED),
                "toolAttachment" to attachmentMap
            )
        )
        allMsgs[lastAssistantIdx] = existing.copy(content = wrapped)
        session.chatMemory.replaceMessages(allMsgs)
    }

    open fun getToolActivitySnapshotJson(): String = ToolActivityHub.getSnapshotJson()

    open fun getBrowserConfigJson(): String {
        return toolCoordinator?.getBrowserConfigJson()
            ?: JsonUtil.toJsonString(mapOf("defaultSearchUrl" to "", "presets" to emptyList<Any>()))
    }

    open fun saveBrowserConfigJson(defaultSearchUrl: String): String {
        return toolCoordinator?.saveBrowserConfigJson(defaultSearchUrl)
            ?: JsonUtil.toJsonString(mapOf("defaultSearchUrl" to defaultSearchUrl.trim()))
    }

    @JvmOverloads
    open fun resolvePendingToolAuthorizationJson(
        requestId: String,
        decision: String,
        username: String = "",
        password: String = "",
        secretKey: String = "",
        secretValue: String = "",
        editedScript: String = ""
    ): String {
        val coordinator = toolCoordinator
            ?: return JsonUtil.toJsonString(mapOf("error" to "Tool coordinator not initialized."))
        val resolution = coordinator.resolvePendingAuthorization(
            requestId = requestId,
            decision = decision,
            approverId = TraceContextHolder.getContext()?.actorId?.takeIf { it.isNotBlank() } ?: "local-user",
            username = username,
            password = password,
            secretKey = secretKey,
            secretValue = secretValue,
            editedScript = editedScript
        )
        if (!resolution.found || resolution.pendingExecution == null) {
            return JsonUtil.toJsonString(
                mapOf(
                    "answer" to resolution.message,
                    "status" to Constants.AI_STATUS_FAILED,
                    "snapshot" to ToolActivityHub.getSnapshotJson()
                )
            )
        }

        val pending = resolution.pendingExecution
        val session = orchestrationService?.getOrCreateSpaceSession(
            spaceId = pending.spaceId,
            spaceName = pending.spaceId,
            preferredSessionId = pending.sessionId
        ) ?: return JsonUtil.toJsonString(
            mapOf(
                "answer" to if (resolution.approved) resolution.toolOutput else resolution.message,
                "status" to if (resolution.approved) Constants.AI_STATUS_COMPLETED else Constants.AI_STATUS_FAILED
            )
        )

        val traceId = pending.view.traceId.ifBlank { Uuid.randomUuid() }
        val dummyPlugin = Plugin(
            sessionId = traceId,
            name = COMPONENT_NAME,
            tenantId = context?.properties?.getProperty("tenant-id", "default-tenant") ?: "default-tenant",
            traceId = traceId,
            actorId = pending.actorId,
            actorType = "USER"
        )

        if (!resolution.approved) {
            session.addMessage(ReveilaMessage.assistant(resolution.message, spaceId = session.spaceId))
            sessionManager?.saveSession(session.sessionId, session)
            return JsonUtil.toJsonString(
                mapOf(
                    "answer" to resolution.message,
                    "status" to Constants.AI_STATUS_FAILED,
                    "sessionId" to session.sessionId,
                    "spaceId" to session.spaceId
                )
            )
        }

        val combinedResults = StringBuilder(pending.accumulatedResults)
        combinedResults.append("\n- Tool: ").append(pending.view.toolName)
            .append("\n  Result: ").append(resolution.toolOutput)

        // Continue any queued subsequent tool calls before resuming the LLM loop
        if (pending.remainingCalls.isNotEmpty()) {
            try {
                for ((idx, nextCall) in pending.remainingCalls.withIndex()) {
                    val tailCalls = pending.remainingCalls.drop(idx + 1)
                    val nextOut = handleGuardedToolCall(
                        session = session,
                        toolCallObj = nextCall,
                        intent = "resumed_after_approval",
                        traceId = traceId,
                        actorId = pending.actorId,
                        preferredProviderName = pending.preferredProviderName,
                        remainingCalls = tailCalls,
                        accumulatedResults = combinedResults.toString(),
                        originalReasoning = pending.originalReasoning
                    )
                    val label = nextCall["name"]?.toString() ?: nextCall["plugin"]?.toString() ?: "tool"
                    combinedResults.append("\n- Tool: ").append(label).append("\n  Result: ").append(nextOut)
                }
            } catch (secEx: SecurityException) {
                if (secEx.message?.startsWith("APPROVAL_REQUIRED") == true) {
                    sessionManager?.saveSession(session.sessionId, session)
                    return JsonUtil.toJsonString(
                        mapOf(
                            "answer" to "Additional tool authorization required.",
                            "status" to Constants.AI_STATUS_ESCALATE,
                            "waitingForUser" to true,
                            "sessionId" to session.sessionId,
                            "spaceId" to session.spaceId
                        )
                    )
                }
            }
        }

        if (ToolActivityHub.getSnapshot().browser.waitingForUser) {
            sessionManager?.saveSession(session.sessionId, session)
            val browserSnap = ToolActivityHub.getSnapshot().browser
            return JsonUtil.toJsonString(
                mapOf(
                    "answer" to "Browser requires manual verification at ${browserSnap.url} (${browserSnap.takeoverReason}). Tap the Live Browser preview to complete and return control.",
                    "status" to Constants.AI_STATUS_ESCALATE,
                    "waitingForUser" to true,
                    "sessionId" to session.sessionId,
                    "spaceId" to session.spaceId
                )
            )
        }

        session.addMessage(ReveilaMessage.tool("Authorized tool execution returned: $combinedResults", spaceId = session.spaceId))
        val resumePrompt = "Authorized tool execution finished with results: $combinedResults | Original reasoning: ${pending.originalReasoning} | Continue and provide the next step or final response."
        val loopResult = executeAgenticLoop(
            session = session,
            initialIntentBuffer = resumePrompt,
            effectivePreferredProvider = pending.preferredProviderName,
            traceId = traceId,
            actorId = pending.actorId,
            dummyPlugin = dummyPlugin,
            responseValidator = AiResponseValidator(governancePolicy),
            persistInitialUserTurn = false
        )
        val answer = interpretAiResponse(loopResult)
        return JsonUtil.toJsonString(
            mapOf(
                "answer" to answer,
                "status" to (loopResult[STATUS] ?: Constants.AI_STATUS_COMPLETED),
                "waitingForUser" to ((loopResult[STATUS]?.toString()?.equals(Constants.AI_STATUS_ESCALATE, ignoreCase = true) == true) ||
                    ToolActivityHub.getSnapshot().waitingForUserInput),
                "sessionId" to session.sessionId,
                "spaceId" to session.spaceId,
                "toolOutput" to resolution.toolOutput
            )
        )
    }

    @JvmOverloads
    open fun browserUserActionJson(
        action: String,
        url: String = "",
        target: String = "",
        text: String = "",
        form: String = "",
        cookieHeader: String = "",
        rawHtml: String = ""
    ): String {
        return toolCoordinator?.handleBrowserUserAction(action, url, target, text, form, cookieHeader, rawHtml)
            ?: ToolActivityHub.getSnapshotJson()
    }

    @JvmOverloads
    open fun terminalUserActionJson(
        action: String,
        script: String = "",
        workingDir: String = ""
    ): String {
        return toolCoordinator?.handleTerminalUserAction(action, script, workingDir)
            ?: ToolActivityHub.getSnapshotJson()
    }

    @JvmOverloads
    open fun downloadUserActionJson(
        action: String,
        downloadId: String = ""
    ): String {
        return toolCoordinator?.handleDownloadUserAction(action, downloadId)
            ?: ToolActivityHub.getSnapshotJson()
    }

    @JvmOverloads
    open fun secretVaultUserActionJson(
        action: String,
        key: String = "",
        value: String = "",
        category: String = "GENERAL",
        domain: String = "",
        username: String = ""
    ): String {
        return toolCoordinator?.handleSecretVaultUserAction(action, key, value, category, domain, username)
            ?: JsonUtil.toJsonString(mapOf("error" to "Secret vault coordinator unavailable"))
    }

    /**
     * Sliding context window management: compacts and summarizes older messages
     * for the session's active Space when message limit is exceeded, replacing raw
     * pre-window turns in the database with the latest summary + recent turns.
     */
    private fun enforceSlidingWindow(session: AgentSession) {
        val worker = llmFactory?.getActiveProvider()
        val isOnDevice = worker?.getName()?.contains("On-Device", ignoreCase = true) == true
        val maxMsgs = if (isOnDevice) 6 else (orchestrationService?.getMaxMessages() ?: 20)
        val spaceId = session.spaceId
        val messages = session.messagesForSpace(spaceId)
        val conversationTurns = messages.filterNot {
            AgentSession.isConversationSummaryMessage(it.role, it.content)
        }

        if (conversationTurns.size > maxMsgs) {
            val keepCount = (maxMsgs / 2).coerceIn(2, 6)
            val toSummarize = conversationTurns.dropLast(keepCount)
            val recentTurns = conversationTurns.takeLast(keepCount)
            val priorSummary = messages.lastOrNull {
                AgentSession.isConversationSummaryMessage(it.role, it.content)
            }?.let { AgentSession.extractConversationSummaryBody(it.content) }
                ?.takeIf { it.isNotBlank() }
                ?: session.summary

            val summary = try {
                if (worker != null) {
                    val historyDump = buildString {
                        if (priorSummary.isNotBlank()) {
                            appendLine("Previous summary: ${sanitizeTextForSummary(priorSummary)}")
                        }
                        toSummarize.forEach { msg ->
                            val cleanBody = sanitizeTextForSummary(msg.content())
                            if (cleanBody.isNotBlank() &&
                                !cleanBody.startsWith("The AI has requested to call tools") &&
                                !cleanBody.startsWith("Authorized tool execution finished with results:")
                            ) {
                                appendLine("${msg.role().name}: $cleanBody")
                            }
                        }
                    }.trim()

                    val summaryReq = LlmRequest.builder()
                        .addMessage(
                            ReveilaMessage.system(
                                "Summarize the conversation into 2-3 concise Markdown bullet points, preserving key facts, decisions, and pending questions. Return ONLY human-readable Markdown bullets — NEVER return JSON, tool call envelopes, or code fences."
                            )
                        )
                        .addMessage(ReveilaMessage.user(historyDump))
                        .build()

                    val rawSummary = worker.invoke(summaryReq).content?.trim()
                    val cleanSummary = rawSummary?.let { sanitizeTextForSummary(it) }?.takeIf { it.isNotBlank() }
                    cleanSummary ?: buildExtractiveSpaceSummary(session.spaceName, toSummarize, priorSummary)
                } else {
                    buildExtractiveSpaceSummary(session.spaceName, toSummarize, priorSummary)
                }
            } catch (e: Exception) {
                logger.warning("[Fabric] LLM summary fallback used while compacting window: ${e.message}")
                buildExtractiveSpaceSummary(session.spaceName, toSummarize, priorSummary)
            }

            val firstRecentTs = recentTurns.firstOrNull()?.timestamp?.takeIf { it > 0L }
                ?: PlatformSystem.currentTimeMillis()
            val summaryTs = (firstRecentTs - 1L).coerceAtLeast(1L)

            session.updateSummary(summary)
            session.chatMemory.clear()
            session.addMessage(
                ReveilaMessage.system(
                    "${AgentSession.CONVERSATION_SUMMARY_PREFIX} $summary",
                    spaceId = spaceId,
                    timestamp = summaryTs
                )
            )
            recentTurns.forEach { session.addMessage(it.withSpace(spaceId)) }
            sessionManager?.saveSession(session.sessionId, session)
            try {
                ChatHistoryArchiveWriter.archiveCompactedTurns(
                    properties = context?.properties,
                    spaceId = spaceId,
                    spaceName = session.spaceName,
                    sessionId = session.sessionId,
                    compactedSummary = summary,
                    compactedTurns = toSummarize
                )
            } catch (archiveEx: Exception) {
                logger.warning("[Fabric] Failed to archive compacted chat turns: ${archiveEx.message}")
            }
            logger.info("[Fabric] Sliding context window activated for space [$spaceId]: compacted ${toSummarize.size} turns into concise summary (kept ${recentTurns.size} recent turns).")
        }
    }

    /**
     * Builds the Space-filtered chat history and Space summary for LLM prompt context.
     * Guarantees messages from unrelated Spaces are excluded before invoking any LLM.
     */
    private fun buildSpaceContextMessages(session: AgentSession, maxHistoryMessages: Int): List<ReveilaMessage> {
        val targetSpaceId = AgentSession.normalizeSpaceId(session.spaceId)
        val priorSpaceMessages = sessionManager
            ?.getFilteredMessagesForSpace(
                spaceId = targetSpaceId,
                excludeSessionId = session.sessionId,
                maxMessages = 40
            )
            .orEmpty()
        val currentSpaceMessages = session.messagesForSpace(targetSpaceId)

        val allFiltered = (priorSpaceMessages + currentSpaceMessages).filter { msg ->
            msg.spaceId == null || AgentSession.normalizeSpaceId(msg.spaceId) == targetSpaceId
        }

        val persistedSummary = session.summary.ifBlank {
            sessionManager?.getSpaceSummary(targetSpaceId).orEmpty()
        }

        val conversationTurns = allFiltered.filterNot { msg ->
            AgentSession.isConversationSummaryMessage(msg.role, msg.content)
        }
        val embeddedSummary = allFiltered.lastOrNull { msg ->
            AgentSession.isConversationSummaryMessage(msg.role, msg.content)
        }?.content

        val recentTurns: List<ReveilaMessage>
        val olderTurns: List<ReveilaMessage>
        if (maxHistoryMessages > 0 && conversationTurns.size > maxHistoryMessages) {
            olderTurns = conversationTurns.dropLast(maxHistoryMessages)
            recentTurns = conversationTurns.takeLast(maxHistoryMessages)
        } else {
            olderTurns = emptyList()
            recentTurns = conversationTurns
        }

        val effectiveSummary = when {
            persistedSummary.isNotBlank() -> persistedSummary
            !embeddedSummary.isNullOrBlank() -> AgentSession.extractConversationSummaryBody(embeddedSummary)
            olderTurns.isNotEmpty() -> buildExtractiveSpaceSummary(session.spaceName, olderTurns)
            else -> ""
        }

        val contextMessages = mutableListOf<ReveilaMessage>()
        if (effectiveSummary.isNotBlank()) {
            contextMessages.add(
                ReveilaMessage.system(
                    "Earlier conversation summary for Space '${session.spaceName}': $effectiveSummary",
                    spaceId = targetSpaceId
                )
            )
        }
        contextMessages.addAll(recentTurns.map { sanitizeMessageForLlmContext(it) })
        return contextMessages
    }

    /**
     * Strips persisted UI-only metadata (such as `toolAttachment` / `rawHtml`) from historical
     * assistant messages before sending conversation history back to an LLM provider.
     */
    private fun sanitizeMessageForLlmContext(msg: ReveilaMessage): ReveilaMessage {
        if (msg.role != LlmRole.ASSISTANT) return msg
        val raw = msg.content.trim()
        if (!raw.contains("\"toolAttachment\"") && raw.length <= 4000) return msg
        val clean = sanitizeTextForSummary(raw)
        return if (clean.isNotBlank()) msg.copy(content = clean.take(4000)) else msg.copy(content = raw.take(4000))
    }

    private fun sanitizeTextForSummary(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json").removePrefix("```markdown").removePrefix("```")
            .removeSuffix("```").trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val parsed = JsonUtil.parseJsonStringToMap(trimmed)
                val extracted = (parsed["summary"] as? String)?.takeIf { it.isNotBlank() && it != "null" }
                    ?: (parsed["answer"] as? String)?.takeIf { it.isNotBlank() && it != "null" }
                    ?: (parsed["result"] as? String)?.takeIf { it.isNotBlank() && it != "null" }
                    ?: (parsed["reasoning"] as? String)?.takeIf { it.isNotBlank() && it != "null" }
                if (!extracted.isNullOrBlank()) {
                    return extracted.trim()
                }
            } catch (_: Exception) {
            }
        }
        return trimmed
    }

    private fun buildExtractiveSpaceSummary(
        spaceName: String,
        olderTurns: List<ReveilaMessage>,
        priorSummary: String = ""
    ): String {
        val bullets = mutableListOf<String>()
        val cleanPrior = sanitizeTextForSummary(priorSummary)
        if (cleanPrior.isNotBlank()) {
            bullets.add(cleanPrior)
        }
        olderTurns
            .filter { it.role == LlmRole.USER || it.role == LlmRole.ASSISTANT }
            .takeLast(6)
            .forEach { msg ->
                val clean = sanitizeTextForSummary(msg.content)
                if (clean.isNotBlank() &&
                    !clean.startsWith("The AI has requested to call tools") &&
                    !clean.startsWith("Authorized tool execution finished with results:")
                ) {
                    val prefix = if (msg.role == LlmRole.USER) "**User:**" else "**Aegin:**"
                    val clipped = clean.replace(Regex("\\s+"), " ").trim().take(160)
                    bullets.add("- $prefix $clipped")
                }
            }
        return bullets.joinToString("\n").ifBlank { "Continued session in $spaceName" }
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
        metaInfo["spanId"] = child.spanId
        metaInfo["parentSpanId"] = child.parentSpanId
        metaInfo["actorId"] = child.actorId
        metaInfo["actorType"] = child.actorType
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

    private fun tryHandleTaskCreationIntent(session: AgentSession, intent: String): Map<String, Any?>? {
        val lower = intent.lowercase().trim()
        val hasCreateVerb = lower.contains("create ") || lower.contains("schedule ") || lower.contains("add ") || lower.contains("set up ")
        val hasTaskNoun = lower.contains("task") || lower.contains("repeating") || lower.contains("recurring") || lower.contains("daily") || lower.contains("hourly") || lower.contains("weekly")
        if (!hasCreateVerb || !hasTaskNoun || !lower.contains("task")) {
            return null
        }

        val frequency = when {
            lower.contains("hourly") || lower.contains("every hour") -> "hourly"
            lower.contains("weekly") || lower.contains("every week") -> "weekly"
            lower.contains("15 min") || lower.contains("every 15") -> "every 15m"
            else -> "daily"
        }

        // Extract schedule time like "8:00 AM", "08:00", "9pm", etc.
        val timeRegex = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)?\b""", RegexOption.IGNORE_CASE)
        var scheduleTime = "08:00"
        val afterAt = lower.substringAfter(" at ", "")
        val timeMatch = (if (afterAt.isNotBlank()) timeRegex.find(afterAt) else null) ?: timeRegex.find(lower)
        if (timeMatch != null) {
            var hour = timeMatch.groupValues[1].toIntOrNull() ?: 8
            val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
            val ampm = timeMatch.groupValues[3].lowercase()
            if (ampm.startsWith("p") && hour in 1..11) hour += 12
            if (ampm.startsWith("a") && hour == 12) hour = 0
            if (hour in 0..23 && minute in 0..59) {
                scheduleTime = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
            }
        }

        // Extract core action/prompt after "task to ..." or "task: ..."
        var actionPrompt = intent
            .replace(Regex("""^(?:please\s+)?(?:create|schedule|add|set\s+up)\s+(?:a\s+|an\s+)?(?:daily|hourly|weekly|repeating|recurring|\s)*task\s+(?:to\s+|that\s+|for\s+|:\s*)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+at\s+\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)?\b.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.', '!')
        if (actionPrompt.isBlank()) {
            actionPrompt = intent.trim()
        }

        val titleWords = actionPrompt
            .replace(Regex("""^(?:show\s+me\s+(?:the\s+)?|find\s+(?:the\s+)?|check\s+(?:the\s+)?|summarize\s+(?:the\s+)?)""", RegexOption.IGNORE_CASE), "")
            .split(Regex("""\s+"""))
            .take(5)
            .joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
            .ifBlank { "Scheduled Task" }

        val requiresApproval = lower.contains("approval") || lower.contains("confirm")

        return try {
            val autoProxy = context?.getProxy("AutonomousAgent")
            val autoAgent = (autoProxy?.getInstance() as? AutonomousAgent) ?: autoProxy as? AutonomousAgent
            val createdJson = if (autoAgent != null) {
                autoAgent.createTask(
                    title = titleWords,
                    prompt = actionPrompt,
                    frequency = frequency,
                    scheduleTime = scheduleTime,
                    requiresApproval = requiresApproval
                )
            } else {
                // Direct fallback if AutonomousAgent proxy is not yet resolved
                val fs = PlatformFileSystem()
                val home = context?.properties?.getProperty("system.home") ?: "."
                val tasksDir = fs.resolve(home, "tasks")
                if (!fs.exists(tasksDir)) fs.createDirectories(tasksDir)
                val slug = titleWords.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "scheduled-task" }
                val filePath = fs.resolve(tasksDir, "$slug.json")
                val map = linkedMapOf<String, Any?>(
                    "taskId" to slug,
                    "title" to titleWords,
                    "intent" to "autonomous.task",
                    "prompt" to actionPrompt,
                    "frequency" to frequency,
                    "scheduleTime" to scheduleTime,
                    "enabled" to true,
                    "status" to if (requiresApproval) "WAITING_APPROVAL" else "ACTIVE",
                    "requiresApproval" to requiresApproval,
                    "createdAt" to PlatformSystem.currentTimeMillis(),
                    "lastRunAt" to 0L,
                    "lastResult" to "Scheduled ($frequency at $scheduleTime)."
                )
                fs.writeText(filePath, JsonUtil.toPrettyJsonString(map), append = false)
                JsonUtil.toJsonString(map)
            }

            val createdMap = JsonUtil.parseJsonStringToMap(createdJson)
            val taskId = createdMap["taskId"]?.toString() ?: "scheduled-task"
            val statusStr = createdMap["status"]?.toString() ?: "ACTIVE"

            session.addMessage(ReveilaMessage.user(intent, spaceId = session.spaceId))
            val summaryMsg = buildString {
                appendLine("Created repeating task in \${REVEILA_HOME}/tasks/$taskId.json:")
                appendLine("• Title: $titleWords")
                appendLine("• ID: $taskId")
                appendLine("• Schedule: ${frequency.uppercase()} at $scheduleTime")
                appendLine("• Status: $statusStr")
                appendLine("• Prompt: \"$actionPrompt\"")
                append("Open the Tasks tab to monitor, run, edit, approve, or stop this task.")
            }
            session.addMessage(ReveilaMessage.assistant(summaryMsg, spaceId = session.spaceId))
            sessionManager?.saveSession(session.sessionId, session)
            recordAuditLog("TASK_CREATED_FROM_CHAT", "Task [$taskId] ($titleWords) scheduled $frequency at $scheduleTime")

            mapOf(
                STATUS to Constants.AI_STATUS_COMPLETED,
                REASONING to "Created task definition $taskId.json in tasks folder",
                RESULT to summaryMsg,
                CONFIDENCE_SCORE to 0.99
            )
        } catch (e: Exception) {
            logger.warning("Failed to create task from chat intent: ${e.message}")
            null
        }
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
            val traceCtx = TraceContextHolder.getContext()
            val traceId = traceCtx?.traceId ?: Uuid.randomUuid()
            val spanId = Uuid.randomUuid()
            val parentSpanId = traceCtx?.spanId
            val actorId = traceCtx?.actorId.orEmpty()
            val actorType = traceCtx?.actorType ?: "SYSTEM"
            val tenantId = context?.properties?.getProperty("tenant-id", "default-tenant") ?: "default-tenant"

            val fr = flightRecorder
            if (fr != null) {
                val plugin = Plugin(
                    sessionId = traceId,
                    name = "AgenticFabric",
                    tenantId = tenantId,
                    traceId = traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    actorId = actorId,
                    actorType = actorType
                )
                fr.recordCommandEvent(plugin, action, details ?: "", actorId, actorType)
                return
            }

            val ds = dataService ?: run {
                val p = context?.getProxy("DataService")
                (p?.getInstance() as? DataService) ?: p as? DataService
            }
            if (ds != null) {
                val ts = PlatformSystem.currentTimeMillis()
                val recordId = Uuid.randomUuid()
                val log = mutableMapOf<String, Any?>()
                log["recordId"] = recordId
                log["traceId"] = traceId
                log["spanId"] = spanId
                if (!parentSpanId.isNullOrBlank()) log["parentSpanId"] = parentSpanId
                log["actorId"] = actorId
                log["actorType"] = actorType
                log["pluginName"] = "AgenticFabric"
                log["tenantId"] = tenantId
                log["action"] = action
                log["details"] = details ?: ""
                log["timestamp"] = ts
                log["prevEventHash"] = ""
                log["eventHash"] = CommandChainAudit.computeEventHash(
                    prevEventHash = "",
                    traceId = traceId,
                    spanId = spanId,
                    parentSpanId = parentSpanId,
                    actorId = actorId,
                    actorType = actorType,
                    action = action,
                    details = details ?: "",
                    timestamp = ts
                )
                ds.save("AuditLog", log)
            }
        } catch (e: Exception) {
            logger.warning("Failed to write AuditLog: ${e.message}")
        }
    }

    @Throws(Exception::class)
    private fun handleToolCall(session: AgentSession, toolCallObj: Map<String, Any?>, intent: String): String {
        val traceCtx = TraceContextHolder.getContext()
        val bridgeArgs = mutableMapOf<String, Any?>()
        bridgeArgs[AgentSession.ID] = session.sessionId
        bridgeArgs[AgentSession.THOUGHT] = toolCallObj
        bridgeArgs["traceId"] = session.parentTraceId ?: traceCtx?.traceId ?: Uuid.randomUuid()
        bridgeArgs["parentSpanId"] = traceCtx?.spanId
        bridgeArgs["actorId"] = traceCtx?.actorId.orEmpty()
        bridgeArgs["actorType"] = traceCtx?.actorType ?: "SYSTEM"

        var pluginId = toolCoordinator?.extractToolName(toolCallObj).orEmpty()
        if (pluginId.isEmpty()) {
            pluginId = toolCallObj["plugin"]?.toString()
                ?: toolCallObj["name"]?.toString()
                ?: ""
        }

        val toolCall = ToolCall()
        toolCall.functionName = pluginId

        @Suppress("UNCHECKED_CAST")
        val argsMap = toolCoordinator?.extractArguments(toolCallObj)
            ?: ((toolCallObj["arguments"] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()).also { m ->
                if (toolCallObj.containsKey("method") && !m.containsKey("method")) {
                    m["method"] = toolCallObj["method"]
                }
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

    private fun unwrapProvider(provider: LlmProvider?): LlmProvider? {
        var current = provider
        while (current is TrackedLlmProvider) {
            current = current.unwrapDelegate()
        }
        return current
    }

    @Throws(LlmException::class)
    private fun askAi(
        session: AgentSession,
        userPrompt: String,
        preferredProviderName: String? = null,
        recordUserTurn: Boolean = true
    ): String {
        val worker = if (!preferredProviderName.isNullOrBlank()) {
            llmFactory?.getProvider(preferredProviderName) ?: llmFactory?.getActiveProvider()
        } else {
            llmFactory?.getActiveProvider()
        }
        if (worker == null) {
            val msg = "System Error: No active LLM Provider found."
            logger.severe(msg)
            return msg
        }

        val rawWorker = unwrapProvider(worker)
        val isOnDevice = rawWorker is LocalLlamaProvider || worker.getName().contains("On-Device", ignoreCase = true)
        val activeModel = (rawWorker as? BaseLlmProvider)?.model
            ?.takeIf { it.isNotBlank() }
            ?: if (isOnDevice) {
                context?.properties?.getProperty("ai.llm.model.name")?.takeIf { it.isNotBlank() }
                    ?: LocalLlamaProvider.nativeDelegate?.getLoadedModelPath()
            } else {
                null
            }
        val systemHome = context?.properties?.getProperty("system.home")
        val extraRoutingKeywords = metadataRegistry?.getLocalRoutingKeywords().orEmpty()
        val exposeBuiltinTools = !isOnDevice ||
            !PromptTemplateRegistry.isSmallLocalModel(activeModel, systemHome) ||
            (toolCoordinator?.shouldRouteToAgenticLoop(userPrompt, extraRoutingKeywords) == true)
        val builtinNames = toolCoordinator?.getToolNames() ?: emptySet()

        val discoveredTools = LinkedHashMap<String, LlmTool>()
        val provider = toolProvider
        if (provider != null) {
            provider.provideTools(userPrompt).forEach { t ->
                val toolName = t.name ?: return@forEach
                if (!exposeBuiltinTools && toolName in builtinNames) return@forEach
                val manifest = metadataRegistry?.getManifest(toolName)
                if (manifest != null && toolCoordinator?.isManifestAllowedForTarget(manifest, isOnDevice, activeModel, systemHome) == false) {
                    return@forEach
                }
                discoveredTools[toolName] = t
            }
        } else {
            val mcpData = metadataRegistry?.exportToMCP()
            @Suppress("UNCHECKED_CAST")
            val mcpTools = mcpData?.get("tools") as? List<Map<String, Any?>>
            if (mcpTools != null) {
                for (toolMap in mcpTools) {
                    val toolName = toolMap["name"] as? String ?: continue
                    if (!exposeBuiltinTools && toolName in builtinNames) continue
                    val manifest = metadataRegistry?.getManifest(toolName)
                    if (manifest != null && toolCoordinator?.isManifestAllowedForTarget(manifest, isOnDevice, activeModel, systemHome) == false) {
                        continue
                    }
                    val tool = LlmTool()
                    tool.name = toolName
                    tool.description = toolMap["description"] as? String
                    val inputSchema = toolMap["inputSchema"]
                    if (inputSchema is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST")
                        tool.parameterSchema = inputSchema as Map<String, Any?>
                    }
                    discoveredTools[toolName] = tool
                }
            }
        }
        if (exposeBuiltinTools) {
            val coordinator = toolCoordinator
            coordinator?.getAllLlmTools()?.forEach { t ->
                val toolName = t.name ?: return@forEach
                val manifest = metadataRegistry?.getManifest(toolName)
                if (manifest == null || coordinator.isManifestAllowedForTarget(manifest, isOnDevice, activeModel, systemHome)) {
                    if (!discoveredTools.containsKey(toolName)) {
                        discoveredTools[toolName] = t
                    }
                }
            }
        }
        val rawTools: List<LlmTool> = discoveredTools.values.toList()
        val tools: List<LlmTool> = toolCoordinator?.filterToolsForIntent(userPrompt, rawTools) ?: rawTools

        val promptMode = PromptTemplateRegistry.resolvePromptMode(
            isLocalProvider = isOnDevice,
            modelName = activeModel,
            hasTools = tools.isNotEmpty(),
            hasGrammar = false,
            systemHome = systemHome
        )

        val renderedPrompt = Prompt.buildSystemPrompt(
            role = "Reveila Sovereign AI Agent (Space: ${session.spaceName})",
            spaceName = session.spaceName,
            tools = if (promptMode == PromptMode.AGENTIC_JSON) tools else emptyList(),
            vaultContext = searchKnowledgeVault(userPrompt),
            constraints = "Always follow governance policy boundaries and preserve confidentiality.",
            modelName = activeModel,
            systemHome = systemHome,
            mode = promptMode
        )
        val systemInstructions = renderedPrompt.systemPrompt

        val requestBuilder = LlmRequest.builder()
            .tools(if (promptMode == PromptMode.AGENTIC_JSON) tools else emptyList())
            .addMetadata("prompt_mode", promptMode.name)
        if (!systemHome.isNullOrBlank()) {
            requestBuilder.addMetadata("system_home", systemHome)
        }
        requestBuilder.addMessage(ReveilaMessage.system(systemInstructions))

        if (isDebug) {
            logger.info("System Prompt used for request (template=${renderedPrompt.resolvedTemplate.templateId}, mode=$promptMode): $systemInstructions")
        }

        val maxWindow = renderedPrompt.resolvedTemplate.maxHistoryMessages
            ?: if (isOnDevice) 6 else (orchestrationService?.getMaxMessages() ?: 20)
        val filteredSpaceHistory = buildSpaceContextMessages(session, maxHistoryMessages = maxWindow)

        for (msg in filteredSpaceHistory) {
            requestBuilder.addMessage(msg)
        }

        val currentUserMsg = ReveilaMessage.user(userPrompt, spaceId = session.spaceId)
        if (recordUserTurn) {
            session.addMessage(currentUserMsg)
        }
        requestBuilder.addMessage(currentUserMsg)

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
                if (response != null) {
                    session.addMessage(ReveilaMessage.assistant(response, spaceId = session.spaceId))
                    sessionManager?.saveSession(session.sessionId, session)
                }
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
