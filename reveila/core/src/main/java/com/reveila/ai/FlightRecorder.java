package com.reveila.ai;

import java.util.Map;

import com.reveila.system.Plugin;

/**
 * Asynchronous logging of the agent's reasoning chain and tool outputs.
 * Provides forensic auditability for autonomous decisions.
 * 
 * @author CL
 */
public interface FlightRecorder {
    /**
     * Records a step in the agent's reasoning process.
     *
     * @param plugin The agent plugin performing the action.
     * @param stepName The name of the reasoning step.
     * @param data Metadata or data associated with the step.
     */
    void recordStep(Plugin plugin, String stepName, Map<String, Object> data);

    /**
     * Records the reasoning trace (the 'thought' process) of the agent.
     *
     * @param plugin The agent plugin.
     * @param reasoning The natural language reasoning for the action.
     */
    void recordReasoning(Plugin plugin, String reasoning);

    /**
     * Records the output of a tool/plugin execution.
     *
     * @param plugin The agent plugin.
     * @param toolName The name of the tool called.
     * @param output The raw or processed output.
     */
    void recordToolOutput(Plugin plugin, String toolName, Object output);

    /**
     * Captures forensic metadata for execution audit.
     *
     * @param plugin The agent plugin.
     * @param metadata Map containing exit code, duration, memory peak, etc.
     */
    void recordForensicMetadata(Plugin plugin, Map<String, Object> metadata);
}
