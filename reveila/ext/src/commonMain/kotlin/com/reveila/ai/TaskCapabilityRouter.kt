package com.reveila.ai

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

/**
 * Closed, predefined 6-category routing taxonomy tied directly to resource and capability boundaries.
 */
enum class TaskRoutingCategory(val defaultRoute: String, val description: String) {
    LOCAL_TEXT_TRANSFORM(
        defaultRoute = "LOCAL",
        description = "Operates strictly on text already provided in prompt or local Reference Documents."
    ),
    LOCAL_STRUCTURED_EXTRACT(
        defaultRoute = "LOCAL",
        description = "Template-driven entity/field extraction, tagging, or schema mapping from local input."
    ),
    LOCAL_DEVICE_ACTION(
        defaultRoute = "LOCAL",
        description = "Single-step deterministic local tool, setting, or local vector store query."
    ),
    CLOUD_LIVE_RETRIEVAL(
        defaultRoute = "CLOUD",
        description = "Requires fresh, real-time, or post-cutoff external data (web search, news, weather, prices)."
    ),
    CLOUD_DEEP_REASONING(
        defaultRoute = "CLOUD",
        description = "Multi-step planning, code generation, long-context synthesis, or complex judgment."
    ),
    CLOUD_MULTI_STEP_AGENT(
        defaultRoute = "CLOUD",
        description = "Autonomous multi-turn tool orchestration or cross-app/web automation loops."
    );

    val isCloudRoute: Boolean
        get() = defaultRoute == "CLOUD"

    companion object {
        /**
         * Parses a category string into one of the 6 predefined enum values.
         * Safety rule: Any unrecognized or malformed output defaults safely to [CLOUD_DEEP_REASONING].
         */
        fun fromRawOrSafeDefault(raw: String?): TaskRoutingCategory {
            if (raw.isNullOrBlank()) return CLOUD_DEEP_REASONING
            val normalized = raw.trim().uppercase()
            return entries.firstOrNull { it.name == normalized } ?: when (normalized) {
                "SUMMARIZE", "REWRITE" -> LOCAL_TEXT_TRANSFORM
                "EXTRACT", "CLASSIFY" -> LOCAL_STRUCTURED_EXTRACT
                "WEB_SEARCH" -> CLOUD_LIVE_RETRIEVAL
                "MULTI_STEP_AGENT" -> CLOUD_MULTI_STEP_AGENT
                else -> CLOUD_DEEP_REASONING
            }
        }
    }
}

/**
 * Result of Reveila's 3-tier TaskCapabilityRouter resolution:
 * 1. Explicit `executionTarget` override (`"local" | "cloud" | "auto"`, default `"auto"`).
 * 2. Static `intent` registry O(1) short-circuit (`configs/capabilities.json`).
 * 3. Local SLM GBNF Preprocessor into the closed 6-category [TaskRoutingCategory] taxonomy.
 */
data class TaskRoutingAssessment(
    val intent: String,
    val executionTarget: String,
    val requiresLiveInternet: Boolean,
    val requiresComplexReasoning: Boolean,
    val localCapable: Boolean,
    val category: TaskRoutingCategory,
    val routeToCloud: Boolean,
    val routingStage: String,
    val reason: String,
    val capabilityConflictError: String? = null
) {
    val taskCategory: String
        get() = category.name

    val targetRoute: String
        get() = if (routeToCloud) "CLOUD" else "LOCAL"

    val hasCapabilityConflict: Boolean
        get() = !capabilityConflictError.isNullOrBlank()

    fun toCompactBadge(): String =
        if (routeToCloud) "☁️ ${category.name}" else "📱 ${category.name}"
}

/**
 * Capability-and-Tool Router implementing the 3-step resolution order:
 * 1. Explicit Override (`executionTarget == "local"` or `"cloud"`): Honors immediately without waking the SLM,
 *    failing fast if `"local"` is forced on a task requiring live internet (`CLOUD_LIVE_RETRIEVAL`).
 * 2. Static `intent` Registry: Maps known intents like `news.search` directly to `CLOUD_LIVE_RETRIEVAL` in O(1) time.
 * 3. Local SLM Preprocessor (`executionTarget == "auto"` + generic/auto intent): Classifies into the closed
 *    6-category [TaskRoutingCategory] taxonomy using GBNF grammar enforcement.
 */
object TaskCapabilityRouter {

    /**
     * Strict GBNF grammar passed to `llama_sampler_init_grammar` in `llama.cpp` JNI / `llama-server`
     * enforcing the closed 6-category taxonomy.
     */
    val TASK_ROUTING_GBNF_GRAMMAR: String = """
        root ::= "{" ws "\"requires_live_internet\":" ws boolean "," ws "\"requires_complex_reasoning\":" ws boolean "," ws "\"local_capable\":" ws boolean "," ws "\"task_category\":" ws category ws "}"
        boolean ::= "true" | "false"
        category ::= "\"LOCAL_TEXT_TRANSFORM\"" | "\"LOCAL_STRUCTURED_EXTRACT\"" | "\"LOCAL_DEVICE_ACTION\"" | "\"CLOUD_LIVE_RETRIEVAL\"" | "\"CLOUD_DEEP_REASONING\"" | "\"CLOUD_MULTI_STEP_AGENT\""
        ws ::= [ \t\n]*
    """.trimIndent()

    private data class IntentCapabilityRule(
        val requiredCapabilities: Set<String>,
        val category: TaskRoutingCategory,
        val reason: String
    )

    private val defaultIntentRegistry: Map<String, IntentCapabilityRule> = mapOf(
        "news.search" to IntentCapabilityRule(
            requiredCapabilities = setOf("web_search", "live_data"),
            category = TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
            reason = "Static intent 'news.search' mapped to CLOUD_LIVE_RETRIEVAL in O(1) time."
        ),
        "web.search" to IntentCapabilityRule(
            requiredCapabilities = setOf("web_search", "live_data"),
            category = TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
            reason = "Static intent 'web.search' mapped to CLOUD_LIVE_RETRIEVAL in O(1) time."
        ),
        "market.watch" to IntentCapabilityRule(
            requiredCapabilities = setOf("web_search", "live_data"),
            category = TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
            reason = "Static intent 'market.watch' mapped to CLOUD_LIVE_RETRIEVAL in O(1) time."
        ),
        "cloud.research" to IntentCapabilityRule(
            requiredCapabilities = setOf("deep_reasoning"),
            category = TaskRoutingCategory.CLOUD_DEEP_REASONING,
            reason = "Static intent 'cloud.research' mapped to CLOUD_DEEP_REASONING in O(1) time."
        ),
        "agent.workflow" to IntentCapabilityRule(
            requiredCapabilities = setOf("multi_step_agent", "web_search"),
            category = TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT,
            reason = "Static intent 'agent.workflow' mapped to CLOUD_MULTI_STEP_AGENT in O(1) time."
        ),
        "vault.summarize" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_text"),
            category = TaskRoutingCategory.LOCAL_TEXT_TRANSFORM,
            reason = "Static intent 'vault.summarize' mapped to LOCAL_TEXT_TRANSFORM in O(1) time."
        ),
        "local.summarize" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_text"),
            category = TaskRoutingCategory.LOCAL_TEXT_TRANSFORM,
            reason = "Static intent 'local.summarize' mapped to LOCAL_TEXT_TRANSFORM in O(1) time."
        ),
        "local.rewrite" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_text"),
            category = TaskRoutingCategory.LOCAL_TEXT_TRANSFORM,
            reason = "Static intent 'local.rewrite' mapped to LOCAL_TEXT_TRANSFORM in O(1) time."
        ),
        "local.extract" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_text"),
            category = TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT,
            reason = "Static intent 'local.extract' mapped to LOCAL_STRUCTURED_EXTRACT in O(1) time."
        ),
        "local.classify" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_text"),
            category = TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT,
            reason = "Static intent 'local.classify' mapped to LOCAL_STRUCTURED_EXTRACT in O(1) time."
        ),
        "device.action" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_device"),
            category = TaskRoutingCategory.LOCAL_DEVICE_ACTION,
            reason = "Static intent 'device.action' mapped to LOCAL_DEVICE_ACTION in O(1) time."
        ),
        "vault.query" to IntentCapabilityRule(
            requiredCapabilities = setOf("local_device"),
            category = TaskRoutingCategory.LOCAL_DEVICE_ACTION,
            reason = "Static intent 'vault.query' mapped to LOCAL_DEVICE_ACTION in O(1) time."
        )
    )

    private val liveInternetKeywords = listOf(
        "latest", "today", "tonight", "breaking", "current events", "news",
        "weather", "forecast", "stock price", "crypto price", "bitcoin price",
        "live score", "right now", "this morning", "recent news", "web search",
        "search the web", "online", "google"
    )

    private val deepReasoningKeywords = listOf(
        "implement in rust", "implement in c++", "write a full",
        "architectural proof", "theorem", "deep research", "end-to-end codebase",
        "compile and debug", "debug a stack trace", "financial forecast model",
        "adversarial simulation", "multi-page contract"
    )

    private val multiStepAgentKeywords = listOf(
        "multi-step", "across apps", "research three vendors", "compile a comparison report",
        "autonomous workflow", "browse and compare", "orchestrate"
    )

    fun normalizeExecutionTarget(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "local" -> "local"
            "cloud" -> "cloud"
            else -> "auto"
        }
    }

    /**
     * Evaluates a task definition (`intent`, `prompt`, `executionTarget`) using the 3-tier resolution order.
     */
    fun assessTask(
        intent: String,
        prompt: String,
        executionTarget: String = "auto",
        systemHome: String? = null,
        localProvider: LlmProvider? = null
    ): TaskRoutingAssessment {
        val normIntent = intent.trim().lowercase().ifBlank { "auto" }
        val normTarget = normalizeExecutionTarget(executionTarget)
        val registry = loadIntentRegistry(systemHome)
        val matchedRule = registry[normIntent]
        val promptNeedsLiveWeb = containsLiveInternetSignal(prompt) ||
            matchedRule?.category == TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL

        // ─────────────────────────────────────────────────────────────────────
        // STEP 1: EXPLICIT OVERRIDE (`executionTarget == "local"` or `"cloud"`)
        // Honors immediately without running the SLM preprocessor.
        // Fails fast if `"local"` is forced on a task needing live internet.
        // ─────────────────────────────────────────────────────────────────────
        if (normTarget == "local") {
            if (promptNeedsLiveWeb) {
                val conflictMsg =
                    "Capability Conflict: Task requires live internet data (CLOUD_LIVE_RETRIEVAL), which cannot be executed under forced 'executionTarget: local' offline mode."
                return TaskRoutingAssessment(
                    intent = normIntent,
                    executionTarget = "local",
                    requiresLiveInternet = true,
                    requiresComplexReasoning = false,
                    localCapable = false,
                    category = TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
                    routeToCloud = false,
                    routingStage = "EXPLICIT_OVERRIDE_CONFLICT",
                    reason = conflictMsg,
                    capabilityConflictError = conflictMsg
                )
            }
            val localCat = matchedRule?.category?.takeIf { !it.isCloudRoute }
                ?: inferLocalCategory(prompt)
            return TaskRoutingAssessment(
                intent = normIntent,
                executionTarget = "local",
                requiresLiveInternet = false,
                requiresComplexReasoning = false,
                localCapable = true,
                category = localCat,
                routeToCloud = false,
                routingStage = "EXPLICIT_OVERRIDE_LOCAL",
                reason = "Explicit executionTarget='local' override -> ${localCat.name} (offline guarantee)."
            )
        }

        if (normTarget == "cloud") {
            val cloudCat = matchedRule?.category?.takeIf { it.isCloudRoute }
                ?: inferCloudCategory(prompt, promptNeedsLiveWeb)
            return TaskRoutingAssessment(
                intent = normIntent,
                executionTarget = "cloud",
                requiresLiveInternet = promptNeedsLiveWeb,
                requiresComplexReasoning = cloudCat != TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
                localCapable = false,
                category = cloudCat,
                routeToCloud = true,
                routingStage = "EXPLICIT_OVERRIDE_CLOUD",
                reason = "Explicit executionTarget='cloud' override -> ${cloudCat.name}."
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 2: STATIC `intent` REGISTRY O(1) SHORT-CIRCUIT
        // Known intents like `news.search` map directly to `CLOUD_LIVE_RETRIEVAL`
        // without waking the local SLM.
        // ─────────────────────────────────────────────────────────────────────
        if (matchedRule != null) {
            val effectiveCategory = if (!matchedRule.category.isCloudRoute && promptNeedsLiveWeb) {
                TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL
            } else {
                matchedRule.category
            }
            val routeCloud = effectiveCategory.isCloudRoute
            return TaskRoutingAssessment(
                intent = normIntent,
                executionTarget = "auto",
                requiresLiveInternet = effectiveCategory == TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL,
                requiresComplexReasoning = effectiveCategory in setOf(
                    TaskRoutingCategory.CLOUD_DEEP_REASONING,
                    TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT
                ),
                localCapable = !routeCloud,
                category = effectiveCategory,
                routeToCloud = routeCloud,
                routingStage = "STATIC_INTENT_REGISTRY",
                reason = if (effectiveCategory != matchedRule.category) {
                    "Prompt requires live internet data despite '$normIntent' intent; escalated to ${effectiveCategory.name}."
                } else {
                    matchedRule.reason
                }
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // STEP 3: LOCAL SLM PREPROCESSOR (`executionTarget == "auto"` + generic intent)
        // Classifies into the closed 6-category taxonomy with GBNF enforcement.
        // ─────────────────────────────────────────────────────────────────────
        val gbnfOutput = tryInvokeLocalPreprocessorLlm(normIntent, prompt, localProvider)
        val parsedLlmMap = if (!gbnfOutput.isNullOrBlank()) {
            try {
                val cleaned = JsonUtil.clean(gbnfOutput) ?: gbnfOutput
                JsonUtil.parseJsonStringToMap(cleaned)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val heuristicLiveWeb = containsLiveInternetSignal(prompt)
        val heuristicMultiStep = containsMultiStepAgentSignal(prompt)
        val heuristicDeep = containsDeepReasoningSignal(prompt)

        val llmCategory = if (parsedLlmMap != null) {
            // Safety rule: If preprocessor outputs anything outside the 6 enum values,
            // fromRawOrSafeDefault defaults safely to CLOUD_DEEP_REASONING.
            TaskRoutingCategory.fromRawOrSafeDefault(parsedLlmMap["task_category"]?.toString())
        } else {
            null
        }

        val requiresLiveInternet = (parsedLlmMap?.get("requires_live_internet") as? Boolean ?: false) ||
            heuristicLiveWeb ||
            llmCategory == TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL

        val requiresComplexReasoning = (parsedLlmMap?.get("requires_complex_reasoning") as? Boolean ?: false) ||
            heuristicDeep ||
            heuristicMultiStep ||
            llmCategory in setOf(TaskRoutingCategory.CLOUD_DEEP_REASONING, TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT)

        val resolvedCategory: TaskRoutingCategory = when {
            requiresLiveInternet -> TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL
            heuristicMultiStep || llmCategory == TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT ->
                TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT
            requiresComplexReasoning || llmCategory == TaskRoutingCategory.CLOUD_DEEP_REASONING ->
                TaskRoutingCategory.CLOUD_DEEP_REASONING
            llmCategory != null -> llmCategory
            else -> inferLocalCategory(prompt)
        }

        val llmLocalCapable = parsedLlmMap?.get("local_capable") as? Boolean ?: true
        val finalCategory = if (!llmLocalCapable && !resolvedCategory.isCloudRoute) {
            TaskRoutingCategory.CLOUD_DEEP_REASONING
        } else {
            resolvedCategory
        }

        val routeToCloud = finalCategory.isCloudRoute
        val stage = if (parsedLlmMap != null) "LOCAL_SLM_GBNF_ROUTER" else "ON_DEVICE_PREPROCESSOR"
        val reason = buildString {
            append("Preprocessor [$stage]: ")
            append("category=${finalCategory.name} -> ")
            append(if (routeToCloud) "Cloud LLM" else "On-Device Local")
        }

        return TaskRoutingAssessment(
            intent = normIntent,
            executionTarget = "auto",
            requiresLiveInternet = requiresLiveInternet,
            requiresComplexReasoning = requiresComplexReasoning,
            localCapable = !routeToCloud,
            category = finalCategory,
            routeToCloud = routeToCloud,
            routingStage = stage,
            reason = reason
        )
    }

    fun buildPreprocessorPrompt(intent: String, prompt: String): List<ReveilaMessage> {
        val systemText = """
            Classify the task requirements into JSON using the 6 predefined task_category values. Do not answer the prompt.
            Categories:
            - LOCAL_TEXT_TRANSFORM: Summarize, rewrite, or format text from prompt or local Reference Documents.
            - LOCAL_STRUCTURED_EXTRACT: Extract fields, entities, tags, or classify local input.
            - LOCAL_DEVICE_ACTION: Single-step local setting or local Reference Documents query.
            - CLOUD_LIVE_RETRIEVAL: Requires latest news, live web search, weather, or current prices.
            - CLOUD_DEEP_REASONING: Multi-step planning, coding, or complex analysis.
            - CLOUD_MULTI_STEP_AGENT: Autonomous multi-turn tool orchestration across web/apps.

            Task: {"intent": "news.search", "prompt": "Find the latest news about AI security and provide a summary."}
            Output: {"requires_live_internet": true, "requires_complex_reasoning": false, "local_capable": false, "task_category": "CLOUD_LIVE_RETRIEVAL"}

            Task: {"intent": "auto", "prompt": "Summarize the key points from my indexed reference documents."}
            Output: {"requires_live_internet": false, "requires_complex_reasoning": false, "local_capable": true, "task_category": "LOCAL_TEXT_TRANSFORM"}

            Task: {"intent": "auto", "prompt": "Extract all action items and dates from my meeting notes into JSON."}
            Output: {"requires_live_internet": false, "requires_complex_reasoning": false, "local_capable": true, "task_category": "LOCAL_STRUCTURED_EXTRACT"}
        """.trimIndent()

        val safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ")
        val userText = """Task: {"intent": "$intent", "prompt": "$safePrompt"}""" + "\nOutput:"

        return listOf(
            ReveilaMessage.system(systemText),
            ReveilaMessage.user(userText)
        )
    }

    private fun tryInvokeLocalPreprocessorLlm(
        intent: String,
        prompt: String,
        localProvider: LlmProvider?
    ): String? {
        val messages = buildPreprocessorPrompt(intent, prompt)

        // 1. Direct in-process native JNI GGUF delegate (if loaded in RAM)
        val delegate = LocalLlamaProvider.nativeDelegate
        if (delegate != null && delegate.isModelLoaded()) {
            return try {
                var unwrapped: LlmProvider? = localProvider
                while (unwrapped is TrackedLlmProvider) {
                    unwrapped = unwrapped.unwrapDelegate()
                }
                val modelName = (unwrapped as? BaseLlmProvider)?.model
                    ?.takeIf { it.isNotBlank() }
                    ?: delegate.getLoadedModelPath()
                    ?: "qwen2.5"
                val systemHome = (unwrapped as? BaseLlmProvider)?.context?.properties?.getProperty("system.home")
                val (formatted, stopToken) = LocalLlamaProvider.formatMessages(messages, modelName, systemHome)
                delegate.generateBlocking(
                    formattedPrompt = formatted,
                    stopToken = stopToken,
                    maxTokens = 56,
                    grammar = TASK_ROUTING_GBNF_GRAMMAR
                ).trim().takeIf { it.startsWith("{") }
            } catch (_: Exception) {
                null
            }
        }

        // 2. LocalLlamaProvider if configured with an active HTTP llama-server
        if (localProvider is LocalLlamaProvider) {
            val ep = localProvider.endpoint?.trim() ?: ""
            if (ep.startsWith("http://") || ep.startsWith("https://")) {
                return try {
                    val req = LlmRequest.builder()
                        .messages(messages)
                        .temperature(0.0)
                        .addMetadata("gbnf_grammar", TASK_ROUTING_GBNF_GRAMMAR)
                        .addMetadata("max_tokens", 56)
                        .build()
                    localProvider.invoke(req).content?.trim()?.takeIf { it.startsWith("{") }
                } catch (_: Exception) {
                    null
                }
            }
        }

        return null
    }

    private fun containsLiveInternetSignal(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return liveInternetKeywords.any { kw -> lower.contains(kw) }
    }

    private fun containsDeepReasoningSignal(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return deepReasoningKeywords.any { kw -> lower.contains(kw) }
    }

    private fun containsMultiStepAgentSignal(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return multiStepAgentKeywords.any { kw -> lower.contains(kw) }
    }

    private fun inferLocalCategory(prompt: String): TaskRoutingCategory {
        val lower = prompt.lowercase()
        return when {
            lower.contains("extract") ||
                lower.contains("classify") ||
                lower.contains("categorize") ||
                lower.contains("parse") ||
                lower.contains("tag") ||
                lower.contains("flag") ||
                lower.contains("audit") -> TaskRoutingCategory.LOCAL_STRUCTURED_EXTRACT
            lower.contains("set an alarm") ||
                lower.contains("toggle") ||
                lower.contains("query local") ||
                lower.contains("find file") ||
                lower.contains("list files") -> TaskRoutingCategory.LOCAL_DEVICE_ACTION
            else -> TaskRoutingCategory.LOCAL_TEXT_TRANSFORM
        }
    }

    private fun inferCloudCategory(prompt: String, needsLiveWeb: Boolean): TaskRoutingCategory {
        if (needsLiveWeb) return TaskRoutingCategory.CLOUD_LIVE_RETRIEVAL
        if (containsMultiStepAgentSignal(prompt)) return TaskRoutingCategory.CLOUD_MULTI_STEP_AGENT
        return TaskRoutingCategory.CLOUD_DEEP_REASONING
    }

    private fun loadIntentRegistry(systemHome: String?): Map<String, IntentCapabilityRule> {
        if (systemHome.isNullOrBlank()) return defaultIntentRegistry
        return try {
            val fs = PlatformFileSystem()
            val path = fs.resolve(systemHome, "configs/capabilities.json")
            if (!fs.exists(path)) return defaultIntentRegistry
            val root = JsonUtil.parseJsonStringToMap(fs.readText(path))
            @Suppress("UNCHECKED_CAST")
            val intentsMap = root["intents"] as? Map<String, Map<String, Any?>> ?: return defaultIntentRegistry
            val merged = defaultIntentRegistry.toMutableMap()
            for ((k, v) in intentsMap) {
                val route = v["route"]?.toString() ?: "PREPROCESSOR"
                val rawCat = v["taskCategory"]?.toString() ?: "AUTO"
                if (route.equals("PREPROCESSOR", ignoreCase = true) || rawCat.equals("AUTO", ignoreCase = true)) {
                    continue
                }
                @Suppress("UNCHECKED_CAST")
                val caps = (v["requiredCapabilities"] as? List<*>)
                    ?.mapNotNull { it?.toString() }
                    ?.toSet()
                    ?: emptySet()
                val category = TaskRoutingCategory.fromRawOrSafeDefault(rawCat)
                val reason = v["reason"]?.toString() ?: "Static intent '$k' mapped to ${category.name} in O(1) time."
                merged[k.lowercase()] = IntentCapabilityRule(caps, category, reason)
            }
            merged
        } catch (_: Exception) {
            defaultIntentRegistry
        }
    }
}
