package com.reveila.ai

import com.reveila.system.Constants
import com.reveila.system.platform.PlatformSystem
import com.reveila.util.TimeFormat
import kotlinx.datetime.TimeZone

data class RenderedPrompt(
    val systemPrompt: String,
    val resolvedTemplate: ResolvedPromptTemplate
)

class Prompt private constructor() {

    companion object {
        private val NO_DOCS_SENTINEL_REGEX = Regex(
            "No relevant internal documents(?: found)?\\.?",
            RegexOption.IGNORE_CASE
        )

        @kotlin.jvm.JvmOverloads
        fun getSystemPrompt(
            role: String?,
            context: String?,
            constraints: String?,
            modelName: String? = null,
            systemHome: String? = null,
            mode: PromptMode = PromptMode.AGENTIC_JSON
        ): String {
            val rawContext = context?.trim().orEmpty()
            var extractedToolsText = ""
            var extractedVaultText = ""

            if (rawContext.isNotEmpty()) {
                if (rawContext.contains("Related Documents:", ignoreCase = true)) {
                    val beforeDocs = rawContext.substringBefore("Related Documents:").trim()
                    val afterDocs = rawContext.substringAfter("Related Documents:").trim()
                    if (beforeDocs.isNotEmpty()) {
                        extractedToolsText = beforeDocs.removePrefix("Available Tools:").trim()
                    }
                    extractedVaultText = afterDocs
                } else if (rawContext.startsWith("Available Tools:", ignoreCase = true)) {
                    extractedToolsText = rawContext.removePrefix("Available Tools:").trim()
                } else {
                    extractedVaultText = rawContext
                }
            }

            val syntheticTools = if (extractedToolsText.isNotBlank() && !extractedToolsText.equals("none", ignoreCase = true)) {
                extractedToolsText.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { toolName -> LlmTool().apply { name = toolName } }
            } else {
                emptyList()
            }

            return buildSystemPrompt(
                role = role,
                spaceName = null,
                tools = syntheticTools,
                vaultContext = extractedVaultText,
                constraints = constraints,
                modelName = modelName,
                systemHome = systemHome,
                mode = mode
            ).systemPrompt
        }

        fun buildSystemPrompt(
            role: String? = null,
            spaceName: String? = null,
            tools: List<LlmTool> = emptyList(),
            vaultContext: String? = null,
            constraints: String? = null,
            modelName: String? = null,
            systemHome: String? = null,
            mode: PromptMode = PromptMode.AGENTIC_JSON
        ): RenderedPrompt {
            val resolved = PromptTemplateRegistry.resolvePromptTemplate(
                modelName = modelName,
                systemHome = systemHome,
                mode = mode
            )

            val effectiveRole = role?.trim()?.takeIf { it.isNotEmpty() } ?: "generalist AI assistant"
            val effectiveSpace = spaceName?.trim()?.takeIf { it.isNotEmpty() } ?: AgentSession.DEFAULT_SPACE_NAME
            val cleanVault = cleanVaultContext(vaultContext)
            val hasVault = cleanVault.isNotEmpty()

            val vaultSection = if (hasVault) {
                """
## REFERENCE DOCUMENTS (OPTIONAL VAULT CONTEXT)
<vault_context>
$cleanVault
</vault_context>
""".trim()
            } else {
                ""
            }

            val vaultPlainSection = if (hasVault) {
                """
[Reference Documents]
$cleanVault
[/Reference Documents]
""".trim()
            } else {
                ""
            }

            val vaultConstraintsLine = if (hasVault) {
                "- If the user asks about their private/internal files or workspace data, prioritize and cite the Reference Documents inside <vault_context>; ignore unrelated excerpts on general questions. Only return status \"${Constants.AI_STATUS_INSUFFICIENT_CONTEXT}\" if the user explicitly asks about private records not present in <vault_context>."
            } else {
                "- Only return status \"${Constants.AI_STATUS_INSUFFICIENT_CONTEXT}\" if the user explicitly asks about private workspace documents or files that have not been provided."
            }

            val validTools = tools.filter { !it.name.isNullOrBlank() }
            val toolsList = if (validTools.isNotEmpty()) {
                validTools.joinToString(", ") { it.name!!.trim() }
            } else {
                "None"
            }

            val toolsSection = if (validTools.isNotEmpty()) {
                val formattedTools = validTools.joinToString("\n") { tool ->
                    val desc = tool.description?.trim().orEmpty()
                    if (desc.isNotEmpty()) "- ${tool.name}: $desc" else "- ${tool.name}"
                }
                """
## AVAILABLE TOOLS
<available_tools>
$formattedTools
</available_tools>
""".trim()
            } else {
                ""
            }

            val toolCallFormatLine = if (validTools.isNotEmpty()) {
                """- "tool-call": (required when status is "{{status_tool_call}}"; an object or array of objects formatted as {"name": "<tool_name>", "arguments": {<tool_parameters>}}, e.g. {"name": "virtual_browser", "arguments": {"action": "search", "query": "latest news"}})"""
            } else {
                ""
            }

            val cleanConstraints = constraints?.trim()?.takeIf { it.isNotEmpty() }
            val currentLocalTimeLine = try {
                val nowStr = TimeFormat.timestamp(PlatformSystem.currentTimeMillis()).substringBefore(".")
                val tz = TimeZone.currentSystemDefault().id
                "Current local date/time: $nowStr ($tz). Use this to resolve relative dates/times for reminders and scheduled tasks."
            } catch (_: Exception) {
                null
            }
            val presentationGuidance = ResponsePresentationService.formatPromptGuidance(DeviceFormFactor.COMPACT_MOBILE)
            val mergedConstraints = listOfNotNull(currentLocalTimeLine, cleanConstraints, presentationGuidance).joinToString("\n")
            val constraintsValue = mergedConstraints.ifBlank { "No additional rules" }
            val constraintsLine = "Additional rules:\n$mergedConstraints"

            val legacyContextSection = buildString {
                if (validTools.isNotEmpty() || hasVault) {
                    appendLine("## CONTEXT")
                    appendLine("<context_boundary>")
                    if (validTools.isNotEmpty()) {
                        appendLine("Available Tools: $toolsList")
                    }
                    if (hasVault) {
                        appendLine("Related Documents: $cleanVault")
                    }
                    append("</context_boundary>")
                }
            }.trim()

            val placeholders = mapOf(
                "role" to effectiveRole,
                "space_name" to effectiveSpace,
                "vault_section" to vaultSection,
                "vault_plain_section" to vaultPlainSection,
                "vault_docs" to cleanVault,
                "vault_constraints_line" to vaultConstraintsLine,
                "tools_section" to toolsSection,
                "tools_list" to toolsList,
                "tool_call_format_line" to toolCallFormatLine,
                "constraints" to constraintsValue,
                "constraints_line" to constraintsLine,
                "context_section" to legacyContextSection,
                "insufficient_context_rule" to "",
                "status_completed" to Constants.AI_STATUS_COMPLETED,
                "status_insufficient_context" to Constants.AI_STATUS_INSUFFICIENT_CONTEXT,
                "status_escalate" to Constants.AI_STATUS_ESCALATE,
                "status_tool_call" to Constants.AI_STATUS_TOOL_CALL,
                "status_failed" to Constants.AI_STATUS_FAILED,
                // Legacy ${...} aliases
                "completed" to Constants.AI_STATUS_COMPLETED,
                "insufficient-context" to Constants.AI_STATUS_INSUFFICIENT_CONTEXT,
                "escalate" to Constants.AI_STATUS_ESCALATE,
                "tool-call" to Constants.AI_STATUS_TOOL_CALL,
                "failed" to Constants.AI_STATUS_FAILED
            )

            var rendered = resolved.body
            for ((key, value) in placeholders) {
                rendered = rendered
                    .replace("{{$key}}", value)
                    .replace("\${$key}", value)
            }
            rendered = rendered
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()

            return RenderedPrompt(
                systemPrompt = rendered,
                resolvedTemplate = resolved
            )
        }

        private fun cleanVaultContext(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim()
            if (NO_DOCS_SENTINEL_REGEX.containsMatchIn(trimmed) && !trimmed.contains("[Doc ")) {
                return ""
            }
            return trimmed
        }
    }
}
