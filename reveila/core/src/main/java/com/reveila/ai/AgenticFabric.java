package com.reveila.ai;

import java.util.Map;

import com.reveila.system.PluginPrincipal;
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
public class AgenticFabric extends com.reveila.system.AbstractService {
    
    private InvocationBridge bridge;
    private AgentSessionManager sessionManager;
    private OrchestrationService orchestrationService;
    private MetadataRegistry metadataRegistry;
    private LlmProviderFactory llmFactory;

    public AgenticFabric() {
        // Wired in onStart
    }

    @Override
    public void onStart() throws Exception {
        this.bridge = context.getProxy("InvocationBridge")
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy sp) {
                            return (InvocationBridge) sp.getInstance();
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new IllegalStateException("InvocationBridge not found."));

        this.sessionManager = context.getProxy("AgentSessionManager")
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy sp) {
                            return (AgentSessionManager) sp.getInstance();
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new IllegalStateException("AgentSessionManager not found."));

        this.orchestrationService = context.getProxy("OrchestrationService")
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy sp) {
                            return (OrchestrationService) sp.getInstance();
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new IllegalStateException("OrchestrationService not found."));

        this.metadataRegistry = context.getProxy("MetadataRegistry")
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy sp) {
                            return (MetadataRegistry) sp.getInstance();
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new IllegalStateException("MetadataRegistry not found."));

        this.llmFactory = context.getProxy("LlmProviderFactory")
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy sp) {
                            return (LlmProviderFactory) sp.getInstance();
                        }
                        return null;
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseThrow(() -> new IllegalStateException("LlmProviderFactory not found."));
    }

    /**
     * Exposes a simple entry point for UI clients to talk to the agent.
     * 
     * @param userIntent The user's prompt.
     * @param sessionId Optional session ID to continue a conversation.
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

        return processLoop(session, principal, userIntent);
    }

    /**
     * Executes the "Reveila AI Loop" for a specific session.
     * Implements the 5-step loop defined in the task requirements.
     * 
     * @param session    The active AgentSession (Persisted topic).
     * @param principal  The agent principal.
     * @param userIntent The latest user task.
     * @return The final answer from the LLM.
     */
    public String processLoop(AgentSession session, PluginPrincipal principal, String userIntent) {
        // Step 1: Reveila sends "Tool Definition" + "System Instructions" + "User Intent"
        Map<String, Object> toolDefinitions = metadataRegistry.exportToMCP();
        
        // Use Prompt utility to generate Markdown+XML formatted system instruction
        String systemInstructions = Prompt.getBasePrompt(
            "Reveila AI Agent", 
            userIntent, 
            "Available Tool Definitions (MCP): " + toolDefinitions, 
            "Adhere to Agency Perimeter security constraints."
        );
        
        // Context Window Management (Optimization Strategy)
        if ("cost".equalsIgnoreCase(orchestrationService.getOptimizationPriority())) {
            int historySize = session.getChatMemory().messages().size();
            if (historySize > 10) {
                System.out.println("[OPTIMIZATION] Chat history size (" + historySize + ") exceeds cost threshold. Summarizing...");
                
                // Use the specialized worker to summarize the history
                LlmProvider worker = llmFactory.getProvider("openai");
                String historyDump = session.getChatMemory().messages().toString();
                String summary = worker.generateResponse("Summarize the following chat history for context preservation: " + historyDump, "System");
                
                session.getChatMemory().clear();
                session.getChatMemory().add(SystemMessage.from("Summary of previous conversation: " + summary));
            }
        }

        // Update Session History (Persisted Context)
        session.getChatMemory().add(SystemMessage.from(systemInstructions));
        session.getChatMemory().add(UserMessage.from(userIntent));

        // Step 2: LLM processes "Reasoning" and returns "Actions" (Simulated via Bridge)
        // Step 3: Reveila parses JSON, executes the tool-call, and captures the result
        
        Map<String, Object> bridgeArgs = new java.util.HashMap<>();
        bridgeArgs.put("_session_id", session.getSessionId());
        bridgeArgs.put("_thought", "Reasoning loop for intent: " + userIntent);

        // The bridge performs parsing, security audit, and execution (Step 3)
        InvocationResult result = bridge.invoke(principal, null, userIntent, bridgeArgs);

        if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            // Step 4 (HITL): Capture the pending approval in the message chain
            session.getChatMemory().add(AiMessage.from("Reasoning: Task requires a dynamic script. Awaiting user approval for: " + userIntent));
            
            // Return structured info about the pending approval
            return "APPROVAL_REQUIRED|" + result.message() + "|" + result.data().toString();
        }

        if (result.status() == InvocationResult.Status.SUCCESS) {
            // Step 2 (Simplified/Simulated): Capture the AI's intent to use a tool
            session.getChatMemory().add(AiMessage.from("Reasoning: Task requires tool execution. Initiating tool-call for " + userIntent));

            // Step 5: Reveila sends the result back to the LLM to "close the loop"
            String toolResult = result.data() != null ? result.data().toString() : "Action completed successfully.";
            session.getChatMemory().add(ToolExecutionResultMessage.from("tool-id", userIntent, toolResult));
            
            // Closing the loop: Call the LLM with the full message chain to get the final answer
            LlmProvider worker = llmFactory.getProvider("openai");
            String finalAnswer = worker.generateResponse(
                "The tool has returned the following result: " + toolResult + ". Please provide a final answer to the user based on the full conversation history.",
                "You are the Reveila AI Agent closing the loop."
            );
            
            session.getChatMemory().add(AiMessage.from(finalAnswer));
            return finalAnswer;
        }

        return "Loop terminated with status: " + result.status() + " - " + result.message();
    }

    @Override
    protected void onStop() throws Exception {
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
    public Object delegate(PluginPrincipal parent, String targetIntent, Map<String, Object> taskArguments) {
        PluginPrincipal child = parent.deriveChild("worker-agent-" + java.util.UUID.randomUUID().toString().substring(0,4));
        
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
