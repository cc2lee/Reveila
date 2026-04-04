package com.reveila.ai;

import com.reveila.system.Constants;

public class Prompt {

    /**
     * Generates a system prompt for the AI agent using a template.
     *
     * @param role The role the AI should assume.
     * @param task The primary task for the AI.
     * @param context Additional context for the task.
     * @param constraints Rules and limitations for the AI.
     * @return A formatted system prompt.
     */
    public static String getPrompt(String role, String task, String context, String constraints) {
        return """
            # BEGINNING OF PROMPT

            ## YOUR ROLE
            You are an expert ${role}.
            
            ## CONTEXT
            <context_boundary>
            ${context}
            </context_boundary>
            
            ## TASK
            Your primary objective is:
            <objective>
            ${task}
            </objective>
            
            ## CONSTRAINTS
            To ensure the best output, you MUST adhere to these rules:
            ${constraints}
            - Always provide a "Confidence Score" (0-1) for your reasoning.
            - If the data in <context_boundary> is insufficient, state: ${insufficient-context}.
            
            ## OUTPUT FORMAT
            Return your response as a valid Markdown document with the following sections:
            ### Status, using format [STATUS: <status>]
            ### 🧠 Reasoning
            ### 🚀 Execution Plan
            ### 📊 Confidence Score, using format: Confidence Score (0-1)

            ## TERMINATION RULES
            If the task is finished, output: ${completed}
            If you are missing data, output: ${insufficient-context}
            If you are stuck in a logic loop, output: ${escalate}
            If you can't find a solution, output: ${failed}

            # END OF PROMPT
            """
            .replace("${role}", role)
            .replace("${task}", task)
            .replace("${context}", context)
            .replace("${constraints}", constraints)
            .replace("${completed}", Constants.AI_STATUS_COMPLETED)
            .replace("${insufficient-context}", Constants.AI_STATUS_INSUFFICIENT_CONTEXT)
            .replace("${escalate}", Constants.AI_STATUS_ESCALATE)
            .replace("${failed}", Constants.AI_STATUS_FAILED);
    }
}
