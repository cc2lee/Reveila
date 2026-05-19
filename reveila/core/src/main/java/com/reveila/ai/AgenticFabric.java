package com.reveila.ai;

import java.util.logging.Level;

import javax.security.auth.Subject;

import org.json.JSONObject;

import com.reveila.system.Constants;
import com.reveila.system.Proxy;
import com.reveila.system.RolePrincipal;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

public class AgenticFabric extends SystemComponent {

    public static final String COMPONENT_NAME = "AgenticFabric";

    private static final String STATUS = "status";
    private static final String REASONING = "reasoning";
    private static final String RESULT = "result";
    private static final String CONFIDENCE_SCORE = "confidence-score";
    private static final String TOOL_CALL = "tool-call";
    
    /**
     * Normalizes a raw JSON string value: treats the literal JSON token "null"
     * as an empty string, and leaves everything else as-is.
     */
    private static String sanitizeAiValue(String value) {
        return "null".equals(value) ? "" : value;
    }
    /**
     * Formats an exception into a user-facing error string that includes
     * the original AI response JSON for transparency.
     */
    private static String formatErrorResponse(Exception e, JSONObject jsonResponse) {
        String errorMsg = e.getClass().getName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return errorMsg + "\n\nOriginal AI response: " + jsonResponse.toString();
    }
    private ManagedInvocation bridge;
    private AgentSessionManager sessionManager;
    private OrchestrationService orchestrationService;
    private MetadataRegistry metadataRegistry;
    private LlmProviderFactory llmFactory;
    private DynamicToolProvider toolProvider;

    private int aiLoopLimit = 5;

    private boolean showReasoning = false;

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
            logger.log(Level.INFO, "Final response to UI client: {0}", uiResponse);
        }

        return uiResponse;
    }

    public String interpretAiResponse(JSONObject jsonResponse) {
        try {
            String status = jsonResponse.optString(STATUS, "");
            String reasoning = sanitizeAiValue(jsonResponse.optString(REASONING, ""));
            String result = sanitizeAiValue(jsonResponse.optString(RESULT, ""));

            if (status.equalsIgnoreCase(Constants.AI_STATUS_COMPLETED)
                    || status.equalsIgnoreCase(Constants.AI_STATUS_INSUFFICIENT_CONTEXT)) {
                return handleCompletedOrInsufficient(reasoning, result);
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_ESCALATE)) {
                return handleEscalate(reasoning);
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_FAILED)) {
                return handleFailed(reasoning);
            } else if (status.equalsIgnoreCase(Constants.AI_STATUS_TOOL_CALL)) {
                return handleToolCallStatus(reasoning);
            } else {
                throw new IllegalStateException(
                        "Unexpected status from AI response: " + String.valueOf(status));
            }
        } catch (Exception e) {
            return formatErrorResponse(e, jsonResponse);
        }
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

    @Override
    protected void onStop() throws Exception {
    }

    /**
     * Appends the AI's reasoning to the message when the {@code showReasoning}
     * flag is enabled and reasoning text is available.
     */
    private String appendReasoningIfVisible(String message, String reasoning) {
        if (showReasoning && !reasoning.isBlank()) {
            return message + "\n\nReasoning: " + reasoning;
        }
        return message;
    }

    /**
     * Handles the COMPLETED and INSUFFICIENT_CONTEXT statuses.
     * Returns the result text (preferring {@code result} over {@code reasoning})
     * with optional reasoning appended when showReasoning is enabled.
     */
    private String handleCompletedOrInsufficient(String reasoning, String result) {
        if (!result.isBlank()) {
            return appendReasoningIfVisible(result, reasoning);
        }
        if (!reasoning.isBlank()) {
            return reasoning;
        }
        throw new IllegalStateException(
                "AI indicated completion but did not provide reasoning or result.");
    }

    /**
     * Handles the ESCALATE status by returning a standard authorization
     * message, optionally decorated with the AI's reasoning.
     */
    private String handleEscalate(String reasoning) {
        return appendReasoningIfVisible("I need authorization to proceed.", reasoning);
    }

    /**
     * Handles the FAILED status by returning the AI's reasoning,
     * or throwing if no reasoning is available.
     */
    private String handleFailed(String reasoning) {
        if (!reasoning.isBlank()) {
            return reasoning;
        }
        throw new IllegalStateException(
                "The AI indicated failure but did not provide reasoning.");
    }

    /**
     * Handles the TOOL_CALL status by returning a standard background-action
     * message, optionally decorated with the AI's reasoning.
     */
    private String handleToolCallStatus(String reasoning) {
        return appendReasoningIfVisible("Performing background actions...", reasoning);
    }

    private JSONObject buildErrorResponse(String errorMessage) {
        JSONObject json = new JSONObject();
        json.put(STATUS, Constants.AI_STATUS_FAILED);
        json.put(REASONING, errorMessage);
        json.put(RESULT, "");
        json.put(CONFIDENCE_SCORE, 0.0);
        json.put(TOOL_CALL, new org.json.JSONArray());
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
}
