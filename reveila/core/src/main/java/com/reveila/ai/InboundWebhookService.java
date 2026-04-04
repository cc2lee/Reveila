package com.reveila.ai;

import java.util.HashMap;
import java.util.Map;

import com.reveila.system.PluginPrincipal;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

/**
 * Sovereign Service for receiving external task management webhooks.
 * ADR 0006: Realigned to use Proxy-based invocation pattern.
 */
public class InboundWebhookService extends SystemComponent {

    private SafeInvocation bridge;
    private OrchestrationService orchestrationService;
    private FlightRecorder flightRecorder;
    private LlmProviderFactory llmFactory;

    @Override
    protected void onStart() throws Exception {
        this.bridge = (SafeInvocation) ((SystemProxy) context.getProxy("SafeInvocation")).getInstance();
        this.orchestrationService = (OrchestrationService) ((SystemProxy) context.getProxy("OrchestrationService")).getInstance();
        this.flightRecorder = (FlightRecorder) ((SystemProxy) context.getProxy("FlightRecorder")).getInstance();
        this.llmFactory = (LlmProviderFactory) ((SystemProxy) context.getProxy("LlmProviderFactory")).getInstance();
    }

    @Override
    protected void onStop() throws Exception {}

    public InvocationResult ingest(Map<String, Object> payload) {
        String source = (String) payload.getOrDefault("trigger_source", "unknown");
        String perimeter = (String) payload.getOrDefault("agency_perimeter", "default");
        
        LlmGovernanceConfig govConfig = new LlmGovernanceConfig("openai", "gemini");
        LlmProvider worker = llmFactory.getProvider(govConfig.workerProvider());
        
        worker.generateJson(
            "You are a Specialized Worker. Map the following context to a Reveila plugin intent.",
            payload.getOrDefault("context", "{}").toString()
        );

        PluginPrincipal principal = PluginPrincipal.create("webhook-agent-" + source, "external-ingestion");
        AgentSession session = orchestrationService.createSession(principal.getTraceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));

        flightRecorder.recordStep(principal, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", Map.of());
        String action = (String) context.getOrDefault("required_action", "generic_task");
        
        String mappedIntent = action;
        if ("extract_liabilities".equals(action)) {
            mappedIntent = "doc_extraction.extract";
        }

        Map<String, Object> args = new HashMap<>();
        args.put("_session_id", session.getSessionId());
        args.put("_thought", "Worker processing Filo task: " + payload.get("task_id"));
        args.put("context", context);

        return bridge.invoke(principal, null, mappedIntent, args);
    }
}
