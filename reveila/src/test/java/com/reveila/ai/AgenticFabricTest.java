package com.reveila.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenticFabricTest {

    @Mock private IntentValidator intentValidator;
    @Mock private SchemaEnforcer schemaEnforcer;
    @Mock private GuardedRuntime guardedRuntime;
    @Mock private FlightRecorder flightRecorder;
    @Mock private MetadataRegistry metadataRegistry;
    @Mock private CredentialManager credentialManager;

    private OrchestrationService orchestrationService;
    private UniversalInvocationBridge bridge;
    private AgentPrincipal principal;
    private AgencyPerimeter managerPerimeter;
    private AgencyPerimeter workerPerimeter;

    @BeforeEach
    void setUp() {
        orchestrationService = new OrchestrationService();
        bridge = new UniversalInvocationBridge(
            intentValidator, schemaEnforcer, guardedRuntime, 
            flightRecorder, metadataRegistry, credentialManager,
            orchestrationService
        );
        principal = AgentPrincipal.create("manager-agent", "tenant-1");
        
        // Manager allowed to delegate
        managerPerimeter = new AgencyPerimeter(Set.of("manage"), Set.of(), true, 2048, 2, 20, 100, 100, true);
        
        // Worker NOT allowed to delegate
        workerPerimeter = new AgencyPerimeter(Set.of("work"), Set.of(), true, 1024, 1, 10, 100, 50, false);
    }

    @Test
    void testRecursiveInvocationTracePropagation() {
        String managerIntent = "delegate:worker.task";
        Map<String, Object> managerArgs = new HashMap<>();
        managerArgs.put("input", "data");
        managerArgs.put("_thought", "Delegating to worker...");

        MetadataRegistry.PluginManifest managerManifest = new MetadataRegistry.PluginManifest(
            "manager", "Manager Plugin", "1.0", Map.of(), managerPerimeter, Set.of()
        );

        when(intentValidator.validateIntent(managerIntent)).thenReturn("manager");
        when(metadataRegistry.getManifest("manager")).thenReturn(managerManifest);
        when(schemaEnforcer.enforce(eq("manager"), anyMap())).thenReturn(managerArgs);
        
        // Mock the manager plugin's execution which in turn calls the bridge again (simulated here)
        // Setup worker expectations OUTSIDE the nested thenAnswer to avoid issues with Mockito's state
        String workerIntent = "worker.task";
        Map<String, Object> workerArgs = Map.of("task_id", "123", "_thought", "Working...");
        MetadataRegistry.PluginManifest workerManifest = new MetadataRegistry.PluginManifest(
            "worker", "Worker Plugin", "1.0", Map.of(), workerPerimeter, Set.of()
        );

        when(guardedRuntime.execute(any(), any(), eq("manager"), anyMap(), any())).thenAnswer(invocation -> {
            // Verify trace_id is preserved in TraceContextHolder
            assertNotNull(TraceContextHolder.getTraceId());
            
            // We use different mocks or verify we can handle nested calls.
            // Simplified simulation of bridge.invoke returning success for nested call.
            return "Worker Result";
        });

        InvocationResult result = bridge.invoke(principal, managerPerimeter, managerIntent, managerArgs);

        assertNotNull(result);
        assertEquals(InvocationResult.Status.SUCCESS, result.status());
        assertEquals("Worker Result", result.data());
        
        // In the test, the bridge.invoke() call finishes, and the 'finally' block
        // in UniversalInvocationBridge should clear the trace context if it was the root.
        // If it's not cleared here, it might be because the principal.traceId() used
        // in the test doesn't match what the bridge thinks is the root.
        // TraceContextHolder.clear(); // Force clear for test stability if needed
        assertNull(TraceContextHolder.getTraceId());
    }

    @Test
    void testDelegationBlockedWhenNotAllowed() {
        String delegationIntent = "delegate:restricted.task";
        Map<String, Object> args = Map.of("_thought", "Attempting unauthorized delegation...");

        MetadataRegistry.PluginManifest workerManifest = new MetadataRegistry.PluginManifest(
            "worker", "Worker Plugin", "1.0", Map.of(), workerPerimeter, Set.of()
        );

        when(intentValidator.validateIntent(delegationIntent)).thenReturn("worker");
        when(metadataRegistry.getManifest("worker")).thenReturn(workerManifest);
        when(schemaEnforcer.enforce(eq("worker"), anyMap())).thenReturn(args);

        // workerPerimeter has delegationAllowed = false
        InvocationResult result = bridge.invoke(principal, workerPerimeter, delegationIntent, args);

        assertNotNull(result);
        assertEquals(InvocationResult.Status.ERROR, result.status());
        assertTrue(result.message().contains("Delegation not allowed"));
        verify(flightRecorder).recordStep(any(), eq("delegation_blocked"), anyMap());
    }

    @Test
    void testAgentSessionPersistence() {
        AgentSession session = orchestrationService.createSession(principal.traceId());
        session.put("shared_key", "shared_value");

        Map<String, Object> args = new HashMap<>();
        args.put("_session_id", session.sessionId());
        args.put("_thought", "Using session context...");

        MetadataRegistry.PluginManifest manifest = new MetadataRegistry.PluginManifest(
            "p1", "Plugin 1", "1.0", Map.of(), workerPerimeter, Set.of()
        );

        when(intentValidator.validateIntent(anyString())).thenReturn("p1");
        when(metadataRegistry.getManifest("p1")).thenReturn(manifest);
        when(schemaEnforcer.enforce(anyString(), anyMap())).thenReturn(args);

        bridge.invoke(principal, workerPerimeter, "some.intent", args);

        // Verify session still exists and context is preserved
        AgentSession retrievedSession = orchestrationService.getSession(session.sessionId());
        assertNotNull(retrievedSession);
        assertEquals("shared_value", retrievedSession.get("shared_key"));
    }
}
