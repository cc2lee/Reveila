package com.reveila.ai;

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

/**
 * Test case for a simulated Healthcare Worker trying to call a 'Patient-Records-Export' tool without a valid session token.
 * Documents how the Gemini Intent Validator flags this as a security breach.
 * 
 * @author CL
 */
@ExtendWith(MockitoExtension.class)
class HealthcareSecurityBreachTest {

    @Mock private IntentValidator intentValidator;
    @Mock private SchemaEnforcer schemaEnforcer;
    @Mock private GuardedRuntime guardedRuntime;
    @Mock private FlightRecorder flightRecorder;
    @Mock private MetadataRegistry metadataRegistry;
    @Mock private CredentialManager credentialManager;
    @Mock private LlmProviderFactory llmFactory;
    @Mock private GeminiProvider geminiProvider;
    @Mock private OrchestrationService orchestrationService;

    private UniversalInvocationBridge bridge;
    private AgentPrincipal healthcareWorker;
    private AgencyPerimeter healthcarePerimeter;
    private LlmGovernanceConfig govConfig = LlmGovernanceConfig.defaultGov();

    @BeforeEach
    void setUp() {
        bridge = new UniversalInvocationBridge(
            intentValidator, schemaEnforcer, guardedRuntime,
            flightRecorder, metadataRegistry, credentialManager,
            orchestrationService, llmFactory, govConfig
        );
        healthcareWorker = AgentPrincipal.create("healthcare-worker", "healthcare-dept");
        healthcarePerimeter = new AgencyPerimeter(
            Set.of("healthcare_ops"), 
            Set.of("internal.claims-db.reveila.local"), 
            true, 512, 1, 30, false
        );
    }

    @Test
    void testUnauthorizedExportTriggerSecurityBreach() {
        String intent = "healthcare.patient_export";
        Map<String, Object> args = Map.of(
            "export_format", "CSV",
            "patient_ssn", "000-00-0000",
            "_thought", "Exporting patient records for external analysis."
        );

        MetadataRegistry.PluginManifest manifest = new MetadataRegistry.PluginManifest(
            "healthcare", "Healthcare Plugin", "1.0", Map.of(), healthcarePerimeter, Set.of("patient_ssn"), Set.of("patient_ssn")
        );

        // 1. Setup Mock Behavior
        when(intentValidator.validateIntent(intent)).thenReturn("healthcare");
        when(metadataRegistry.getManifest("healthcare")).thenReturn(manifest);
        when(schemaEnforcer.enforce(eq("healthcare"), anyMap())).thenReturn(args);
        
        // 2. Simulate Gemini Guardrail detecting the breach
        // The IntentValidator fails the audit
        when(intentValidator.performSafetyAudit(anyString(), anyString(), anyString())).thenReturn(false);
        // The Guardrail provider specifically identifies it as a SECURITY_BREACH
        when(llmFactory.getProvider(govConfig.guardrailProvider())).thenReturn(geminiProvider);
        when(geminiProvider.generateResponse(anyString(), anyString())).thenReturn("REJECTED: SECURITY_BREACH: Attempt to export sensitive data without authorization.");

        // 3. Invoke the Bridge
        InvocationResult result = bridge.invoke(healthcareWorker, healthcarePerimeter, intent, args);

        // 4. Assertions for CISO Documentation
        assertNotNull(result);
        assertEquals(InvocationResult.Status.SECURITY_BREACH, result.status());
        assertTrue(result.message().contains("SECURITY_BREACH detected by Gemini RailGuard"));

        // 5. Verify Forensic Logging
        // Verify the flight recorder logged the reasoning (the 'thought' behind the breach)
        verify(flightRecorder).recordReasoning(healthcareWorker, (String) args.get("_thought"));
        // Verify the flight recorder logged the safety audit failure
        verify(flightRecorder).recordStep(healthcareWorker, eq("safety_audit_failed"), anyMap());
        
        // Verify the Docker sandbox was NEVER spawned
        verify(guardedRuntime, never()).execute(any(), any(), anyString(), anyMap(), any());
    }
}
