package com.reveila.ai;

import java.util.Optional;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniversalInvocationBridgeTest {

    @Mock private IntentValidator intentValidator;
    @Mock private SchemaEnforcer schemaEnforcer;
    @Mock private GuardedRuntime guardedRuntime;
    @Mock private FlightRecorder flightRecorder;
    @Mock private MetadataRegistry metadataRegistry;
    @Mock private CredentialManager credentialManager;
    @Mock private LlmProviderFactory llmFactory;
    private LlmGovernanceConfig govConfig = LlmGovernanceConfig.defaultGov();

    private UniversalInvocationBridge bridge;
    private OrchestrationService orchestrationService;
    private AgentPrincipal principal;
    private AgencyPerimeter perimeter;
    @Mock private com.reveila.system.SystemContext systemContext;
    @Mock private com.reveila.system.Proxy proxy;

    @BeforeEach
    void setUp() throws Exception {
        orchestrationService = new OrchestrationService();
        bridge = new UniversalInvocationBridge();
        bridge.setSystemContext(systemContext);
        
        when(systemContext.getProxy(anyString())).thenReturn(Optional.of(proxy));
        when(proxy.invoke(eq("getInstance"), any())).thenAnswer(invocation -> {
            String name = (String) Mockito.mockingDetails(systemContext).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("getProxy"))
                .reduce((first, second) -> second)
                .get().getArgument(0);
            
            return switch (name) {
                case "IntentValidator" -> intentValidator;
                case "SchemaEnforcer" -> schemaEnforcer;
                case "DockerGuardedRuntime" -> guardedRuntime;
                case "FlightRecorder" -> flightRecorder;
                case "MetadataRegistry" -> metadataRegistry;
                case "CredentialManager" -> credentialManager;
                case "OrchestrationService" -> orchestrationService;
                case "LlmProviderFactory" -> llmFactory;
                default -> null;
            };
        });
        
        bridge.start();
        principal = AgentPrincipal.create("test-agent", "tenant-1");
        perimeter = new AgencyPerimeter(Set.of("read"), Set.of(), true, 1024, 1, 10, false);
    }

    @Test
    void testGoodPath() {
        String intent = "test.action";
        Map<String, Object> args = Map.of("key", "value", "_thought", "thinking...");
        MetadataRegistry.PluginManifest manifest = new MetadataRegistry.PluginManifest(
            "p1", "Plugin 1", "1.0", Map.of(), perimeter, Set.of(), Set.of()
        );

        when(intentValidator.validateIntent(intent)).thenReturn("p1");
        when(metadataRegistry.getManifest("p1")).thenReturn(manifest);
        when(schemaEnforcer.enforce(eq("p1"), anyMap())).thenReturn(args);
        when(intentValidator.performSafetyAudit(anyString(), anyString(), anyString())).thenReturn(true);
        when(guardedRuntime.execute(any(), any(), eq("p1"), anyMap(), any())).thenReturn("Success");

        InvocationResult result = bridge.invoke(principal, perimeter, intent, args);

        assertEquals(InvocationResult.Status.SUCCESS, result.status());
        assertEquals("Success", result.data());
        verify(flightRecorder).recordReasoning(principal, "thinking...");
        verify(flightRecorder).recordToolOutput(principal, "p1", "Success");
    }

    @Test
    void testShadowAi() {
        String intent = "unknown.action";
        when(intentValidator.validateIntent(intent)).thenReturn("ghost-plugin");
        when(metadataRegistry.getManifest("ghost-plugin")).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> 
            bridge.invoke(principal, perimeter, intent, Map.of())
        );
    }

    @Test
    void testHitlGate() {
        String intent = "db.delete";
        MetadataRegistry.PluginManifest manifest = new MetadataRegistry.PluginManifest(
            "p1", "Plugin 1", "1.0", Map.of(), perimeter, Set.of(), Set.of()
        );

        when(intentValidator.validateIntent(intent)).thenReturn("p1");
        when(metadataRegistry.getManifest("p1")).thenReturn(manifest);
        when(schemaEnforcer.enforce(eq("p1"), anyMap())).thenReturn(Map.of());
        when(intentValidator.performSafetyAudit(anyString(), anyString(), anyString())).thenReturn(true);

        InvocationResult result = bridge.invoke(principal, perimeter, intent, Map.of());

        assertEquals(InvocationResult.Status.PENDING_APPROVAL, result.status());
        assertTrue(result.callbackUrl().contains(principal.traceId()));
        verify(guardedRuntime, never()).execute(any(), any(), anyString(), anyMap(), any());
    }
}
