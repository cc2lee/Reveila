package com.reveila.spring.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reveila.ai.AgenticFabric;
import com.reveila.ai.InvocationResult;
import com.reveila.ai.UniversalInvocationBridge;
import com.reveila.spring.service.PostgresFlightRecorder;

@ExtendWith(MockitoExtension.class)
class FabricDemoControllerTest {

    @Mock private UniversalInvocationBridge bridge;
    @Mock private AgenticFabric fabric;
    @Mock private PostgresFlightRecorder flightRecorder;

    private FabricDemoController controller;

    @BeforeEach
    void setUp() {
        controller = new FabricDemoController(bridge, fabric, flightRecorder);
    }

    @Test
    void testMaWorkflowTriggersCorrectSteps() {
        Map<String, Object> request = Map.of("prompt", "M&A Deal for Company Alpha");
        
        // Mock delegation
        when(fabric.delegate(any(), eq("doc_extraction.extract"), anyMap()))
            .thenReturn("Extracted Financials");

        // Mock HITL result
        InvocationResult hitlResult = InvocationResult.pendingApproval("ma_summary.approve", "test-trace");
        when(bridge.invoke(any(), any(), eq("ma_summary.approve"), anyMap()))
            .thenReturn(hitlResult);

        Map<String, Object> response = controller.runMaWorkflow(request);

        assertNotNull(response);
        assertEquals(InvocationResult.Status.PENDING_APPROVAL, response.get("status"));
        assertEquals("Workflow paused for HITL approval.", response.get("message"));
        assertNotNull(response.get("approval_url"));

        // Verify Flight Recorder interactions
        verify(flightRecorder).recordReasoning(any(), contains("Starting M&A"));
        verify(flightRecorder).recordStep(any(), eq("delegating_extraction"), anyMap());
    }
}
