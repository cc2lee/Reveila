package com.reveila.spring.service;

import com.reveila.ai.*;
import com.reveila.system.AbstractService;
import java.util.Map;
import java.util.HashMap;

/**
 * Sovereign Service for receiving external task management webhooks.
 * ADR 0006: Realigned to use Proxy-based invocation pattern.
 */
public class InboundWebhookService extends AbstractService {

    private UniversalInvocationBridge bridge;
    private OrchestrationService orchestrationService;
    private FlightRecorder flightRecorder;
    private LlmProviderFactory llmFactory;

    @Override
    protected void onStart() throws Exception {
        this.bridge = (UniversalInvocationBridge) systemContext.getProxy("UniversalInvocationBridge").orElseThrow().getInstance();
        this.orchestrationService = (OrchestrationService) systemContext.getProxy("OrchestrationService").orElseThrow().getInstance();
        this.flightRecorder = (FlightRecorder) systemContext.getProxy("FlightRecorder").orElseThrow().getInstance();
        this.llmFactory = (LlmProviderFactory) systemContext.getProxy("LlmProviderFactory").orElseThrow().getInstance();
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

        AgentPrincipal principal = AgentPrincipal.create("webhook-agent-" + source, "external-ingestion");
        AgentSession session = orchestrationService.createSession(principal.traceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));

        flightRecorder.recordStep(principal, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
        ));

        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", Map.of());
        String action = (String) context.getOrDefault("required_action", "generic_task");
        
        String mappedIntent = action;
        if ("extract_liabilities".equals(action)) {
            mappedIntent = "doc_extraction.extract";
        }

        Map<String, Object> args = new HashMap<>();
        args.put("_session_id", session.sessionId());
        args.put("_thought", "Worker processing Filo task: " + payload.get("task_id"));
        args.put("context", context);

        return bridge.invoke(principal, null, mappedIntent, args);
    }
}
