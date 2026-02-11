package com.reveila.ai;

import java.util.Map;

/**
 * Phase 5: Collaboration (The Agentic Fabric).
 * Manages multi-agent workflows and verticalized skill sets.
 * Uses Redis for context persistence across execution boundaries.
 * 
 * @author CL
 */
public class AgenticFabric {
    
    private final UniversalInvocationBridge bridge;
    private final AgentSessionManager sessionManager;

    public AgenticFabric(UniversalInvocationBridge bridge, AgentSessionManager sessionManager) {
        this.bridge = bridge;
        this.sessionManager = sessionManager;
    }

    /**
     * Allows a Manager agent to delegate tasks to Worker agents.
     * Implements the Agent-to-Agent (A2A) Bridge via recursive invocation.
     *
     * @param parent The calling agent principal.
     * @param targetIntent The intent for the worker agent.
     * @param taskArguments The task-specific arguments.
     * @return The result of the delegated task.
     */
    public Object delegate(AgentPrincipal parent, String targetIntent, Map<String, Object> taskArguments) {
        AgentPrincipal child = parent.deriveChild("worker-agent-" + java.util.UUID.randomUUID().toString().substring(0,4));
        
        // Maintain episodic memory by passing context from the parent trace
        Map<String, Object> parentContext = sessionManager.getContext(parent.traceId());
        sessionManager.saveContext(child.traceId(), parentContext);

        // Recursive call back into the bridge
        InvocationResult result = bridge.invoke(child, null, targetIntent, taskArguments);
        
        if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            return "DELEGATION_PAUSED: " + result.message() + " Approval required at: " + result.callbackUrl();
        }
        
        return result.data();
    }
}
