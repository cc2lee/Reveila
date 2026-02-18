package com.reveila.spring.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.reveila.ai.AgencyPerimeter;
import com.reveila.ai.AgentPrincipal;
import com.reveila.ai.AgenticFabric;
import com.reveila.ai.FlightRecorder;
import com.reveila.ai.InvocationResult;
import com.reveila.ai.PerformanceReport;
import com.reveila.ai.UniversalInvocationBridge;
import com.reveila.system.AbstractService;

/**
 * Sovereign Service for M&A Workflow orchestration.
 * ADR 0006: Realigned to use Proxy-based invocation pattern.
 */
public class MaWorkflowService extends AbstractService {

    private UniversalInvocationBridge bridge;
    private AgenticFabric fabric;
    private FlightRecorder flightRecorder;

    @Override
    protected void onStart() throws Exception {
        this.bridge = (UniversalInvocationBridge) systemContext.getProxy("UniversalInvocationBridge").orElseThrow().getInstance();
        this.fabric = (AgenticFabric) systemContext.getProxy("AgenticFabric").orElseThrow().getInstance();
        this.flightRecorder = (FlightRecorder) systemContext.getProxy("FlightRecorder").orElseThrow().getInstance();
    }

    @Override
    protected void onStop() throws Exception {}

    /**
     * Entry point for unified invocation.
     */
    public Map<String, Object> runMaWorkflow(Map<String, Object> request) {
        String prompt = (String) request.getOrDefault("prompt", "Analyze M&A documents for Project X");
        AgentPrincipal managerPrincipal = AgentPrincipal.create("manager-agent", "m&a-dept");
        String traceId = managerPrincipal.traceId();

        flightRecorder.recordReasoning(managerPrincipal, "Starting M&A high-risk workflow for prompt: " + prompt);

        Map<String, Object> workerArgs = new HashMap<>();
        workerArgs.put("document_type", "SEC Filing");
        workerArgs.put("target_company", "Project X");
        workerArgs.put("_thought", "Extracting key financial data from M&A documents.");

        flightRecorder.recordStep(managerPrincipal, "delegating_extraction", Map.of("target", "worker-agent"));
        Object extractionResult = fabric.delegate(managerPrincipal, "doc_extraction.extract", workerArgs);

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
     * ADR 0006: Diagnostic method for performance tracking.
     */
    public PerformanceReport getPerformanceReport() {
        AbstractService bridgeService = (AbstractService) bridge;
        
        // Dynamic lookups for providers as they might be isolated
        AbstractService gemini = null;
        try {
            gemini = (AbstractService) systemContext.getProxy("GeminiProvider").orElseThrow().getInstance();
        } catch (Exception e) {}

        AbstractService auditor = null;
        try {
            auditor = (AbstractService) systemContext.getProxy("healthcare-audit-worker").orElseThrow().getInstance();
        } catch (Exception e) {}

        Map<String, Long> details = new HashMap<>();
        if (bridgeService != null) details.put("UniversalInvocationBridge", bridgeService.getStartupLatencyMs());
        if (gemini != null) details.put("GeminiProvider", gemini.getStartupLatencyMs());
        if (auditor != null) details.put("ClaimsAuditor", auditor.getStartupLatencyMs());

        return new PerformanceReport(
            bridgeService != null ? bridgeService.getStartupLatencyMs() : 0,
            gemini != null ? gemini.getStartupLatencyMs() : 0,
            auditor != null ? auditor.getStartupLatencyMs() : 0,
            details
        );
    }
}
