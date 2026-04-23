package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

import com.reveila.system.Plugin;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

/**
 * Sovereign Service for receiving external task management webhooks.
 * ADR 0006: Realigned to use Proxy-based invocation pattern.
 */
public class InboundWebhookService extends SystemComponent {

    private ManagedInvocation bridge;
    private OrchestrationService orchestrationService;
    private FlightRecorder flightRecorder;
    private LlmProviderFactory llmFactory;

    @Override
    protected void onStart() throws Exception {
        this.bridge = (ManagedInvocation) ((SystemProxy) context.getProxy("ManagedInvocation")).getInstance();
        this.orchestrationService = (OrchestrationService) ((SystemProxy) context.getProxy("OrchestrationService")).getInstance();
        this.flightRecorder = (FlightRecorder) ((SystemProxy) context.getProxy("FlightRecorder")).getInstance();
        this.llmFactory = (LlmProviderFactory) ((SystemProxy) context.getProxy("LlmProviderFactory")).getInstance();
    }

    @Override
    protected void onStop() throws Exception {}

    public InvocationResult ingest(Map<String, Object> payload) {
        String source = (String) payload.getOrDefault("trigger_source", "unknown");
        String perimeter = (String) payload.getOrDefault("agency_perimeter", "default");
        
        LlmProvider worker = llmFactory.getActiveProvider();
        if (worker == null) {
            String msg = "System Error: No active LLM Provider found.";
            if (logger != null)
                logger.severe(msg);
            return InvocationResult.error(msg);
        }
        
        LlmRequest request = LlmRequest.builder()
                .addMessage(ReveilaMessage.system("You are a Specialized Worker. Map the following context to a Reveila plugin intent. Return JSON."))
                .addMessage(ReveilaMessage.user(payload.getOrDefault("context", "{}").toString()))
                .build();

        try {
            worker.invoke(request).getContent();
        } catch (Exception e) {
            // Ignore for now
        }

        ToolCall toolCall = new ToolCall();
        toolCall.setFunctionName("webhook-agent-" + source);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", Map.of());
        
        Map<String, Object> toolArgs = new HashMap<>();
        toolArgs.put("method", "external-ingestion");
        toolArgs.putAll(context);
        toolCall.setArguments(toolArgs);

        String tenantId = this.context != null && this.context.getProperties() != null ? this.context.getProperties().getProperty("tenant-id", "default-tenant") : "default-tenant";

        Plugin plugin = new Plugin(
            java.util.UUID.randomUUID(),
            toolCall.getFunctionName(),
            tenantId,
            java.util.UUID.randomUUID().toString()
        );
        AgentSession session = orchestrationService.createSession(plugin.getTraceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));

        flightRecorder.recordStep(plugin, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
        ));

        String action = (String) context.getOrDefault("required_action", "generic_task");
        
        String mappedIntent = action;
        if ("extract_liabilities".equals(action)) {
            mappedIntent = "doc_extraction.extract";
        }

        Map<String, Object> args = new HashMap<>();
        args.put(AgentSession.ID, session.getSessionId());
        args.put("traceId", plugin.getTraceId());
        args.put(AgentSession.THOUGHT, "Worker processing Filo task: " + payload.get("task_id"));
        args.put("arguments", toolCall.getArguments());

        return bridge.invoke(toolCall, null, mappedIntent, args);
    }
}
