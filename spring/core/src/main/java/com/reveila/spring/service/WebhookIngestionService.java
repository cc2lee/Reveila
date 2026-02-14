package com.reveila.spring.service;

import com.reveila.ai.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Service for ingesting external webhook signals from AI tools like Filo.
 * 
 * @author CL
 */
@Service
public class WebhookIngestionService {

    private final UniversalInvocationBridge bridge;
    private final OrchestrationService orchestrationService;
    private final FlightRecorder flightRecorder;
    private final LlmProviderFactory llmFactory;
    private final LlmGovernanceConfig govConfig;

    public WebhookIngestionService(UniversalInvocationBridge bridge, 
                                   OrchestrationService orchestrationService,
                                   FlightRecorder flightRecorder,
                                   LlmProviderFactory llmFactory,
                                   LlmGovernanceConfig govConfig) {
        this.bridge = bridge;
        this.orchestrationService = orchestrationService;
        this.flightRecorder = flightRecorder;
        this.llmFactory = llmFactory;
        this.govConfig = govConfig;
    }

    /**
     * Ingests a webhook payload, converts it to a ReveilaIntent,
     * and initiates the governance audit.
     *
     * @param payload The raw webhook payload.
     * @return The result of the intent invocation.
     */
    public InvocationResult ingest(Map<String, Object> payload) {
        // 1. Filo Handshake: Validate trigger source and priority
        String source = (String) payload.getOrDefault("trigger_source", "unknown");
        String perimeter = (String) payload.getOrDefault("agency_perimeter", "default");
        
        // 2. Specialized Worker (OpenAI) processes context to generate internal intent
        LlmProvider worker = llmFactory.getProvider(govConfig.workerProvider());
        String workerOutput = worker.generateJson(
            "You are a Specialized Worker. Map the following context to a Reveila plugin intent.",
            payload.getOrDefault("context", "{}").toString()
        );

        // 3. Initiate AgentSession and record ingestion
        AgentPrincipal principal = AgentPrincipal.create("webhook-agent-" + source, "external-ingestion");
        AgentSession session = orchestrationService.createSession(principal.traceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));
        session.put("perimeter_requested", perimeter);

        flightRecorder.recordStep(principal, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
        ));

        // 4. Trigger Dual-Model Governance Audit via the Bridge
        // In a real scenario, we'd parse workerOutput to get the intent and args.
        // For simulation, we map to a known intent based on context.
        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", Map.of());
        String action = (String) context.getOrDefault("required_action", "generic_task");
        
        String mappedIntent = action;
        if ("extract_liabilities".equals(action)) {
            mappedIntent = "doc_extraction.extract";
        }

        Map<String, Object> args = new java.util.HashMap<>();
        args.put("_session_id", session.sessionId());
        args.put("_thought", "Worker processing Filo task: " + payload.get("task_id"));
        args.put("context", context);

        return bridge.invoke(principal, null, mappedIntent, args);
    }
}
