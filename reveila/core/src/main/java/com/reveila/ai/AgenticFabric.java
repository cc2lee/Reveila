package com.reveila.ai;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.reveila.error.LlmException;
import com.reveila.system.Constants;
import com.reveila.system.Plugin;
import com.reveila.system.Proxy;
import com.reveila.system.RolePrincipal;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;
import com.reveila.util.ExceptionCollection;

public class AgenticFabric extends SystemComponent {

    private ManagedInvocation bridge;
    private AgentSessionManager sessionManager;
    private OrchestrationService orchestrationService;
    private MetadataRegistry metadataRegistry;
    private LlmProviderFactory llmFactory;
    private DynamicToolProvider toolProvider;
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
            Proxy p = context.getProxy("ManagedInvocation");
            if (p instanceof SystemProxy sp) {
                this.bridge = (ManagedInvocation) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ManagedInvocation not found.", e);
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

        try {
            Proxy p = context.getProxy("DynamicToolProvider");
            if (p instanceof SystemProxy sp) {
                this.toolProvider = (DynamicToolProvider) sp.getInstance();
            }
        } catch (IllegalArgumentException e) {
            // Optional for now
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

        RolePrincipal principal = new RolePrincipal("ui-client");

        AgentSession session = orchestrationService.getSession(sessionId);
        if (session == null) {
            session = orchestrationService.createSession(sessionId);
        }

        return processIntent(session, principal, userIntent);
    }

    public String processIntent(AgentSession session, Principal principal, String intent) {

        //TODO: Does the principal have permission to invoke the agent? If not, return an error message. Security first!

        String modifiedIntent = intent;

        // Step 1: Execute initial LLM reasoning
        String response = null;
        try {
            response = askAi(session, modifiedIntent);
        } catch (LlmException e) {
            response = "ERROR: Failed to get response from LLM: " + e.getMessage();
            if (logger != null) {
                logger.severe(response);
            }
            return response;
        }

        // Step 2: The "AI Loop" - parse the response and
        // determine if further action is needed based on the defined output format in
        // the Prompt.
        AiResponseValidator validator = new AiResponseValidator();
        int loopCount = 0;
        while (validator.getMessage(response) != null && loopCount < aiLoopLimit) {
            loopCount++;
            try {
                // Step 3: Handle terminal statuses defined in Prompt
                JSONObject jsonResponse = new JSONObject(response);
                String status = jsonResponse.optString("status", "");
                String reasoning = jsonResponse.optString("reasoning", "");
                String result = jsonResponse.optString("result", "");
                double confidenceScore = jsonResponse.optDouble("confidence-score", 0.0);
                JSONObject toolCall = jsonResponse.optJSONObject("tool-call");

                if (status.equalsIgnoreCase(Constants.AI_STATUS_COMPLETED)) {
                    return response;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_ESCALATE)) {
                    return response;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_FAILED)) {
                    return response;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_TOOL_CALL)) {
                    try {
                        if (toolCall == null) {
                            modifiedIntent = "The AI indicated a tool call is needed but did not specify the tool or arguments. Reasoning provided: "
                                    + reasoning
                                    + ". Please clarify the tool to call and its arguments.";
                        } else {
                            String toolCallResult = handleToolCall(session, toolCall, response);
                            modifiedIntent = "The AI has requested to call a tool with the following details: "
                                    + toolCall.toString()
                                    + ". Reasoning provided: " + reasoning
                                    + ". Here are the results from the tool execution: " + toolCallResult
                                    + ". Please analyze this information and provide the next step or final answer.";
                        }
                    } catch (Exception e) {
                        modifiedIntent = "The AI requested a tool call, but the call could not be completed: "
                                + e.getMessage()
                                + ". Reasoning provided: " + reasoning
                                + ". Please clarify the tool to call and its arguments.";
                    }
                } else {
                    return response;
                }

                try {
                    response = askAi(session, modifiedIntent);
                } catch (LlmException e) {
                    response = "ERROR: Failed to get response from LLM: " + e.getMessage();
                    if (logger != null) {
                        logger.severe(response);
                    }
                    return response;
                }
            } catch (Throwable t) {
                String errorMsg = "Exception in AI Loop iteration " + loopCount + ": " + t.getMessage();
                if (logger != null) {
                    logger.severe(errorMsg);
                }
                return "ERROR: Agentic Loop failed due to a system exception: " + errorMsg;
            }
        }

        return "AI_LOOP_LIMIT_REACHED: The task could not be completed within the execution limit of " + aiLoopLimit
                + " iteration(s).";
    }

    private String handleToolCall(AgentSession session, JSONObject toolCall, String response) throws Exception {

        Plugin plugin = null;

        Map<String, Object> bridgeArgs = new HashMap<>();
        bridgeArgs.put("_session_id", session.getSessionId());
        bridgeArgs.put("_thought", toolCall);

        // TODO: Implement a more robust mapping from toolCall to plugin and method, potentially using a registry or dynamic discovery mechanism.
        // For now, we will assume the toolCall JSON has a "plugin" field and an "arguments" field.
        String pluginId = toolCall.optString("plugin", "");
        String methodName = toolCall.optString("method", "");
        // plugin = session.getPlugin(pluginId);
        bridgeArgs.put("arguments", toolCall.optJSONObject("arguments"));

        // Step 4: Invoke the tool via the bridge and capture the output
        // The bridge performs parsing, security audit, and tool execution
        InvocationResult result = bridge.invoke(plugin, null, response, bridgeArgs);

        if (result.status() == InvocationResult.Status.SUCCESS) {
            // Step 4: Capture tool output and feed it back for the next reasoning iteration
            String toolResult = result.data() != null ? result.data().toString()
                    : "Action completed successfully.";

            // Explicitly log the tool execution in chat memory for context
            session.getChatMemory()
                    .add(ReveilaMessage.assistant("Reasoning: Action required. Initiating tool execution."));
            session.getChatMemory().add(ReveilaMessage.tool("The tool has returned: " + toolResult));

            return toolResult;

        } else if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            // Step 5 (HITL): Pause the loop for user approval
            session.getChatMemory().add(
                    ReveilaMessage
                            .assistant("Task requires approval."));
            
            // TODO: Implement a callback mechanism to resume the loop once approval is granted.
            throw new SecurityException("APPROVAL_REQUIRED|" + result.message() + "|"
                    + (result.data() != null ? result.data().toString() : ""));
        } else {
            throw new RuntimeException("Tool execution failed: " + result.message());
        }
    }

    /**
     * Internal call to the LLM Provider to get a single reasoning response.
     * 
     * @throws LlmException
     */
    private String askAi(AgentSession session, String prompt) throws LlmException {

        // TODO: Does the principal have permission to invoke the agent? If not, throw a security exception. Security first!
        
        LlmProvider worker = llmFactory.getActiveProvider();
        if (worker == null) {
            String msg = "System Error: No active LLM Provider found.";
            if (logger != null)
                logger.severe(msg);
            return msg;
        }

        // Tool RAG Implementation:
        // TODO: Use DynamicToolProvider to semantic search and rerank tools.
        java.util.List<LlmTool> tools;
        if (toolProvider != null) {
            tools = toolProvider.provideTools(prompt);
        } else {
            // Fallback to manual mapping from full registry (legacy)
            Map<String, Object> mcpData = metadataRegistry.exportToMCP();
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> mcpTools = (java.util.List<Map<String, Object>>) mcpData.get("tools");
            tools = new java.util.ArrayList<>();
            if (mcpTools != null) {
                for (Map<String, Object> toolMap : mcpTools) {
                    LlmTool tool = new LlmTool();
                    tool.setName((String) toolMap.get("name"));
                    tool.setDescription((String) toolMap.get("description"));
                    Object inputSchema = toolMap.get("inputSchema");
                    if (inputSchema instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> schemaMap = (Map<String, Object>) inputSchema;
                        tool.setParameterSchema(schemaMap);
                    }
                    tools.add(tool);
                }
            }
        }

        // Generate the dynamic system instruction
        String systemInstructions = Prompt.getSystemPrompt(
                "Reveila AI Agent",
                "Available Tools: "
                        + tools.stream().map(LlmTool::getName).collect(java.util.stream.Collectors.joining(", "))
                        + "\n\n" +
                        "Related Documents: " + searchKnowledgeVault(prompt),
                "Adhere to Agency Perimeter security constraints.");

        // Optimization: Context Window Management (Summary Strategy)
        if ("cost".equalsIgnoreCase(orchestrationService.getOptimizationPriority())) {
            int historySize = session.getChatMemory().messages().size();
            if (historySize > 10) {

                String historyDump = session.getChatMemory().messages().toString();

                LlmRequest summaryRequest = LlmRequest.builder()
                        .addMessage(ReveilaMessage.system("System"))
                        .addMessage(ReveilaMessage
                                .user("Summarize the following chat history for context preservation: " + historyDump))
                        .build();

                String summary;
                try {
                    summary = worker.invoke(summaryRequest).getContent();
                } catch (Exception e) {
                    summary = "ERROR summarising context: " + e.getMessage();
                }

                session.getChatMemory().clear();
                session.getChatMemory().add(ReveilaMessage.system("Summary of previous conversation: " + summary));
            }
        }

        // Maintain the chain of thought in the session chat memory
        session.getChatMemory().add(ReveilaMessage.system(systemInstructions));
        session.getChatMemory().add(ReveilaMessage.user(prompt));

        LlmRequest request = LlmRequest.builder()
                .addMessage(ReveilaMessage.system("You are the Reveila AI Agent."))
                .addMessage(ReveilaMessage.user(prompt))
                .tools(tools)
                .build();

        String response = null;
        try {
            if (debug && logger != null) {
                logger.info("Invoking LLM provider [" + worker.getName() + "] with prompt: " + prompt);
            }
            response = worker.invoke(request).getContent();
            if (debug && logger != null) {
                logger.info("Received response from LLM provider [" + worker.getName() + "]: " + response);
            }
        } catch (Exception e) {
            ExceptionCollection ec = new ExceptionCollection();
            ec.addException(e);
            Throwable t = e.getCause();
            while (t != null) {
                ec.addException(t);
                t = t.getCause();
            }
            throw new LlmException("LLM Invocation Failure, caused by: " + ec.toString());
        } finally {
            // Record the raw response in history before returning it to the loop
            try {
                session.getChatMemory()
                        .add(ReveilaMessage.assistant(response != null ? response : "No response received from LLM."));
            } catch (Exception e) {
                if (logger != null) {
                    logger.severe("Error recording response in chat memory: " + e.getMessage());
                }
            }
        }

        return response;
    }

    /**
     * Semantic search for relevant documents in the Knowledge Vault.
     * 
     * @param query The user's message.
     * @return A formatted string of relevant snippets.
     */
    private String searchKnowledgeVault(String query) {
        try {
            Proxy p = context.getProxy("KnowledgeVault");
            if (p instanceof SystemProxy sp) {
                // Assuming KnowledgeVault component exists and has a search method
                Object result = sp.invoke("search", new Object[] { query, 3 });
                return result != null ? result.toString() : "No relevant internal documents found.";
            }
        } catch (Exception e) {
            // Silently fail if Vault is not available
        }
        return "Knowledge Vault is offline.";
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
    public Object delegate(Plugin parent, String targetIntent, Map<String, Object> taskArguments) {
        Plugin child = parent
                .deriveChild("plugin-" + java.util.UUID.randomUUID().toString().substring(0, 4));

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
