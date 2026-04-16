package com.reveila.spring.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.reveila.ai.AgentSession;
import com.reveila.ai.FlightRecorder;
import com.reveila.ai.InvocationResult;
import com.reveila.ai.LlmProvider;
import com.reveila.ai.LlmProviderFactory;
import com.reveila.ai.ManagedInvocation;
import com.reveila.ai.OrchestrationService;
import com.reveila.system.PluginPrincipal;
import com.reveila.system.Reveila;
import com.reveila.system.SystemProxy;

/**
 * Service for ingesting external webhook signals from AI tools like Filo.
 *
 * @author CL
 */
@Service
public class WebhookIngestionService {

    private final Reveila reveila;

    public WebhookIngestionService(Reveila reveila) {
        this.reveila = reveila;
    }

    private <T> T getComponent(String name, Class<T> type) {
        try {
            SystemProxy p = reveila.getSystemContext().getProxy(name);
            Object instance = p.getInstance();
            if (type.isInstance(instance)) {
                return type.cast(instance);
            }
            throw new IllegalStateException("Component '" + name + "' invalid type.");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Component '" + name + "' not found in Reveila context.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Component '" + name + "' failed to initialize.", e);
        }
    }

    /**
     * Ingests a webhook payload, converts it to a ReveilaIntent,
     * and initiates the governance audit.
     *
     * @param payload The raw webhook payload.
     * @return The result of the intent invocation.
     */
    public InvocationResult ingest(Map<String, Object> payload) {
        ManagedInvocation bridge = getComponent("ManagedInvocation", ManagedInvocation.class);
        OrchestrationService orchestrationService = getComponent("OrchestrationService", OrchestrationService.class);
        FlightRecorder flightRecorder = getComponent("FlightRecorder", FlightRecorder.class);
        LlmProviderFactory llmFactory = getComponent("LlmProviderFactory", LlmProviderFactory.class);
        
        // 1. Filo Handshake: Validate trigger source and priority
        String source = (String) payload.getOrDefault("trigger_source", "unknown");
        String perimeter = (String) payload.getOrDefault("agency_perimeter", "default");
        
        // Initiate AgentSession and record ingestion early to capture worker trace
        PluginPrincipal principal = PluginPrincipal.create("webhook-agent-" + source, "external-ingestion");
        AgentSession session = orchestrationService.createSession(principal.getTraceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));
        session.put("perimeter_requested", perimeter);

        flightRecorder.recordStep(principal, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
        ));

        // 2. Specialized Worker processes context to generate internal intent
        LlmProvider worker = llmFactory.getActiveProvider();
        if (worker == null) {
            return InvocationResult.error("System Error: No active LLM Provider found.");
        }

        com.reveila.ai.LlmRequest request = com.reveila.ai.LlmRequest.builder()
                .addMessage(dev.langchain4j.data.message.SystemMessage.from("You are a Specialized Worker. Map the following context to a Reveila plugin intent. Return JSON."))
                .addMessage(dev.langchain4j.data.message.UserMessage.from(payload.getOrDefault("context", "{}").toString()))
                .build();
                
        String workerOutput;
        try {
            workerOutput = worker.invoke(request).getContent();
        } catch (Exception e) {
            workerOutput = "{\"error\": \"Worker invocation failed: " + e.getMessage() + "\"}";
        }
        
        // Capture the mapped intent output in the trace
        flightRecorder.recordStep(principal, "worker_intent_mapped", Map.of(
            "worker_output", workerOutput
        ));

        // 4. Trigger Dual-Model Governance Audit via the Bridge
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) payload.getOrDefault("context", Map.of());
        String action = (String) context.getOrDefault("required_action", "generic_task");
        
        String mappedIntent = action;
        if ("extract_liabilities".equals(action)) {
            mappedIntent = "doc_extraction.extract";
        }

        Map<String, Object> args = new java.util.HashMap<>();
        args.put("_session_id", session.getSessionId());
        args.put("_thought", "Worker processing Filo task: " + payload.get("task_id"));
        args.put("context", context);

        return bridge.invoke(principal, null, mappedIntent, args);
    }
}
