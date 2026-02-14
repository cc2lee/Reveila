package com.reveila.spring.service;

import com.reveila.ai.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookIngestionServiceTest {

    @Mock private UniversalInvocationBridge bridge;
    @Mock private OrchestrationService orchestrationService;
    @Mock private FlightRecorder flightRecorder;
    @Mock private AgentSession session;
    @Mock private LlmProviderFactory llmFactory;
    @Mock private LlmProvider llmProvider;
    private LlmGovernanceConfig govConfig = LlmGovernanceConfig.defaultGov();

    private WebhookIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new WebhookIngestionService(bridge, orchestrationService, flightRecorder, llmFactory, govConfig);
    }

    @Test
    void testIngestionFlow() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("trigger_source", "filo_ai");
        payload.put("task_id", "filo_2026_089");
        payload.put("context", Map.of("required_action", "extract_liabilities"));
        payload.put("agency_perimeter", "ma_due_diligence_standard");

        when(llmFactory.getProvider(anyString())).thenReturn(llmProvider);
        when(llmProvider.generateJson(anyString(), anyString())).thenReturn("{}");
        when(orchestrationService.createSession(anyString())).thenReturn(session);
        when(bridge.invoke(any(), any(), eq("doc_extraction.extract"), anyMap()))
            .thenReturn(InvocationResult.success("Payment Processed"));

        InvocationResult result = ingestionService.ingest(payload);

        assertNotNull(result);
        assertEquals(InvocationResult.Status.SUCCESS, result.status());
        assertEquals("Payment Processed", result.data());

        // Verify session management
        verify(orchestrationService).createSession(anyString());
        verify(session).put(eq("ingestion_source"), eq("Filo"));
        
        // Verify flight recorder
        verify(flightRecorder).recordStep(any(), eq("webhook_ingested"), anyMap());
        
        // Verify bridge was called with mapped intent
        verify(bridge).invoke(any(), isNull(), eq("finance.transfer"), anyMap());
    }
}
