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
 * The Validation Pipeline:
 * 
 * Intercept: The bridge catches the Worker's intent.
 * 
 * Mask: Parameters marked as SECRET in the MetadataRegistry are redacted to
 * prevent PII/PHI leakage to the safety model.
 * 
 * Audit: The GeminiIntentValidator performs the performSafetyAudit.
 * 
 * Enforce: If Gemini detects a violation (e.g., an unauthorized domain or a
 * prompt injection attempt), the bridge returns a SECURITY_BREACH status and
 * blocks the sandbox from spawning.
 * 
 * @author CL
 */
public class UniversalInvocationBridge extends com.reveila.system.AbstractService {
    private IntentValidator intentValidator;
    private LlmProviderFactory llmFactory;
    private LlmGovernanceConfig govConfig;
    private SchemaEnforcer schemaEnforcer;
    private GuardedRuntime guardedRuntime;
    private FlightRecorder flightRecorder;
    private MetadataRegistry metadataRegistry;
    private CredentialManager credentialManager;
    private OrchestrationService orchestrationService;

    public UniversalInvocationBridge() {
        // Dependencies will be wired via the SystemContext in onStart
    }

    @Override
    public void onStart() throws Exception {
        this.intentValidator = getComponent("IntentValidator", IntentValidator.class);
        this.schemaEnforcer = getComponent("SchemaEnforcer", SchemaEnforcer.class);
        this.guardedRuntime = getComponent("DockerGuardedRuntime", GuardedRuntime.class);
        this.flightRecorder = getComponent("FlightRecorder", FlightRecorder.class);
        this.metadataRegistry = getComponent("MetadataRegistry", MetadataRegistry.class);
        this.credentialManager = getComponent("CredentialManager", CredentialManager.class);
        this.orchestrationService = getComponent("OrchestrationService", OrchestrationService.class);
        this.llmFactory = getComponent("LlmProviderFactory", LlmProviderFactory.class);
        
        // Governance config could be an argument or a separate component
        this.govConfig = new LlmGovernanceConfig("openai", "gemini");
    }

    @Override
    protected void onStop() throws Exception {
    }

    private <T> T getComponent(String name, Class<T> type) {
        return systemContext.getProxy(name)
                .map(p -> {
                    try {
                        return (T) p.invoke("getInstance", null);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(type::isInstance)
                .map(type::cast)
                .orElseThrow(() -> new IllegalStateException("Component '" + name + "' not found or invalid type."));
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

        // Multi-Model Governance: Use OpenAI to simulate/generate response if needed,
        // then Gemini for Safety Audit on masked arguments.
        Map<String, Object> validatedArgs = schemaEnforcer.enforce(pluginId, rawArguments);

        // Mask Sensitive Parameters before Safety Audit
        Map<String, Object> maskedArgs = new java.util.HashMap<>(validatedArgs);
        if (manifest.secretParameters() != null) {
            for (String secretKey : manifest.secretParameters()) {
                if (maskedArgs.containsKey(secretKey)) {
                    maskedArgs.put(secretKey, "[REDACTED_SECRET]");
                }
            }
        }

        boolean safe = intentValidator.performSafetyAudit(pluginId, maskedArgs.toString(), "Safety Guardrail Context");
        if (!safe) {
            flightRecorder.recordStep(principal, "safety_audit_failed", Map.of("pluginId", pluginId));

            // Use structured audit to check for security breach
            if (intentValidator instanceof GeminiIntentValidator geminiValidator) {
                GuardrailResponse audit = geminiValidator.getGuardrailResponse(pluginId, maskedArgs.toString(), "Safety Guardrail Context");
                if ("REJECTED".equals(audit.status()) && audit.reasoning().contains("SECURITY_BREACH")) {
                    return InvocationResult.securityBreach("Governance Pipeline: SECURITY_BREACH detected by Gemini RailGuard: " + audit.reasoning());
                }
            }

            return InvocationResult.error("Governance Pipeline: Safety audit failed by Gemini RailGuard.");
        }

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
        if (!activePerimeter.accessScopes().isEmpty()) {
            jitCreds = credentialManager.generateJitToken(principal, activePerimeter.accessScopes().iterator().next());
        }

        try {
            Object result = guardedRuntime.execute(principal, activePerimeter, pluginId, validatedArgs, jitCreds);

            // Log output, but mask parameters marked as MASKED in the manifest
            Object loggedOutput = result;
            if (manifest.maskedParameters() != null && result instanceof Map) {
                Map<String, Object> outputMap = new java.util.HashMap<>((Map<String, Object>) result);
                for (String maskedKey : manifest.maskedParameters()) {
                    if (outputMap.containsKey(maskedKey)) {
                        outputMap.put(maskedKey, "[MASKED]");
                    }
                }
                loggedOutput = outputMap;
            }

            flightRecorder.recordToolOutput(principal, pluginId, loggedOutput);
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
