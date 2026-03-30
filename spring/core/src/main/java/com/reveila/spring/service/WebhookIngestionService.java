package com.reveila.spring.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.reveila.ai.AgentSession;
import com.reveila.ai.FlightRecorder;
import com.reveila.ai.InvocationBridge;
import com.reveila.ai.InvocationResult;
import com.reveila.ai.LlmGovernanceConfig;
import com.reveila.ai.LlmProvider;
import com.reveila.ai.LlmProviderFactory;
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
        return reveila.getSystemContext().getProxy(name)
                .map(p -> {
                    try {
                        if (p instanceof SystemProxy) {
                            return ((SystemProxy) p).getInstance();
                        }
                        return p.invoke("getInstance", null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(type::isInstance)
                .map(type::cast)
                .orElseThrow(() -> new IllegalStateException("Component '" + name + "' not found in Reveila context."));
    }

    /**
     * Ingests a webhook payload, converts it to a ReveilaIntent,
     * and initiates the governance audit.
     *
     * @param payload The raw webhook payload.
     * @return The result of the intent invocation.
     */
    public InvocationResult ingest(Map<String, Object> payload) {
        InvocationBridge bridge = getComponent("InvocationBridge", InvocationBridge.class);
        OrchestrationService orchestrationService = getComponent("OrchestrationService", OrchestrationService.class);
        FlightRecorder flightRecorder = getComponent("FlightRecorder", FlightRecorder.class);
        LlmProviderFactory llmFactory = getComponent("LlmProviderFactory", LlmProviderFactory.class);
        // Default governance config as it's no longer a standalone bean
        LlmGovernanceConfig govConfig = new LlmGovernanceConfig("openai", "gemini");

        // 1. Filo Handshake: Validate trigger source and priority
        String source = (String) payload.getOrDefault("trigger_source", "unknown");
        String perimeter = (String) payload.getOrDefault("agency_perimeter", "default");
        
        // 2. Specialized Worker (OpenAI) processes context to generate internal intent
        LlmProvider worker = llmFactory.getProvider(govConfig.workerProvider());
        String workerOutput = worker.generateJson(
            "You are a Specialized Worker. Map the following context to a Reveila plugin intent.",
            payload.getOrDefault("context", "{}").toString()
        );
        // Log the output or capture it in the trace (currently a simulated draft step)
        System.out.println("Mapped Intent Output: " + workerOutput);

        // 3. Initiate AgentSession and record ingestion
        PluginPrincipal principal = PluginPrincipal.create("webhook-agent-" + source, "external-ingestion");
        AgentSession session = orchestrationService.createSession(principal.getTraceId());
        session.put("ingestion_source", source);
        session.put("filo_task_id", payload.get("task_id"));
        session.put("perimeter_requested", perimeter);

        flightRecorder.recordStep(principal, "filo_handshake_received", Map.of(
            "task_id", payload.getOrDefault("task_id", "N/A"),
            "perimeter", perimeter
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
