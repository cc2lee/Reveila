package com.reveila.ai;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.Subject;

import org.json.JSONObject;

import com.reveila.error.LlmException;
import com.reveila.system.Constants;
import com.reveila.system.InvocationTarget;
import com.reveila.system.Proxy;
import com.reveila.system.RolePrincipal;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;
import com.reveila.util.ExceptionCollection;
import com.reveila.util.json.JsonUtil;

public class AgenticFabric extends SystemComponent {

    private ManagedInvocation bridge;
    private AgentSessionManager sessionManager;
    private OrchestrationService orchestrationService;
    private MetadataRegistry metadataRegistry;
    private LlmProviderFactory llmFactory;
    private DynamicToolProvider toolProvider;
    private int aiLoopLimit = 5;
    private static boolean showReasoning = false;

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
        showReasoning = context.getProperties().getProperty("ai.show.reasoning", "false").equalsIgnoreCase("true");
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
     * @param userIntent   The user's prompt.
     * @param sessionId    Optional session ID to continue a conversation.
     * @param systemPrompt Optional initial system prompt (e.g., summary from
     *                     previous session).
     * @return A JSON object containing the result and the session id.
     */
    public JSONObject askAgent(String userIntent, String sessionId, String systemPrompt) {

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = java.util.UUID.randomUUID().toString();
        }

        AgentSession session = orchestrationService.getSession(sessionId);
        if (session == null) {
            session = orchestrationService.createSession(sessionId, sessionId);
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                session.getChatMemory()
                        .add(ReveilaMessage.system("Context carried over from previous session: " + systemPrompt));
            }
        }

        RolePrincipal principal = new RolePrincipal("ui-client");
        Subject subject = new Subject();
        subject.getPrincipals().add(principal);

        JSONObject jsonResponse = processIntent(session, subject, userIntent);
        String interpretation = interpretAiResponse(jsonResponse);
        JSONObject uiResponse = new JSONObject();
        uiResponse.put("answer", interpretation);
        uiResponse.put("sessionId", session.getSessionId());

        if (debug && logger != null) {
            logger.info("Final response to UI client: " + uiResponse.toString());
        }

        return uiResponse;
    }

    public static String interpretAiResponse(JSONObject jsonResponse) {
        String prettyAnswer = "";
        try {
            String status = jsonResponse.optString("status", "");
            String reasoning = jsonResponse.optString("reasoning", "");
            String result = jsonResponse.optString("result", "");

            if (reasoning.equals("null")) reasoning = "";
            if (result.equals("null")) result = "";
            
            if (status.equalsIgnoreCase(Constants.AI_STATUS_COMPLETED)
                    || status.equalsIgnoreCase(Constants.AI_STATUS_INSUFFICIENT_CONTEXT)) {
                if (showReasoning && reasoning != null && !reasoning.isBlank()) {
                    if (result != null && !result.isBlank()) {
                        prettyAnswer = result;
                        prettyAnswer = prettyAnswer + "\n\nReasoning: " + reasoning;
                    } else {
                        prettyAnswer = reasoning;
                    }
                } else if (result != null && !result.isBlank()) {
                    prettyAnswer = result;
                } else if (reasoning != null && !reasoning.isBlank()) {
                    prettyAnswer = reasoning;
                } else {
                    throw new IllegalStateException("AI indicated completion but did not provide reasoning or result.");
                }
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_ESCALATE)) {
                prettyAnswer = "I need authorization to proceed.";
                if (showReasoning && reasoning != null && !reasoning.isBlank()) {
                    prettyAnswer = prettyAnswer + "\n\nReasoning: " + reasoning;
                }
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_FAILED)) {
                if (reasoning != null && !reasoning.isBlank()) {
                    prettyAnswer = reasoning;
                } else {
                    throw new IllegalStateException("The AI indicated failure but did not provide reasoning.");
                }
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_TOOL_CALL)) {
                prettyAnswer = "Performing background actions...";
                if (showReasoning && reasoning != null && !reasoning.isBlank()) {
                    prettyAnswer = prettyAnswer + "\n\nReasoning: " + reasoning;
                }
            } else {
                throw new IllegalStateException("Unexpected status from AI response: " + String.valueOf(status));
            }
        } catch (Exception e) {
            prettyAnswer = e.getClass().getName() + e.getMessage() == null ? "" : ": " + e.getMessage();
            prettyAnswer = prettyAnswer + "\n\nOriginal AI response: ";
            prettyAnswer = prettyAnswer + jsonResponse.toString();
        }

        return prettyAnswer;
    }

    /**
     * Summarizes a session history for carry-over.
     */
    public String summarizeSession(String sessionId) {
        try {
            AgentSession session = orchestrationService.getSession(sessionId);
            if (session == null)
                return "";

            LlmProvider worker = llmFactory.getActiveProvider();
            if (worker == null)
                return "";

            String historyDump = session.getChatMemory().messages().stream()
                    .map(m -> m.role().name() + ": " + m.content())
                    .collect(java.util.stream.Collectors.joining("\n"));

            LlmRequest request = LlmRequest.builder()
                    .addMessage(ReveilaMessage.system(
                            "Summarize the following chat history briefly for context preservation in a new session."))
                    .addMessage(ReveilaMessage.user(historyDump))
                    .build();

            return worker.invoke(request).getContent();
        } catch (Exception e) {
            if (logger != null)
                logger.warning("Failed to summarize session: " + e.getMessage());
            return "";
        }
    }

    private JSONObject buildErrorResponse(String errorMessage) {
        JSONObject json = new JSONObject();
        json.put("status", Constants.AI_STATUS_FAILED);
        json.put("reasoning", errorMessage);
        json.put("result", "");
        json.put("confidence-score", 0.0);
        json.put("tool-call", new org.json.JSONArray());
        return json;
    }

    private void recordAuditLog(String action, String details) {
        try {
            Proxy p = context.getProxy("DataService");
            if (p != null) {
                java.util.Map<String, Object> log = new java.util.HashMap<>();
                log.put("action", action);
                log.put("details", details);
                log.put("timestamp", System.currentTimeMillis());
                p.invoke("save", new Object[] { "AuditLog", log });
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to write AuditLog: " + e.getMessage());
            }
        }
    }

    public JSONObject processIntent(AgentSession session, Subject subject, String intent) {

        Objects.requireNonNull(session, "AgentSession cannot be null.");
        Objects.requireNonNull(subject, "Subject cannot be null.");
        Objects.requireNonNull(intent, "Intent cannot be null.");

        if (logger != null) {
            logger.info("Processing intent: " + intent);
        }

        Set<Principal> principals = subject.getPrincipals();
        if (principals == null || principals.isEmpty()) {
            return buildErrorResponse("ERROR: Access Denied. No principals found in subject.");
        }

        boolean authorized = false;
        for (Principal p : principals) {
            if (p instanceof RolePrincipal rp) {
                String roleName = rp.getName();
                if ("ui-client".equalsIgnoreCase(roleName)
                        || "admin".equalsIgnoreCase(roleName)) {
                    authorized = true;
                    break;
                }
            }
        }

        if (!authorized) {
            if (logger != null) {
                logger.info("Access Denied. No authorized principals found in subject.");
            }
            return buildErrorResponse("ERROR: Access Denied. No authorized principals found in subject.");

        }

        String intentBuffer = intent;

        // Step 1: Execute initial LLM reasoning
        String response = null;
        try {
            response = askAi(session, intentBuffer);
        } catch (LlmException e) {
            response = "ERROR: Failed to get response from LLM: " + e.getMessage();
            if (logger != null) {
                logger.severe(response);
            }
            return buildErrorResponse(response);
        }

        // Step 2: The "AI Loop" - parse the response and
        // determine if further action is needed based on the defined output format in
        // the Prompt.
        response = JsonUtil.clean(response);
        AiResponseValidator validator = new AiResponseValidator();

        for (int loopCount = 0; loopCount < aiLoopLimit; loopCount++) {
            if (validator.getMessage(response) == null) {
                intentBuffer = "Invalid response from AI: " + response;
                try {
                    response = askAi(session, intentBuffer);
                    continue;
                } catch (LlmException e) {
                    response = "ERROR: Failed to get response from LLM: " + e.getMessage();
                    if (logger != null) {
                        logger.severe(response);
                    }
                    return buildErrorResponse(response);
                }
            }

            try {
                // Step 3: Handle terminal statuses defined in Prompt
                JSONObject jsonResponse = new JSONObject(response);
                String status = jsonResponse.optString("status", "");
                String reasoning = jsonResponse.optString("reasoning", "");
                // String result = jsonResponse.optString("result", "");
                // double confidenceScore = jsonResponse.optDouble("confidence-score", 0.0);
                // JSONObject toolCall = jsonResponse.optJSONObject("tool-call");

                if (status.equalsIgnoreCase(Constants.AI_STATUS_COMPLETED)) {
                    recordAuditLog("TASK_COMPLETED", reasoning);
                    return jsonResponse;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_INSUFFICIENT_CONTEXT)) {
                    recordAuditLog("INSUFFICIENT_CONTEXT", reasoning);
                    return jsonResponse;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_ESCALATE)) {
                    recordAuditLog("ESCALATED", reasoning);
                    return jsonResponse;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_FAILED)) {
                    recordAuditLog("TASK_FAILED", reasoning);
                    return jsonResponse;
                } else if (status.equalsIgnoreCase(Constants.AI_STATUS_TOOL_CALL)) {
                    try {
                        Object toolCallObj = jsonResponse.opt("tool-call");
                        if (toolCallObj == null) {
                            intentBuffer = "The AI indicated a tool call is needed but did not specify the tool or arguments. Reasoning provided: "
                                    + reasoning
                                    + " | Please clarify the tool to call and its arguments.";
                            recordAuditLog("TOOL_CALL_FAILED", "Missing tool/arguments.");
                        } else {
                            StringBuilder toolResults = new StringBuilder();
                            if (toolCallObj instanceof org.json.JSONArray toolCallArray) {
                                for (int i = 0; i < toolCallArray.length(); i++) {
                                    JSONObject singleToolCall = toolCallArray.getJSONObject(i);
                                    String result = handleToolCall(session, singleToolCall, response);
                                    toolResults.append("\n- Tool: ").append(singleToolCall.optString("method"))
                                            .append("\n  Result: ").append(result);
                                }
                            } else if (toolCallObj instanceof JSONObject singleToolCall) {
                                String result = handleToolCall(session, singleToolCall, response);
                                toolResults.append(result);
                            }

                            intentBuffer = "The AI has requested to call tools with the following details: "
                                    + toolCallObj.toString()
                                    + " | Reasoning provided: " + reasoning
                                    + " | Here are the results from the tool execution: " + toolResults.toString()
                                    + " | Please analyze this information and provide the next step or final answer.";
                            recordAuditLog("TOOL_CALLED", "Called tools: " + toolCallObj.toString());
                        }
                    } catch (Exception e) {
                        intentBuffer = "The AI requested a tool call, but the call could not be completed: "
                                + e.getMessage()
                                + " | Reasoning provided: " + reasoning
                                + " | Please clarify the tool to call and its arguments.";
                    }
                } else {
                    intentBuffer = "The AI provided an unexpected status: " + status
                            + " | Reasoning provided: " + reasoning
                            + " | Please analyze this information and provide the next step or final answer.";
                }

                try {
                    response = askAi(session, intentBuffer);
                } catch (LlmException e) {
                    response = "ERROR: Failed to get response from LLM: " + e.getMessage();
                    if (logger != null) {
                        logger.severe(response);
                    }
                    return buildErrorResponse(response);
                }
            } catch (Throwable t) {
                intentBuffer = "Exception occurred while processing response: "
                        + (t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString())
                        + "\nPlease analyze this information and provide the next step or final answer."
                        + "\nOriginal response: " + response;
                recordAuditLog("EXCEPTION", t.getMessage());
                try {
                    response = askAi(session, intentBuffer);
                } catch (LlmException e) {
                    response = "ERROR: Failed to get response from LLM: " + e.getMessage();
                    if (logger != null) {
                        logger.severe(response);
                    }
                    return buildErrorResponse(response);
                }
            }
        }

        return buildErrorResponse(
                "AI_LOOP_LIMIT_REACHED: The task could not be completed within the execution limit of " + aiLoopLimit
                        + " iteration(s).");
    }

    private String handleToolCall(AgentSession session, JSONObject toolCall, String response) throws Exception {

        Map<String, Object> bridgeArgs = new HashMap<>();
        bridgeArgs.put(AgentSession.ID, session.getSessionId());
        bridgeArgs.put(AgentSession.THOUGHT, toolCall);

        String pluginId = toolCall.optString("plugin", "");
        String methodName = toolCall.optString("method", "");

        InvocationTarget plugin = new InvocationTarget(
                UUID.fromString(session.getSessionId()),
                pluginId,
                "default",
                session.getParentTraceId() != null ? session.getParentTraceId() : UUID.randomUUID().toString());

        bridgeArgs.put("arguments", toolCall.optJSONObject("arguments"));

        // Invoke the tool via the bridge and capture the output
        // The bridge performs parsing, security audit, and tool execution
        SecurityPerimeter activePerimeter = null;
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest != null) {
            activePerimeter = manifest.defaultPerimeter();
        }

        InvocationResult result = bridge.invoke(plugin, activePerimeter, methodName.isEmpty() ? response : methodName, bridgeArgs);

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

            session.put("pendingApproval", result.callbackUrl());
            throw new com.reveila.error.SecurityException("APPROVAL_REQUIRED|" + result.message() + "|"
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
    private String askAi(AgentSession session, String userPrompt) throws LlmException {

        LlmProvider worker = llmFactory.getActiveProvider();
        if (worker == null) {
            String msg = "System Error: No active LLM Provider found.";
            if (logger != null)
                logger.severe(msg);
            return msg;
        }

        // Tool RAG Implementation:
        // Uses DynamicToolProvider to semantic search and rerank tools.
        java.util.List<LlmTool> tools;
        if (toolProvider != null) {
            tools = toolProvider.provideTools(userPrompt);
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
                        "Related Documents: " + searchKnowledgeVault(userPrompt),
                "");

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
        session.getChatMemory().add(ReveilaMessage.user(userPrompt));

        LlmRequest.Builder requestBuilder = LlmRequest.builder()
                .tools(tools);

        // Inject the dynamic system instructions as the first message for this request
        // (We don't save it to chat memory to avoid duplicating it on every loop)
        requestBuilder.addMessage(ReveilaMessage.system(systemInstructions));

        if (debug && logger != null) {
            logger.info("System Prompt used for request: " + systemInstructions);
        }

        for (ReveilaMessage msg : session.getChatMemory().messages()) {
            requestBuilder.addMessage(msg);
        }

        LlmRequest request = requestBuilder.build();

        String response = null;
        try {
            if (debug && logger != null) {
                logger.info("Invoking LLM provider [" + worker.getName() + "] with prompt: " + request.toString());
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
            if (p != null) {
                // KnowledgeVault component exists and has a search method
                Object result = p.invoke("search", new Object[] { query, 3 });
                String snippets = result != null ? result.toString() : "No relevant internal documents found.";
                if (debug && logger != null) {
                    logger.info("Knowledge Vault search result for [" + query + "]: " + snippets);
                }
                return snippets;
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Knowledge Vault search failed: " + e.getMessage());
            }
        }
        return "No relevant internal documents found.";
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
    public Object delegate(InvocationTarget parent, String targetIntent, Map<String, Object> taskArguments) {
        InvocationTarget child = parent
                .deriveChild("plugin-" + java.util.UUID.randomUUID().toString().substring(0, 4));

        // Maintain episodic memory by passing context from the parent trace
        Map<String, Object> parentContext = sessionManager.getContext(parent.getTraceId());
        sessionManager.saveContext(child.getTraceId(), parentContext);

        // Recursive call back into the bridge
        SecurityPerimeter activePerimeter = null;
        String pluginId = child.getTargetName();
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest != null) {
            activePerimeter = manifest.defaultPerimeter();
        }

        InvocationResult result = bridge.invoke(child, activePerimeter, targetIntent, taskArguments);

        if (result.status() == InvocationResult.Status.PENDING_APPROVAL) {
            return "DELEGATION_PAUSED: " + result.message() + " Approval required at: " + result.callbackUrl();
        }

        return result.data();
    }
}
