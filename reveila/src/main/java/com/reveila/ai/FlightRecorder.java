package com.reveila.ai;

import java.util.Map;

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
     * @param principal The agent principal performing the action.
     * @param stepName The name of the reasoning step.
     * @param data Metadata or data associated with the step.
     */
    void recordStep(AgentPrincipal principal, String stepName, Map<String, Object> data);

    /**
     * Records the output of a tool/plugin execution.
     *
     * @param principal The agent principal.
     * @param toolName The name of the tool called.
     * @param output The raw or processed output.
     */
    void recordToolOutput(AgentPrincipal principal, String toolName, Object output);
}
