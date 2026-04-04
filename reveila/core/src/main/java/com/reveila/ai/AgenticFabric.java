package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

import com.reveila.system.PluginPrincipal;
import com.reveila.system.Proxy;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Phase 5: Collaboration (The Agentic Fabric).
 * Manages multi-agent workflows and verticalized skill sets.
 * Uses Redis for context persistence across execution boundaries.
 * 
 * @author CL
 */
public class AgenticFabric extends SystemComponent {

    private SafeInvocation bridge;
    private AgentSessionManager sessionManager;
    private OrchestrationService orchestrationService;
    private MetadataRegistry metadataRegistry;
    private LlmProviderFactory llmFactory;
    private int aiLoopLimit = 5;

    public AgenticFabric() {
        // Wired in onStart
    }

    public int getAiLoopLimit() {
        return aiLoopLimit;
    }

    public void setAiLoopLimit(int aiLoopLimit) {
        this.aiLoopLimit = aiLoopLimit;
    }

    @Override
    public void onStart() throws Exception {
        try {
            Proxy p = context.getProxy("SafeInvocation");
            if (p instanceof SystemProxy sp) {
                this.bridge = (SafeInvocation) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SafeInvocation not found.", e);
        }

        try {
            Proxy p = context.getProxy("AgentSessionManager");
            if (p instanceof SystemProxy sp) {
                this.sessionManager = (AgentSessionManager) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AgentSessionManager not found.", e);
        }

        try {
            Proxy p = context.getProxy("OrchestrationService");
            if (p instanceof SystemProxy sp) {
                this.orchestrationService = (OrchestrationService) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("OrchestrationService not found.", e);
        }

        try {
            Proxy p = context.getProxy("MetadataRegistry");
            if (p instanceof SystemProxy sp) {
                this.metadataRegistry = (MetadataRegistry) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MetadataRegistry not found.", e);
        }

        try {
            Proxy p = context.getProxy("LlmProviderFactory");
            if (p instanceof SystemProxy sp) {
                this.llmFactory = (LlmProviderFactory) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LlmProviderFactory not found.", e);
        }
    }

    /**
     * Exposes a simple entry point for UI clients to talk to the agent.
     * 
     * @param userIntent The user's prompt.
     * @param sessionId  Optional session ID to continue a conversation.
     * @return The final answer from the LLM.
     */
    public String askAgent(String userIntent, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }

        PluginPrincipal principal = PluginPrincipal.create("ui-client", "system");

        AgentSession session = orchestrationService.getSession(sessionId);
        if (session == null) {
            session = orchestrationService.createSession(principal.getTraceId());
        }

        return processIntent(session, principal, userIntent);
    }

    public String processIntent(AgentSession session, PluginPrincipal principal, String initialIntent) {
        String currentIntent = initialIntent;

        // Step 1: Execute initial LLM reasoning
        String response = askAi(session, principal, currentIntent);
        AiResponseValidator validator = new AiResponseValidator();
        int loopCount = 0;
        while (validator.getMessage(response) != null && loopCount < aiLoopLimit) {
            loopCount++;
            try {
                // Step 2: Handle terminal statuses defined in Prompt.getBasePrompt()
                if (response.contains("[STATUS: COMPLETED]") || response.contains("## Status: COMPLETED")) {
                    return response; // AI said "Done"
                } else if (response.contains("[STATUS: INSUFFICIENT_CONTEXT]")
                        || response.contains("## Status: INSUFFICIENT_CONTEXT")) {
                    // In a real scenario, we might trigger a context-gathering tool here
                    return response; // Let the message sender decides what to do
                } else if (response.contains("[STATUS: ESCALATE]") || response.contains("## Status: ESCALATE")) {
                    return "APPROVAL_REQUIRED|" + "[STATUS: ESCALATE]" + "|" + response;
                }

                // Step 3: If not terminal, interpret the response as a request for action
                Map<String, Object> bridgeArgs = new HashMap<>();
                bridgeArgs.put("_session_id", session.getSessionId());
                bridgeArgs.put("_thought", response);

                // The bridge performs parsing, security audit, and tool execution
                InvocationResult result = bridge.invoke(principal, null, response, bridgeArgs);

                if (result.status() == InvocationResult.Status.SUCCESS) {
                    // Step 4: Capture tool output and feed it back for the next reasoning iteration
                    String toolResult = result.data() != null ? result.data().toString()
                            : "Action completed successfully.";

                    // Explicitly log the tool execution in chat memory for context
                    session.getChatMemory()
                            .add(AiMessage.from("Reasoning: Action required. Initiating tool execution."));
                    session.getChatMemory().add(ToolExecutionResultMessage.from("tool-id", response, toolResult));

                    currentIntent = "The tool has returned the following result: " + toolResult
                            + ". Please analyze this and provide the next step or final answer.";
                } else if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
                    // Step 5 (HITL): Pause the loop for user approval
                    session.getChatMemory().add(
                            AiMessage.from("Reasoning: Task requires a protected script. Awaiting user approval."));
                    return "APPROVAL_REQUIRED|" + result.message() + "|"
                            + (result.data() != null ? result.data().toString() : "");
                } else {
                    // Handle tool failure as context for the AI
                    currentIntent = "The tool execution failed: " + result.message()
                            + ". Please suggest a workaround or report the error.";
                }
                response = askAi(session, principal, currentIntent);
            } catch (Exception e) {
                if (logger != null) {
                    logger.severe("Agentic Loop failure on iteration " + loopCount + ": " + e.getMessage());
                }
                return "ERROR: Agentic Loop failed due to a system exception: " + e.getMessage();
            }
        }

        if (loopCount >= aiLoopLimit) {
            return "AI_LOOP_LIMIT_REACHED: The task could not be completed within the execution limit of " + aiLoopLimit
                    + " iteration(s).";
        } else {
            return "FAILED: The quality of the task could not be determined.";
        }
    }

    /**
     * Internal call to the LLM Provider to get a single reasoning response.
     */
    private String askAi(AgentSession session, PluginPrincipal principal, String userIntent) {
        // Gather current tool definitions for context
        Map<String, Object> toolDefinitions = metadataRegistry.exportToMCP();

        // Generate the dynamic system instruction
        String systemInstructions = Prompt.getPrompt(
                "Reveila AI Agent",
                userIntent,
                "Available Tool Definitions (MCP): " + toolDefinitions,
                "Adhere to Agency Perimeter security constraints.");

        // Optimization: Context Window Management (Summary Strategy)
        if ("cost".equalsIgnoreCase(orchestrationService.getOptimizationPriority())) {
            int historySize = session.getChatMemory().messages().size();
            if (historySize > 10) {
                LlmProvider worker = llmFactory.getProvider("openai");
                String historyDump = session.getChatMemory().messages().toString();
                String summary = worker.generateResponse(
                        "Summarize the following chat history for context preservation: " + historyDump, "System");

                session.getChatMemory().clear();
                session.getChatMemory().add(SystemMessage.from("Summary of previous conversation: " + summary));
            }
        }

        // Maintain the chain of thought in the session chat memory
        session.getChatMemory().add(SystemMessage.from(systemInstructions));
        session.getChatMemory().add(UserMessage.from(userIntent));

        // Invoke the LLM Provider (Primary model)
        LlmProvider worker = llmFactory.getProvider("openai");
        String response = worker.generateResponse(userIntent, "You are the Reveila AI Agent.");

        // Record the raw response in history before returning it to the loop
        session.getChatMemory().add(AiMessage.from(response));

        return response;
    }

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Allows a Manager agent to delegate tasks to Worker agents.
     * Implements the Agent-to-Agent (A2A) Bridge via recursive invocation.
     *
     * @param parent        The calling agent principal.
     * @param targetIntent  The intent for the worker agent.
     * @param taskArguments The task-specific arguments.
     * @return The result of the delegated task.
     */
    public Object delegate(PluginPrincipal parent, String targetIntent, Map<String, Object> taskArguments) {
        PluginPrincipal child = parent
                .deriveChild("worker-agent-" + java.util.UUID.randomUUID().toString().substring(0, 4));

        // Maintain episodic memory by passing context from the parent trace
        Map<String, Object> parentContext = sessionManager.getContext(parent.getTraceId());
        sessionManager.saveContext(child.getTraceId(), parentContext);

        // Recursive call back into the bridge
        InvocationResult result = bridge.invoke(child, null, targetIntent, taskArguments);

        if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            return "DELEGATION_PAUSED: " + result.message() + " Approval required at: " + result.callbackUrl();
        }

        return result.data();
    }
}
