package com.reveila.ai;

import java.util.Map;

/**
 * The Universal Invocation Bridge (UIB) is the central orchestrator for all
 * agentic tool calls. This class serves as the Agentic Control Plane (ACP),
 * orchestrating the entire lifecycle of an AI tool call.
 * 
 * Every agent request is intercepted by the bridge to ensure the intent
 * maps to a registered, metadata-defined plugin.
 * 
 * Sequential Enforcement: It correctly implements a linear safety pipeline:
 * Intercept → Validate Intent → Check Metadata → Enforce Schema → Execute →
 * Record Output.
 * 
 * Registry Dependency: It includes a critical check to ensure the plugin is
 * registered in the MetadataRegistry, throwing an IllegalArgumentException if
 * an unauthorized "shadow" plugin is detected.
 * 
 * Perimeter Application: It intelligently merges agent-level perimeters with
 * manifest defaults, ensuring a baseline security posture is always active.
 * 
 * @author CL
 */
public class UniversalInvocationBridge {
    private final IntentValidator intentValidator;
    private final SchemaEnforcer schemaEnforcer;
    private final GuardedRuntime guardedRuntime;
    private final FlightRecorder flightRecorder;
    private final MetadataRegistry metadataRegistry;
    private final CredentialManager credentialManager;
    private final OrchestrationService orchestrationService;

    public UniversalInvocationBridge(
            IntentValidator intentValidator,
            SchemaEnforcer schemaEnforcer,
            GuardedRuntime guardedRuntime,
            FlightRecorder flightRecorder,
            MetadataRegistry metadataRegistry,
            CredentialManager credentialManager,
            OrchestrationService orchestrationService) {
        this.intentValidator = intentValidator;
        this.schemaEnforcer = schemaEnforcer;
        this.guardedRuntime = guardedRuntime;
        this.flightRecorder = flightRecorder;
        this.metadataRegistry = metadataRegistry;
        this.credentialManager = credentialManager;
        this.orchestrationService = orchestrationService;
    }

    /**
     * Invokes a plugin based on an agent's intent.
     *
     * @param principal    The agent principal.
     * @param perimeter    The defined perimeter for the agent.
     * @param intent       The intent string.
     * @param rawArguments The unvalidated arguments from the model.
     * @return An InvocationResult containing status and data.
     * @throws IllegalArgumentException If validation fails.
     */
    public InvocationResult invoke(AgentPrincipal principal, AgencyPerimeter perimeter, String intent,
            Map<String, Object> rawArguments) {
        
        // Phase 5: Recursive Invocation & Delegation Handling
        String sessionId = (String) rawArguments.get("_session_id");
        AgentSession session = null;
        if (sessionId != null) {
            session = orchestrationService.getSession(sessionId);
        }

        // Trace Context Propagation
        String effectiveTraceId = TraceContextHolder.getTraceId();
        if (effectiveTraceId == null) {
            effectiveTraceId = principal.traceId();
            TraceContextHolder.setTraceId(effectiveTraceId);
        }

        String reasoning = (String) rawArguments.getOrDefault("_thought", "No reasoning provided");
        flightRecorder.recordReasoning(principal, reasoning);

        flightRecorder.recordStep(principal, "intent_intercepted", Map.of(
                "intent", intent,
                "trace_id", principal.traceId()));

        String pluginId = intentValidator.validateIntent(intent);

        // Phase 2: Metadata Check
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest == null) {
            throw new IllegalArgumentException("Plugin not registered in Metadata Registry: " + pluginId);
        }

        Map<String, Object> validatedArgs = schemaEnforcer.enforce(pluginId, rawArguments);

        // Phase 3: Agency Perimeters & HITL Triggers
        // 1. Perform 'Perimeter Intersection'
        AgencyPerimeter activePerimeter = manifest.defaultPerimeter();
        if (perimeter != null) {
            activePerimeter = activePerimeter.intersect(perimeter);
        }

        // Delegation Enforcement
        boolean isDelegationRequested = intent.startsWith("delegate:");
        if (isDelegationRequested && !activePerimeter.delegationAllowed()) {
            flightRecorder.recordStep(principal, "delegation_blocked", Map.of("intent", intent));
            return InvocationResult.error("Delegation not allowed for this plugin perimeter.");
        }

        // 2. HITL Check for High-Risk Actions
        if (isHighRiskAction(manifest, intent, validatedArgs)) {
            flightRecorder.recordStep(principal, "hitl_triggered", Map.of("intent", intent));
            return InvocationResult.pendingApproval(intent, principal.traceId());
        }

        // 3. JIT Credential Injection
        Map<String, String> jitCreds = null;
        if (!activePerimeter.allowedScopes().isEmpty()) {
            jitCreds = credentialManager.generateJitToken(principal, activePerimeter.allowedScopes().iterator().next());
        }

        try {
            Object result = guardedRuntime.execute(principal, activePerimeter, pluginId, validatedArgs, jitCreds);
            flightRecorder.recordToolOutput(principal, pluginId, result);
            return InvocationResult.success(result);
        } catch (Exception e) {
            flightRecorder.recordStep(principal, "execution_failed", Map.of("error", e.getMessage()));
            return InvocationResult.error(e.getMessage());
        } finally {
            // Only clear if we were the root of the trace context
            if (principal.traceId().equals(TraceContextHolder.getTraceId())) {
                TraceContextHolder.clear();
            }
        }
    }

    private boolean isHighRiskAction(MetadataRegistry.PluginManifest manifest, String intent,
            Map<String, Object> args) {
        if (manifest.hitlRequiredIntents() != null && manifest.hitlRequiredIntents().contains(intent)) {
            return true;
        }
        return intent.toLowerCase().contains("delete") ||
                intent.toLowerCase().contains("transfer") ||
                intent.toLowerCase().contains("purchase");
    }
}
