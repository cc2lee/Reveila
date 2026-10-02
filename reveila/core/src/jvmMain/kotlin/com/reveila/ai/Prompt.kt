package com.reveila.ai

import com.reveila.system.Constants

class Prompt private constructor() {

    companion object {
        /**
         * Generates a system prompt for the AI agent using a template.
         */
        @JvmStatic
        fun getSystemPrompt(role: String?, context: String?, constraints: String?): String {
            val prompt = """
## YOUR ROLE
Your role is ${'$'}{role}.

${'$'}{context_section}

## CONSTRAINTS
- Always provide a "Confidence Score" (0-1) for your reasoning.
${'$'}{insufficient_context_rule}
- Additional rules: ${'$'}{constraints}

## OUTPUT FORMAT
Return your response as a valid JSON document with only the following keys, and all keys MUST be lowercase:
- "status": (use ONLY one of: ${'$'}{completed}, ${'$'}{insufficient-context}, ${'$'}{escalate}, ${'$'}{tool-call}, ${'$'}{failed})
- "reasoning": (a brief explanation of how you arrived at the status, and any relevant information from the context that influenced your decision)
- "result": (Your final answer, or the result of any tool execution)
- "confidence-score": (a value between 0 and 1 indicating the confidence in the response)
- "tool-call": (if applicable, include any tools you would call and their arguments in a JSON-structured format)

"""

            var contextSection = ""
            var insufficientContextRule = ""

            if (!context.isNullOrBlank() && !context.contains("Related Documents: No relevant internal documents found.")) {
                contextSection = """
## CONTEXT
<context_boundary>

${'$'}{context_data}

</context_boundary>
""".replace("\${context_data}", context)
                insufficientContextRule = "- If the context information within the <context_boundary> is insufficient, return the status without attempting to answer the task."
            }

            return prompt
                .replace("\${role}", if (role.isNullOrBlank()) "generalist agent" else role)
                .replace("\${context_section}", contextSection)
                .replace("\${insufficient_context_rule}", insufficientContextRule)
                .replace("\${constraints}", if (constraints.isNullOrBlank()) "No additional rules" else constraints)
                // Status values
                .replace("\${completed}", Constants.AI_STATUS_COMPLETED)
                .replace("\${insufficient-context}", Constants.AI_STATUS_INSUFFICIENT_CONTEXT)
                .replace("\${escalate}", Constants.AI_STATUS_ESCALATE)
                .replace("\${tool-call}", Constants.AI_STATUS_TOOL_CALL)
                .replace("\${failed}", Constants.AI_STATUS_FAILED)
        }
    }
}
