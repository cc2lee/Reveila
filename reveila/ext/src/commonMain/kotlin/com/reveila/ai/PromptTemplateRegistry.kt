package com.reveila.ai

import com.reveila.system.io.PlatformFileSystem
import com.reveila.util.json.JsonUtil

enum class PromptMode {
    AGENTIC_JSON,
    CONVERSATIONAL
}

enum class ChatFormat(val defaultStopToken: String) {
    CHATML("<|im_end|>"),
    GEMMA("<end_of_turn>"),
    LLAMA3("<|eot_id|>"),
    PHI3("<|end|>"),
    MISTRAL("</s>"),
    CUSTOM("");

    companion object {
        fun fromStringOrNull(raw: String?): ChatFormat? {
            val norm = raw?.trim()?.lowercase()?.replace("-", "")?.replace("_", "") ?: return null
            return when (norm) {
                "chatml", "qwen", "qwen2", "qwen25", "qwen3" -> CHATML
                "gemma", "gemma2", "gemma3" -> GEMMA
                "llama3", "llama31", "llama32", "llama33" -> LLAMA3
                "phi3", "phi35", "phi4", "phi" -> PHI3
                "mistral", "mixtral", "llama2" -> MISTRAL
                "custom" -> CUSTOM
                else -> null
            }
        }
    }
}

data class CustomChatMarkers(
    val bos: String = "",
    val systemPrefix: String? = null,
    val systemSuffix: String? = null,
    val userPrefix: String? = null,
    val userSuffix: String? = null,
    val assistantPrefix: String? = null,
    val assistantSuffix: String? = null,
    val assistantGenerationPrefix: String? = null,
    val foldSystemIntoUser: Boolean = false,
    val stopToken: String? = null
)

data class ChatFormattingSpec(
    val chatFormat: ChatFormat,
    val stopToken: String,
    val customMarkers: CustomChatMarkers? = null,
    val matchedTemplateId: String = "family-default"
)

data class ResolvedPromptTemplate(
    val templateId: String,
    val sourcePath: String?,
    val body: String,
    val chatFormat: ChatFormat,
    val stopToken: String,
    val preferredResponseMode: PromptMode? = null,
    val parameterSizeBillions: Double? = null,
    val maxHistoryMessages: Int? = null,
    val customMarkers: CustomChatMarkers? = null
)

/**
 * Resolves model-specific and family-specific prompt templates from
 * `${system.home}/configs/templates/prompts/`, falling back cleanly to
 * standard generic templates when no model-specific match exists.
 */
object PromptTemplateRegistry {

    private const val PROMPTS_REL_DIR = "configs/templates/prompts"

    private val QUANT_SUFFIX_REGEX = Regex(
        "[-_](?:iq\\d+_[a-z0-9_]+|q\\d+_[a-z0-9_]+|q\\d+|f16|bf16|f32|fp16|int\\d+|awq|gptq|gguf)$",
        RegexOption.IGNORE_CASE
    )
    private val PARAM_SIZE_REGEX = Regex(
        "(?:^|[-_:/\\s])(\\d+(?:\\.\\d+)?)b(?=$|[-_.\\s:/])",
        RegexOption.IGNORE_CASE
    )
    private val FILE_EXT_REGEX = Regex(
        "\\.(?:gguf|bin|safetensors|onnx)$",
        RegexOption.IGNORE_CASE
    )

    internal const val EMBEDDED_DEFAULT_AGENTIC_TEMPLATE: String = """## YOUR ROLE
Your role is ${'$'}{role}. You are a versatile AI assistant and sovereign agent capable of answering general knowledge, reasoning, technical, and everyday questions directly, as well as analyzing the user's Reference Documents (Knowledge Vault) and invoking approved tools when appropriate.

${'$'}{tools_section}

${'$'}{vault_section}

## CONSTRAINTS
- Always provide a "confidence-score" (0-1) for your reasoning.
- Answer general knowledge, coding, math, and everyday questions directly using your own knowledge.
${'$'}{vault_constraints_line}
- Additional rules: ${'$'}{constraints}

## OUTPUT FORMAT
Return your response as a valid JSON document with only the following keys, and all keys MUST be lowercase:
- "status": (use ONLY one of: ${'$'}{status_completed}, ${'$'}{status_insufficient_context}, ${'$'}{status_escalate}, ${'$'}{status_tool_call}, ${'$'}{status_failed})
- "reasoning": (a brief explanation of how you arrived at the status and answer)
- "result": (your final answer, or the result of any tool execution)
- "confidence-score": (a numeric value between 0 and 1 indicating confidence in the response)
${'$'}{tool_call_format_line}"""

    internal const val EMBEDDED_DEFAULT_CONVERSATIONAL_TEMPLATE: String = """You are ${'$'}{role}.
You are a helpful, accurate general-purpose AI assistant. Answer any general knowledge, technical, or workspace question directly in clear plain text.
If relevant Reference Documents are provided below, use and cite them; if the question is unrelated to those documents, answer normally from your own knowledge.
${'$'}{constraints_line}

${'$'}{vault_section}"""

    private data class ManifestTemplateHints(
        val promptTemplate: String? = null,
        val chatFormat: ChatFormat? = null
    )

    private data class ParsedTemplateFile(
        val templateId: String,
        val sourcePath: String?,
        val body: String,
        val metadata: Map<String, String>
    )

    fun extractModelStems(modelName: String?): Pair<String, String> {
        val raw = modelName?.trim().orEmpty()
        if (raw.isEmpty()) return "" to ""
        val fileName = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        val exactStem = fileName.replace(FILE_EXT_REGEX, "").lowercase().trim()
        var baseStem = exactStem
        while (true) {
            val stripped = baseStem.replace(QUANT_SUFFIX_REGEX, "")
            if (stripped == baseStem || stripped.isBlank()) break
            baseStem = stripped
        }
        return exactStem to baseStem
    }

    fun extractParameterSizeBillions(modelName: String?): Double? {
        val raw = modelName?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val match = PARAM_SIZE_REGEX.find(raw) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    fun inferModelFamily(modelName: String?): String? {
        val lower = modelName?.trim()?.lowercase().orEmpty()
        if (lower.isEmpty()) return null
        return when {
            lower.contains("qwen2.5") || lower.contains("qwen2_5") -> "qwen2.5"
            lower.contains("qwen3") -> "qwen3"
            lower.contains("qwen") -> "qwen"
            lower.contains("gemma-2") || lower.contains("gemma2") -> "gemma"
            lower.contains("gemma-3") || lower.contains("gemma3") -> "gemma"
            lower.contains("gemma") -> "gemma"
            lower.contains("llama-3") || lower.contains("llama3") -> "llama3"
            lower.contains("phi-3") || lower.contains("phi3") || lower.contains("phi-4") || lower.contains("phi4") -> "phi3"
            lower.contains("mistral") || lower.contains("mixtral") -> "mistral"
            else -> null
        }
    }

    fun inferChatFormat(modelName: String?): ChatFormat {
        val lower = modelName?.trim()?.lowercase().orEmpty()
        return when {
            lower.isEmpty() || lower == "auto" || lower.contains("on-device") -> ChatFormat.CHATML
            lower.contains("llama-3") || lower.contains("llama3") -> ChatFormat.LLAMA3
            lower.contains("gemma") -> ChatFormat.GEMMA
            lower.contains("phi-3") || lower.contains("phi3") || lower.contains("phi-4") || lower.contains("phi4") -> ChatFormat.PHI3
            lower.contains("mistral") || lower.contains("mixtral") || lower.contains("llama-2") || lower.contains("llama2") -> ChatFormat.MISTRAL
            lower.contains("qwen") || lower.contains("smollm") || lower.contains("deepseek") || lower.contains("yi-") -> ChatFormat.CHATML
            else -> ChatFormat.CHATML
        }
    }

    fun resolveParameterSizeBillions(modelName: String?, systemHome: String? = null): Double? {
        val modelProfile = findBestTemplateFileForMetadata(modelName, systemHome)
        val frontmatterSize = modelProfile?.metadata?.get("parameter_size_b")?.toDoubleOrNull()
        return frontmatterSize ?: extractParameterSizeBillions(modelName)
    }

    fun isSmallLocalModel(modelName: String?, systemHome: String? = null): Boolean {
        val norm = modelName?.trim()?.lowercase().orEmpty()
        if (norm.isEmpty() || norm == "auto" || norm.contains("on-device")) {
            return true
        }
        val effectiveSize = resolveParameterSizeBillions(modelName, systemHome)
        if (effectiveSize != null) {
            return effectiveSize <= 2.2
        }
        return norm.contains("tiny") || norm.contains("nano") || norm.contains("0.5b") || norm.contains("1.5b")
    }

    /**
     * Determines whether a turn should use plain-text conversational prompting or strict agentic JSON.
     * Small local models (<= 2B) default to CONVERSATIONAL even in askAgent(), reserving AGENTIC_JSON
     * for tool-enabled, Cloud, GBNF-constrained, or larger (> 2B) model calls.
     */
    fun resolvePromptMode(
        isLocalProvider: Boolean,
        modelName: String?,
        hasTools: Boolean,
        hasGrammar: Boolean = false,
        systemHome: String? = null
    ): PromptMode {
        if (hasGrammar || hasTools || !isLocalProvider) {
            return PromptMode.AGENTIC_JSON
        }
        val metaTemplate = findBestTemplateFileForMetadata(modelName, systemHome)
        val explicitPref = parsePromptMode(metaTemplate?.metadata?.get("response_mode"))
        if (explicitPref != null) {
            return explicitPref
        }
        return if (isSmallLocalModel(modelName, systemHome)) {
            PromptMode.CONVERSATIONAL
        } else {
            PromptMode.AGENTIC_JSON
        }
    }

    fun resolveChatFormattingSpec(
        modelName: String?,
        systemHome: String? = null
    ): ChatFormattingSpec {
        val manifestHints = readManifestHints(modelName, systemHome)
        val metaTemplate = findBestTemplateFileForMetadata(modelName, systemHome, manifestHints?.promptTemplate)
        val customMarkers = metaTemplate?.let { parseCustomChatMarkers(it.metadata) }
        val explicitFormat = metaTemplate?.metadata?.get("chat_format")?.let { ChatFormat.fromStringOrNull(it) }
            ?: manifestHints?.chatFormat
        val chatFormat = when {
            customMarkers != null && explicitFormat == null -> ChatFormat.CUSTOM
            customMarkers != null && explicitFormat == ChatFormat.CUSTOM -> ChatFormat.CUSTOM
            explicitFormat != null -> explicitFormat
            else -> inferChatFormat(modelName)
        }
        val stopToken = metaTemplate?.metadata?.get("stop_token")
            ?.takeIf { it.isNotEmpty() }
            ?: customMarkers?.stopToken?.takeIf { it.isNotEmpty() }
            ?: chatFormat.defaultStopToken
        return ChatFormattingSpec(
            chatFormat = chatFormat,
            stopToken = stopToken,
            customMarkers = customMarkers,
            matchedTemplateId = metaTemplate?.templateId ?: "heuristic:${chatFormat.name.lowercase()}"
        )
    }

    fun resolvePromptTemplate(
        modelName: String?,
        systemHome: String? = null,
        mode: PromptMode = PromptMode.AGENTIC_JSON
    ): ResolvedPromptTemplate {
        val manifestHints = readManifestHints(modelName, systemHome)
        val metaFormatSpec = resolveChatFormattingSpec(modelName, systemHome)
        val fs = PlatformFileSystem()
        val promptsDir = resolvePromptsDir(fs, systemHome)

        val candidate = if (promptsDir != null) {
            findTemplateForMode(fs, promptsDir, modelName, mode, manifestHints?.promptTemplate)
        } else {
            null
        }

        if (candidate != null) {
            val chatFmt = candidate.metadata["chat_format"]?.let { ChatFormat.fromStringOrNull(it) }
                ?: metaFormatSpec.chatFormat
            val customMarkers = parseCustomChatMarkers(candidate.metadata) ?: metaFormatSpec.customMarkers
            val stopTok = candidate.metadata["stop_token"]?.takeIf { it.isNotEmpty() }
                ?: customMarkers?.stopToken?.takeIf { it.isNotEmpty() }
                ?: chatFmt.defaultStopToken
            val prefMode = parsePromptMode(candidate.metadata["response_mode"])
            val paramB = candidate.metadata["parameter_size_b"]?.toDoubleOrNull()
                ?: extractParameterSizeBillions(modelName)
            val maxHist = candidate.metadata["max_history_messages"]?.toIntOrNull()

            return ResolvedPromptTemplate(
                templateId = candidate.templateId,
                sourcePath = candidate.sourcePath,
                body = candidate.body,
                chatFormat = chatFmt,
                stopToken = stopTok,
                preferredResponseMode = prefMode,
                parameterSizeBillions = paramB,
                maxHistoryMessages = maxHist,
                customMarkers = customMarkers
            )
        }

        val fallbackBody = if (mode == PromptMode.CONVERSATIONAL) {
            EMBEDDED_DEFAULT_CONVERSATIONAL_TEMPLATE
        } else {
            EMBEDDED_DEFAULT_AGENTIC_TEMPLATE
        }
        val fallbackId = if (mode == PromptMode.CONVERSATIONAL) {
            "embedded:conversational"
        } else {
            "embedded:default"
        }
        return ResolvedPromptTemplate(
            templateId = fallbackId,
            sourcePath = null,
            body = fallbackBody,
            chatFormat = metaFormatSpec.chatFormat,
            stopToken = metaFormatSpec.stopToken,
            preferredResponseMode = mode,
            parameterSizeBillions = extractParameterSizeBillions(modelName),
            maxHistoryMessages = null,
            customMarkers = metaFormatSpec.customMarkers
        )
    }

    private fun findTemplateForMode(
        fs: PlatformFileSystem,
        promptsDir: String,
        modelName: String?,
        mode: PromptMode,
        manifestTemplateHint: String?
    ): ParsedTemplateFile? {
        val (exactStem, baseStem) = extractModelStems(modelName)
        val family = inferModelFamily(modelName)
        val modelStems = buildList {
            if (!manifestTemplateHint.isNullOrBlank()) {
                add(manifestTemplateHint.trim().removeSuffix(".md").lowercase())
            }
            if (exactStem.isNotEmpty()) add(exactStem)
            if (baseStem.isNotEmpty() && baseStem != exactStem) add(baseStem)
        }.distinct()

        if (mode == PromptMode.CONVERSATIONAL) {
            // 1. Model-specific conversational templates
            for (stem in modelStems) {
                loadTemplateByRelativePath(fs, promptsDir, "models/$stem.conversational.md")?.let { return it }
                loadTemplateByRelativePath(fs, promptsDir, "models/$stem.md")
                    ?.takeIf { parsePromptMode(it.metadata["response_mode"]) != PromptMode.AGENTIC_JSON }
                    ?.let { return it }
            }
            // 2. Family-specific conversational templates
            if (!family.isNullOrBlank()) {
                loadTemplateByRelativePath(fs, promptsDir, "families/$family.conversational.md")?.let { return it }
                loadTemplateByRelativePath(fs, promptsDir, "families/$family.md")
                    ?.takeIf { parsePromptMode(it.metadata["response_mode"]) != PromptMode.AGENTIC_JSON }
                    ?.let { return it }
                // Group qwen2.5 / qwen3 under families/qwen.md if exact family file isn't present
                if (family.startsWith("qwen") && family != "qwen") {
                    loadTemplateByRelativePath(fs, promptsDir, "families/qwen.md")
                        ?.takeIf { parsePromptMode(it.metadata["response_mode"]) != PromptMode.AGENTIC_JSON }
                        ?.let { return it }
                }
            }
            // 3. Generic conversational template
            loadTemplateByRelativePath(fs, promptsDir, "conversational.md")?.let { return it }
            return null
        }

        // PromptMode.AGENTIC_JSON
        for (stem in modelStems) {
            loadTemplateByRelativePath(fs, promptsDir, "models/$stem.agentic.md")?.let { return it }
            loadTemplateByRelativePath(fs, promptsDir, "models/$stem.md")
                ?.takeIf { parsePromptMode(it.metadata["response_mode"]) == PromptMode.AGENTIC_JSON }
                ?.let { return it }
        }
        if (!family.isNullOrBlank()) {
            loadTemplateByRelativePath(fs, promptsDir, "families/$family.agentic.md")?.let { return it }
            loadTemplateByRelativePath(fs, promptsDir, "families/$family.md")
                ?.takeIf { parsePromptMode(it.metadata["response_mode"]) == PromptMode.AGENTIC_JSON }
                ?.let { return it }
        }
        // Generic agentic template
        loadTemplateByRelativePath(fs, promptsDir, "default.md")?.let { return it }
        return null
    }

    private fun findBestTemplateFileForMetadata(
        modelName: String?,
        systemHome: String?,
        manifestTemplateHint: String? = null
    ): ParsedTemplateFile? {
        val fs = PlatformFileSystem()
        val promptsDir = resolvePromptsDir(fs, systemHome) ?: return null
        val (exactStem, baseStem) = extractModelStems(modelName)
        val family = inferModelFamily(modelName)

        val modelStems = buildList {
            if (!manifestTemplateHint.isNullOrBlank()) {
                add(manifestTemplateHint.trim().removeSuffix(".md").lowercase())
            }
            if (exactStem.isNotEmpty()) add(exactStem)
            if (baseStem.isNotEmpty() && baseStem != exactStem) add(baseStem)
        }.distinct()

        for (stem in modelStems) {
            loadTemplateByRelativePath(fs, promptsDir, "models/$stem.md")?.let { return it }
            loadTemplateByRelativePath(fs, promptsDir, "models/$stem.conversational.md")?.let { return it }
            loadTemplateByRelativePath(fs, promptsDir, "models/$stem.agentic.md")?.let { return it }
        }
        if (!family.isNullOrBlank()) {
            loadTemplateByRelativePath(fs, promptsDir, "families/$family.md")?.let { return it }
            if (family.startsWith("qwen") && family != "qwen") {
                loadTemplateByRelativePath(fs, promptsDir, "families/qwen.md")?.let { return it }
            }
        }
        return null
    }

    private fun resolvePromptsDir(fs: PlatformFileSystem, systemHome: String?): String? {
        val candidates = buildList {
            if (!systemHome.isNullOrBlank()) {
                add(fs.resolve(systemHome, PROMPTS_REL_DIR))
            }
            add(fs.resolve("system-home/standard", PROMPTS_REL_DIR))
        }.distinct()
        for (dir in candidates) {
            if (fs.exists(dir) && fs.isDirectory(dir)) {
                return dir
            }
        }
        return null
    }

    private fun loadTemplateByRelativePath(
        fs: PlatformFileSystem,
        promptsDir: String,
        relPath: String
    ): ParsedTemplateFile? {
        val directPath = fs.resolve(promptsDir, relPath)
        val resolvedPath = if (fs.exists(directPath)) {
            directPath
        } else {
            val slashIdx = relPath.lastIndexOf('/')
            val subDir = if (slashIdx > 0) fs.resolve(promptsDir, relPath.substring(0, slashIdx)) else promptsDir
            val targetName = if (slashIdx > 0) relPath.substring(slashIdx + 1) else relPath
            if (fs.exists(subDir) && fs.isDirectory(subDir)) {
                val match = fs.listRelativePaths(subDir, "md")
                    .firstOrNull { it.substringAfterLast('/').substringAfterLast('\\').equals(targetName, ignoreCase = true) }
                if (match != null) fs.resolve(subDir, match) else null
            } else {
                null
            }
        } ?: return null

        return try {
            val rawText = fs.readText(resolvedPath)
            if (rawText.isBlank()) return null
            val (meta, body) = parseFrontmatter(rawText)
            ParsedTemplateFile(
                templateId = relPath.removeSuffix(".md"),
                sourcePath = resolvedPath,
                body = body.trim(),
                metadata = meta
            )
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseFrontmatter(rawText: String): Pair<Map<String, String>, String> {
        val normalized = rawText.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) {
            return emptyMap<String, String>() to normalized
        }
        val endIdx = normalized.indexOf("\n---", startIndex = 4)
        if (endIdx < 0) {
            return emptyMap<String, String>() to normalized
        }
        val headerBlock = normalized.substring(4, endIdx)
        val afterMarker = endIdx + 4
        val body = if (afterMarker < normalized.length && normalized[afterMarker] == '\n') {
            normalized.substring(afterMarker + 1)
        } else if (afterMarker < normalized.length) {
            normalized.substring(afterMarker)
        } else {
            ""
        }

        val meta = linkedMapOf<String, String>()
        for (line in headerBlock.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx <= 0) continue
            val key = trimmed.substring(0, colonIdx).trim().lowercase()
            var value = trimmed.substring(colonIdx + 1).trim()
            if ((value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) ||
                (value.startsWith("'") && value.endsWith("'") && value.length >= 2)
            ) {
                value = value.substring(1, value.length - 1)
            }
            value = value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
            meta[key] = value
        }
        return meta to body
    }

    private fun parseCustomChatMarkers(meta: Map<String, String>): CustomChatMarkers? {
        val markerKeys = listOf(
            "bos",
            "system_prefix", "system_suffix",
            "user_prefix", "user_suffix",
            "assistant_prefix", "assistant_suffix",
            "assistant_generation_prefix"
        )
        if (markerKeys.none { meta.containsKey(it) }) {
            return null
        }
        return CustomChatMarkers(
            bos = meta["bos"] ?: "",
            systemPrefix = meta["system_prefix"],
            systemSuffix = meta["system_suffix"],
            userPrefix = meta["user_prefix"],
            userSuffix = meta["user_suffix"],
            assistantPrefix = meta["assistant_prefix"],
            assistantSuffix = meta["assistant_suffix"],
            assistantGenerationPrefix = meta["assistant_generation_prefix"],
            foldSystemIntoUser = meta["fold_system_into_user"]?.equals("true", ignoreCase = true) == true,
            stopToken = meta["stop_token"]
        )
    }

    private fun parsePromptMode(raw: String?): PromptMode? {
        return when (raw?.trim()?.lowercase()?.replace("-", "_")) {
            "conversational", "plain", "chat" -> PromptMode.CONVERSATIONAL
            "agentic_json", "agentic", "json" -> PromptMode.AGENTIC_JSON
            else -> null
        }
    }

    private fun readManifestHints(modelName: String?, systemHome: String?): ManifestTemplateHints? {
        val target = modelName?.trim().orEmpty()
        if (target.isEmpty() || systemHome.isNullOrBlank()) return null
        return try {
            val fs = PlatformFileSystem()
            val manifestPath = fs.resolve(systemHome, "models/manifest.json")
            if (!fs.exists(manifestPath)) return null
            val parsed = JsonUtil.parseJsonStringToMap(fs.readText(manifestPath))
            @Suppress("UNCHECKED_CAST")
            val models = parsed["models"] as? List<Map<String, Any?>> ?: return null
            val targetLower = target.lowercase()
            val (targetExactStem, _) = extractModelStems(target)
            for (entry in models) {
                val filename = entry["filename"]?.toString()?.trim().orEmpty()
                val modelId = entry["modelId"]?.toString()?.trim().orEmpty()
                val repoFile = entry["repoPath"]?.toString()?.substringAfterLast('/')?.substringBefore('?')?.trim().orEmpty()
                val matches = filename.equals(targetLower, ignoreCase = true) ||
                    modelId.equals(targetLower, ignoreCase = true) ||
                    modelId.equals(targetExactStem, ignoreCase = true) ||
                    repoFile.equals(targetLower, ignoreCase = true)
                if (matches) {
                    val tpl = entry["promptTemplate"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val fmt = ChatFormat.fromStringOrNull(entry["chatFormat"]?.toString())
                    return if (tpl != null || fmt != null) ManifestTemplateHints(tpl, fmt) else null
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
