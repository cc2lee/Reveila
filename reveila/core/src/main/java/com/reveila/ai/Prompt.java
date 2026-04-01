package com.reveila.ai;

public class Prompt {

    /**
     * Generates a base system prompt for the AI agent.
     *
     * @param role The role the AI should assume.
     * @param task The primary task for the AI.
     * @param context Additional context for the task.
     * @param constraints Rules and limitations for the AI.
     * @return A formatted system prompt.
     */
    public static String getBasePrompt(String role, String task, String context, String constraints) {
        return """
            # ROLE
            You are an expert ${role}. 
            
            # CONTEXT
            <context_boundary>
            ${context}
            </context_boundary>
            
            # TASK
            Your primary objective is:
            <objective>
            ${task}
            </objective>
            
            # CONSTRAINTS
            To ensure enterprise-grade output, you MUST adhere to these rules:
            ${constraints}
            - Always provide a "Confidence Score" (0-1) for your reasoning.
            - If the data in <context_boundary> is insufficient, state: "INSUFFICIENT_CONTEXT".
            
            # OUTPUT_FORMAT
            Return your response as a valid Markdown document with the following sections:
            ## 🧠 Reasoning
            ## 🚀 Execution_Plan
            ## 📊 Confidence_Report

            # TERMINATION_RULES
            If the task is finished, output: [STATUS: SUCCESS]
            If you are missing data, output: [STATUS: INSUFFICIENT_CONTEXT]
            If you are stuck in a logic loop, output: [STATUS: ESCALATE]
            """
            .replace("${role}", role)
            .replace("${task}", task)
            .replace("${context}", context)
            .replace("${constraints}", constraints);
    }
}
