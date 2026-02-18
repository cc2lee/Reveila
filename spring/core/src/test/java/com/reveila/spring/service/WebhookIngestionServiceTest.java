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
import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class WebhookIngestionServiceTest {

    @Mock private UniversalInvocationBridge bridge;
    @Mock private OrchestrationService orchestrationService;
    @Mock private FlightRecorder flightRecorder;
    @Mock private AgentSession session;
    @Mock private LlmProviderFactory llmFactory;
    @Mock private LlmProvider llmProvider;
    @Mock private com.reveila.system.Reveila reveila;
    @Mock private com.reveila.system.SystemContext systemContext;
    @Mock private com.reveila.system.Proxy proxy;

    private WebhookIngestionService ingestionService;

    @BeforeEach
    void setUp() throws Exception {
        when(reveila.getSystemContext()).thenReturn(systemContext);
        when(systemContext.getProxy(anyString())).thenReturn(java.util.Optional.of(proxy));
        
        when(proxy.getInstance()).thenAnswer(invocation -> {
            String name = (String) Mockito.mockingDetails(systemContext).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("getProxy"))
                .reduce((first, second) -> second)
                .get().getArgument(0);
            
            return switch (name) {
                case "UniversalInvocationBridge" -> bridge;
                case "OrchestrationService" -> orchestrationService;
                case "FlightRecorder" -> flightRecorder;
                case "LlmProviderFactory" -> llmFactory;
                default -> null;
            };
        });

        ingestionService = new WebhookIngestionService(reveila);
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
        verify(session).put(eq("ingestion_source"), eq("filo_ai"));
        
        // Verify flight recorder
        verify(flightRecorder).recordStep(any(), eq("filo_handshake_received"), anyMap());
        
        // Verify bridge was called with mapped intent
        verify(bridge).invoke(any(), isNull(), eq("doc_extraction.extract"), anyMap());
    }
}
