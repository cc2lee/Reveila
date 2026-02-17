package com.reveila.spring.system;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reveila.ai.AgencyPerimeter;
import com.reveila.ai.AgentPrincipal;
import com.reveila.ai.AgenticFabric;
import com.reveila.ai.InvocationResult;
import com.reveila.ai.PerformanceReport;
import com.reveila.ai.UniversalInvocationBridge;
import com.reveila.system.AbstractService;
import com.reveila.system.Reveila;

/**
 * FabricDemoController simulates a high-risk M&A workflow.
 * 
 * Workflow:
 * 1. A Manager agent receives a prompt.
 * 2. It delegates 'Document Extraction' to a Worker agent.
 * 3. It triggers a 'HITL Approval' for the final summary.
 * 4. The entire trace is persisted to the Postgres Flight Recorder.
 * 
 * @author CL
 */
@RestController
@RequestMapping("/api/v1/fabric-demo")
public class FabricDemoController {

    private final Reveila reveila;

    public FabricDemoController(Reveila reveila) {
        this.reveila = reveila;
    }

    private <T> T getComponent(String name, Class<T> type) {
        return reveila.getSystemContext().getProxy(name)
                .map(p -> {
                    try {
                        return p.getInstance();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(type::isInstance)
                .map(type::cast)
                .orElse(null);
    }

    /**
     * Starts the M&A Workflow simulation.
     * 
     * @param request A map containing the initial prompt.
     * @return The result of the workflow.
     */
    @PostMapping("/ma-workflow")
    public Map<String, Object> runMaWorkflow(@RequestBody Map<String, Object> request) {
        UniversalInvocationBridge bridge = getComponent("UniversalInvocationBridge", UniversalInvocationBridge.class);
        AgenticFabric fabric = getComponent("AgenticFabric", AgenticFabric.class);
        com.reveila.ai.FlightRecorder flightRecorder = getComponent("FlightRecorder", com.reveila.ai.FlightRecorder.class);

        String prompt = (String) request.getOrDefault("prompt", "Analyze M&A documents for Project X");
        AgentPrincipal managerPrincipal = AgentPrincipal.create("manager-agent", "m&a-dept");
        String traceId = managerPrincipal.traceId();

        // 1. Manager reasoning
        flightRecorder.recordReasoning(managerPrincipal, "Starting M&A high-risk workflow for prompt: " + prompt);

        // 2. Delegate 'Document Extraction' to Worker
        Map<String, Object> workerArgs = new HashMap<>();
        workerArgs.put("document_type", "SEC Filing");
        workerArgs.put("target_company", "Project X");
        workerArgs.put("_thought", "Extracting key financial data from M&A documents.");

        flightRecorder.recordStep(managerPrincipal, "delegating_extraction", Map.of("target", "worker-agent"));
        Object extractionResult = fabric.delegate(managerPrincipal, "doc_extraction.extract", workerArgs);

        // 3. Trigger HITL Approval for the final summary
        Map<String, Object> summaryArgs = new HashMap<>();
        summaryArgs.put("summary", "Project X shows 15% revenue growth but has outstanding litigation.");
        summaryArgs.put("extraction_data", extractionResult);
        summaryArgs.put("_thought", "Generating final M&A summary for executive approval. This is high risk.");

        AgencyPerimeter managerPerimeter = new AgencyPerimeter(
                Set.of("finance"),
                Set.of("ma_summary.approve"),
                false,
                4096, 5, 60, true
        );

        InvocationResult summaryResult = bridge.invoke(managerPrincipal, managerPerimeter, "ma_summary.approve", summaryArgs);

        Map<String, Object> response = new HashMap<>();
        response.put("trace_id", traceId);
        response.put("status", summaryResult.status());
        
        if (summaryResult.status() == InvocationResult.Status.PENDING_APPROVAL) {
            response.put("message", "Workflow paused for HITL approval.");
            response.put("approval_url", summaryResult.callbackUrl());
        } else {
            response.put("data", summaryResult.data());
        }

        return response;
    }

    /**
     * Generates a Performance Report for the Investor Pitch Deck.
     */
    @GetMapping("/performance-report")
    public PerformanceReport getPerformanceReport() {
        AbstractService bridge = getComponent("UniversalInvocationBridge", AbstractService.class);
        AbstractService gemini = getComponent("GeminiProvider", AbstractService.class);
        AbstractService auditor = getComponent("healthcare-audit-worker", AbstractService.class);

        Map<String, Long> details = new HashMap<>();
        if (bridge != null) details.put("UniversalInvocationBridge", bridge.getStartupLatencyMs());
        if (gemini != null) details.put("GeminiProvider", gemini.getStartupLatencyMs());
        if (auditor != null) details.put("ClaimsAuditor", auditor.getStartupLatencyMs());

        return new PerformanceReport(
            bridge != null ? bridge.getStartupLatencyMs() : 0,
            gemini != null ? gemini.getStartupLatencyMs() : 0,
            auditor != null ? auditor.getStartupLatencyMs() : 0,
            details
        );
    }
}
