package com.reveila.aegin.bridge

/**
 * Centralized UI terminology mapper that translates internal codebase tokens,
 * enum constants, key-value expressions, and backend telemetry into clean,
 * user-friendly labels across all Aegin screens.
 *
 * Strategy:
 * 1. Exact lookup tables per domain (status, frequency, routing target, capability,
 *    audit action, download phase, hardware tier, model profile, template category).
 * 2. Pattern normalization for key=value tokens (e.g., "status=completed" -> "Completed").
 * 3. Deterministic [humanizeCode] fallback for any unrecognized SNAKE_CASE or kebab-case token.
 * 4. Telemetry sanitizer ([auditDetails], [taskStep], [routingReason]) for backend-authored
 *    system messages so internal IDs, sandbox tags, and routing codes never leak into the UI.
 */
object AeginDisplayMapper {

    private val TASK_STATUS_LABELS: Map<String, String> = mapOf(
        "RUNNING" to "Running",
        "SUSPENDED" to "Paused",
        "PAUSED" to "Paused",
        "ACTIVE" to "Active",
        "SCHEDULED" to "Scheduled",
        "QUEUED" to "Queued",
        "WAITING_APPROVAL" to "Needs Approval",
        "COMPLETED" to "Completed",
        "FAILED" to "Failed",
        "KILLED" to "Stopped",
        "REJECTED" to "Declined",
        "IDLE" to "Ready"
    )

    private val TASK_FREQUENCY_LABELS: Map<String, String> = mapOf(
        "ONCE" to "One-Time",
        "EVERY 15M" to "Every 15 Min",
        "EVERY_15M" to "Every 15 Min",
        "15M" to "Every 15 Min",
        "HOURLY" to "Hourly",
        "DAILY" to "Daily",
        "WEEKLY" to "Weekly",
        "MONTHLY" to "Monthly"
    )

    private val EXECUTION_TARGET_LABELS: Map<String, String> = mapOf(
        "AUTO" to "Smart Auto",
        "LOCAL" to "On-Device",
        "CLOUD" to "Cloud AI"
    )

    private val TASK_CATEGORY_LABELS: Map<String, String> = mapOf(
        "LOCAL_TEXT_TRANSFORM" to "Summarize & Rewrite",
        "LOCAL_STRUCTURED_EXTRACT" to "Extract & Organize",
        "LOCAL_DEVICE_ACTION" to "Quick Device Actions",
        "CLOUD_LIVE_RETRIEVAL" to "Live Web Info",
        "CLOUD_DEEP_REASONING" to "Deep Analysis",
        "CLOUD_MULTI_STEP_AGENT" to "Multi-Step Workflow",
        "AUTO" to "Smart Auto"
    )

    private val TASK_CATEGORY_DESCRIPTIONS: Map<String, String> = mapOf(
        "LOCAL_TEXT_TRANSFORM" to "Summarize, rewrite, or format text privately on your device",
        "LOCAL_STRUCTURED_EXTRACT" to "Pull out key facts, tags, or structured details",
        "LOCAL_DEVICE_ACTION" to "Quick actions and searches across your local files",
        "CLOUD_LIVE_RETRIEVAL" to "Look up live news, weather, or current web info",
        "CLOUD_DEEP_REASONING" to "In-depth planning, coding, and complex analysis",
        "CLOUD_MULTI_STEP_AGENT" to "Multi-step automated workflows across tools"
    )

    private val DOWNLOAD_STATUS_LABELS: Map<String, String> = mapOf(
        "IDLE" to "Ready",
        "DOWNLOADING" to "Downloading",
        "VERIFYING" to "Verifying",
        "COMPLETED" to "Downloaded",
        "PAUSED" to "Paused",
        "CANCELLED" to "Cancelled",
        "FAILED" to "Download Failed"
    )

    private val ENGINE_STATUS_LABELS: Map<String, String> = mapOf(
        "ONLINE" to "Online",
        "INITIALIZING_CORE" to "Starting Up",
        "SUCCESS" to "Ready"
    )

    private val AUDIT_ACTION_LABELS: Map<String, String> = mapOf(
        "KILL_SWITCH_EMERGENCY_STOP_ALL" to "Emergency Stop Activated",
        "KILL_SWITCH_RESET" to "Emergency Stop Cleared",
        "TASK_WAITING_APPROVAL" to "Approval Requested",
        "TASK_STARTED" to "Task Started",
        "TASK_CAPABILITY_CONFLICT" to "Device Compatibility Issue",
        "TASK_PREPROCESSOR_ROUTED" to "Task Routed",
        "TASK_LLM_INCOMPATIBLE" to "AI Model Incompatible",
        "TASK_COMPLETED" to "Task Completed",
        "TASK_FAILED" to "Task Failed",
        "TASK_CREATED" to "Task Created",
        "TASK_CREATED_FROM_CHAT" to "Task Created from Chat",
        "TASK_UPDATED" to "Task Updated",
        "TASK_APPROVED" to "Task Approved",
        "TASK_REJECTED" to "Task Declined",
        "TASK_KILLED" to "Task Stopped",
        "TASK_SUSPENDED" to "Task Paused",
        "TASK_PAUSED" to "Task Paused",
        "TASK_RESUMED" to "Task Resumed",
        "TASK_DELETED" to "Task Deleted",
        "ACCESS_DENIED" to "Access Blocked",
        "SECURITY_BREACH_PROMPT_BLOCKED" to "Unsafe Request Blocked",
        "SECURITY_BREACH_RESPONSE_BLOCKED" to "Unsafe Response Blocked",
        "HITL_TRIGGERED_PROMPT" to "Approval Required",
        "GOVERNANCE_LLM_INCOMPATIBLE" to "Safety Model Incompatible",
        "ASSISTANT_LLM_UNAVAILABLE" to "Assistant Model Unavailable",
        "WORKER_LLM_INCOMPATIBLE" to "Background Model Incompatible",
        "LLM_INVOCATION_FAILURE" to "AI Request Failed",
        "INSUFFICIENT_CONTEXT" to "More Details Needed",
        "ESCALATED" to "Sent for Review",
        "TOOL_CALL_FAILED" to "Action Failed",
        "TOOL_CALLED" to "Action Completed",
        "TOOL_EXECUTION_ERROR" to "Action Error",
        "AI_LOOP_LIMIT_EXCEEDED" to "Step Limit Reached",
        "DUAL_MODEL_AUDIT_REJECTED" to "Blocked by Safety Check",
        "EVENT" to "Activity"
    )

    private val TEMPLATE_CATEGORY_LABELS: Map<String, String> = mapOf(
        "RAG MONITOR" to "Document Monitor",
        "DAILY BRIEF" to "Daily Summary",
        "GUARDRAIL" to "Privacy Check",
        "STRATEGY" to "Roadmap",
        "RAG • LOCAL" to "Private Docs",
        "BRIEFING" to "Daily Summary",
        "PLANNER" to "Planner"
    )

    private val PROVIDER_DISPLAY_NAMES: Map<String, String> = mapOf(
        "AUTO" to "Smart Auto",
        "DISABLE" to "Off",
        "ON-DEVICE MODEL" to "On-Device AI",
        "CUSTOM" to "Custom AI"
    )

    private val HARDWARE_TIER_LABELS: Map<String, String> = mapOf(
        "BLOCKED (< 6 GB RAM)" to "Cloud Recommended (< 6 GB RAM)",
        "ENTRY TIER (6 – 8 GB RAM)" to "Standard (6 – 8 GB RAM)",
        "STANDARD TIER (8 – 12 GB RAM)" to "Capable (8 – 12 GB RAM)",
        "STANDARD (8-12 GB)" to "Capable (8–12 GB)",
        "FLAGSHIP TIER (12 GB+ RAM)" to "High Performance (12 GB+ RAM)"
    )

    private val MODEL_DISPLAY_NAMES: Map<String, String> = mapOf(
        ModelTierCatalog.MODEL_0_5B.lowercase() to "Qwen 2.5 • Compact (0.5B)",
        ModelTierCatalog.MODEL_1_5B.lowercase() to "Qwen 2.5 • Fast (1.5B)",
        ModelTierCatalog.MODEL_GEMMA_2B.lowercase() to "Gemma 2 • Balanced (2B)",
        ModelTierCatalog.MODEL_3B.lowercase() to "Qwen 2.5 • Smart (3B)",
        ModelTierCatalog.MODEL_7B.lowercase() to "Qwen 2.5 • Pro (7B)"
    )

    private val BRACKETED_ID_REGEX = Regex("\\s*\\[(?:task|sbx|session|space)[^\\]]*\\]", RegexOption.IGNORE_CASE)
    private val SANDBOX_PAREN_REGEX = Regex("\\s*\\(sandbox=[^)]*\\)", RegexOption.IGNORE_CASE)
    private val KEY_VALUE_TOKEN_REGEX = Regex("\\b(status|target|executionTarget|routeTarget|category|stage)=([A-Za-z0-9_.-]+)")

    fun taskStatus(rawStatus: String): String =
        lookupMapped(rawStatus, TASK_STATUS_LABELS, default = "Ready")

    fun taskFrequency(rawFrequency: String): String =
        lookupMapped(rawFrequency, TASK_FREQUENCY_LABELS, default = "Daily")

    fun executionTarget(rawTarget: String): String =
        lookupMapped(rawTarget, EXECUTION_TARGET_LABELS, default = "Smart Auto")

    fun routeTarget(rawRoute: String): String =
        lookupMapped(rawRoute, EXECUTION_TARGET_LABELS, default = "On-Device")

    fun taskCategory(rawCategory: String): String =
        lookupMapped(rawCategory, TASK_CATEGORY_LABELS, default = "General")

    fun taskCategoryDescription(rawCategory: String): String =
        TASK_CATEGORY_DESCRIPTIONS[extractTokenValue(rawCategory).uppercase()].orEmpty()

    fun downloadStatus(rawStatus: String): String =
        lookupMapped(rawStatus, DOWNLOAD_STATUS_LABELS, default = "Ready")

    fun engineStatus(rawStatus: String): String =
        lookupMapped(rawStatus, ENGINE_STATUS_LABELS, default = "Ready")

    fun auditAction(rawAction: String): String =
        lookupMapped(rawAction, AUDIT_ACTION_LABELS, default = "Activity")

    fun spaceTemplateCategory(rawCategory: String): String =
        lookupMapped(rawCategory, TEMPLATE_CATEGORY_LABELS, default = "Template")

    fun providerDisplayName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return "Not Set"
        return PROVIDER_DISPLAY_NAMES[trimmed.uppercase()] ?: trimmed
    }

    fun hardwareTier(rawTier: String): String {
        val trimmed = rawTier.trim()
        if (trimmed.isEmpty()) return "Unknown"
        return HARDWARE_TIER_LABELS[trimmed.uppercase()] ?: trimmed
    }

    fun modelDisplayName(rawModelId: String): String {
        val trimmed = rawModelId.trim()
        if (trimmed.isEmpty()) return "Default Model"
        return MODEL_DISPLAY_NAMES[trimmed.lowercase()] ?: trimmed
    }

    fun messageCountLabel(count: Int): String = when {
        count <= 0 -> "New"
        count == 1 -> "1 message"
        else -> "$count messages"
    }

    fun taskStep(rawStep: String, isSuspended: Boolean = false): String {
        val trimmed = rawStep.trim()
        if (trimmed.isEmpty()) {
            return if (isSuspended) "Paused by you." else "Running in the background..."
        }
        val lower = trimmed.lowercase()
        return when {
            lower == "paused at hitl governance gate" -> "Waiting for your approval"
            lower == "halted by global kill switch" -> "Stopped by Emergency Stop"
            lower == "terminated by user" -> "Stopped by you"
            lower == "paused by user" -> "Paused by you"
            lower == "scheduled and active" -> "Scheduled and ready"
            lower.startsWith("stage 1/3") -> "Step 1 of 3 • Preparing task..."
            lower.startsWith("stage 2/3") -> "Step 2 of 3 • Searching your documents..."
            lower.startsWith("stage 3/3") -> "Step 3 of 3 • Generating response..."
            else -> sanitizeTechnicalText(trimmed)
        }
    }

    fun routingReason(rawReason: String): String {
        val trimmed = rawReason.trim()
        if (trimmed.isEmpty()) return ""
        return sanitizeTechnicalText(trimmed)
    }

    fun auditDetails(rawDetails: String): String {
        val trimmed = rawDetails.trim()
        if (trimmed.isEmpty()) return ""
        return sanitizeTechnicalText(trimmed)
    }

    /**
     * Converts raw technical text containing internal IDs, enum tokens, or key=value pairs
     * into readable prose suitable for non-technical users.
     */
    fun sanitizeTechnicalText(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        // Strip internal ID brackets like [task-1234] or [sbx-1234] and (sandbox=...)
        text = BRACKETED_ID_REGEX.replace(text, "")
        text = SANDBOX_PAREN_REGEX.replace(text, "")
        text = text.replace("in isolated sandbox", "in the background", ignoreCase = true)

        // Replace embedded key=value pairs like executionTarget=auto, status=completed, category=CLOUD_DEEP_REASONING
        text = KEY_VALUE_TOKEN_REGEX.replace(text) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            when (key.lowercase()) {
                "status" -> taskStatus(value)
                "target", "executiontarget", "routetarget" -> "Mode: ${executionTarget(value)}"
                "category" -> "Type: ${taskCategory(value)}"
                "stage" -> "Route: ${humanizeCode(value)}"
                else -> "${humanizeCode(key)}: ${humanizeCode(value)}"
            }
        }

        // Replace standalone backend category constants if present in prose
        for ((code, friendly) in TASK_CATEGORY_LABELS) {
            if (code != "AUTO" && text.contains(code)) {
                text = text.replace(code, friendly)
            }
        }
        for ((code, friendly) in TASK_STATUS_LABELS) {
            val wordRegex = Regex("\\b${Regex.escape(code)}\\b")
            if (wordRegex.containsMatchIn(text)) {
                text = wordRegex.replace(text, friendly)
            }
        }

        // Replace technical jargon phrases with plain-language equivalents
        text = text
            .replace("Global Kill Switch", "Emergency Stop", ignoreCase = true)
            .replace("Kill Switch", "Emergency Stop", ignoreCase = true)
            .replace("HITL", "approval", ignoreCase = false)
            .replace("in O(1) time", "automatically", ignoreCase = true)
            .replace("Static intent", "Preset rule", ignoreCase = true)
            .replace("SLM Preprocessor", "smart router", ignoreCase = true)
            .replace("Worker LLM", "Background AI", ignoreCase = true)
            .replace("Governance LLM", "Safety AI", ignoreCase = true)
            .replace("Assistant LLM", "Assistant AI", ignoreCase = true)
            .replace("GGUF", "offline AI", ignoreCase = false)
            .replace(Regex("\\s{2,}"), " ")
            .replace(" ,", ",")
            .trim()

        return text
    }

    /**
     * Converts any raw code or key=value string (e.g., "status=completed", "WAITING_APPROVAL",
     * "multi-step-agent") into clean Title Case for user-facing display.
     */
    fun humanizeCode(rawCode: String, default: String = ""): String {
        val extracted = extractTokenValue(rawCode)
        if (extracted.isEmpty()) return default
        return extracted
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                val lower = word.lowercase()
                lower.replaceFirstChar { ch -> ch.titlecase() }
            }
            .ifEmpty { default }
    }

    private fun lookupMapped(raw: String, table: Map<String, String>, default: String): String {
        val extracted = extractTokenValue(raw)
        if (extracted.isEmpty()) return default
        return table[extracted.uppercase()] ?: humanizeCode(extracted, default)
    }

    private fun extractTokenValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val eqIdx = trimmed.indexOf('=')
        return if (eqIdx in 1 until trimmed.lastIndex) {
            trimmed.substring(eqIdx + 1).trim()
        } else {
            trimmed
        }
    }
}
