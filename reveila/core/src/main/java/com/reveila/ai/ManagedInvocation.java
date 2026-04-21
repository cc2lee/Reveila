package com.reveila.ai;

import java.util.Map;

import com.reveila.system.InvocationTarget;
import com.reveila.system.Proxy;
import com.reveila.system.SystemComponent;
import com.reveila.system.SystemProxy;

public class ManagedInvocation extends SystemComponent {
    
    private IntentValidator intentValidator;
    private SchemaEnforcer schemaEnforcer;
    private GuardedRuntime guardedRuntime;
    private FlightRecorder flightRecorder;
    private MetadataRegistry metadataRegistry;
    private SecretManager secretManager;
    private OrchestrationService orchestrationService;

    public ManagedInvocation() {
        // Dependencies will be wired via the SystemContext in onStart
    }

    @Override
    public void onStart() throws Exception {
        this.intentValidator = getComponent("IntentValidator", IntentValidator.class);
        this.schemaEnforcer = getComponent("SchemaEnforcer", SchemaEnforcer.class);
        this.guardedRuntime = getComponent("GuardedRuntime", GuardedRuntime.class);
        this.flightRecorder = getComponent("FlightRecorder", FlightRecorder.class);
        this.metadataRegistry = getComponent("MetadataRegistry", MetadataRegistry.class);
        this.secretManager = getComponent("SecretManager", SecretManager.class);
        this.orchestrationService = getComponent("OrchestrationService", OrchestrationService.class);
    }

    @Override
    protected void onStop() throws Exception {
    }

    private <T> T getComponent(String name, Class<T> type) {
        try {
            Proxy p = context.getProxy(name);
            if (p instanceof SystemProxy sp) {
                Object instance = sp.getInstance();
                if (type.isInstance(instance)) {
                    return type.cast(instance);
                }
            }
            throw new IllegalStateException("Component '" + name + "' invalid type.");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Component '" + name + "' not found.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Component '" + name + "' failed to initialize.", e);
        }
    }

    /**
     * Invokes a plugin based on an agent's intent.
     *
     * @param target    The plugin to invoke on.
     * @param perimeter    The defined perimeter for the agent.
     * @param intent       The intent string.
     * @param arguments The unvalidated arguments from the model.
     * @return An InvocationResult containing status and data.
     * @throws IllegalArgumentException If validation fails.
     */
    public InvocationResult invoke(InvocationTarget target, SecurityPerimeter perimeter, String intent, Map<String, Object> arguments) {
        
        if (target == null) {
            throw new IllegalArgumentException("InvocationTarget cannot be null");
        }
        if (perimeter == null) {
            throw new IllegalArgumentException("Perimeter cannot be null");
        }
        if (intent == null) {
            throw new IllegalArgumentException("Intent cannot be null");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        // Session Management: Recursive Invocation & Delegation
        String sessionId = (String) arguments.get(AgentSession.ID);
        if (sessionId != null) {
            orchestrationService.getSession(sessionId);
        }

        String traceId = target.getTraceId();

        // Trace Context Propagation
        String effectiveTraceId = TraceContextHolder.getTraceId();
        if (effectiveTraceId == null) {
            effectiveTraceId = traceId;
            TraceContextHolder.setTraceId(effectiveTraceId);
        }

        // Step 1: Intercept Intent
        String reasoning = (String) arguments.getOrDefault(AgentSession.THOUGHT, "No reasoning provided");
        flightRecorder.recordReasoning(target, reasoning);

        flightRecorder.recordStep(target, "intent_intercepted", Map.of(
                "intent", intent,
                "trace_id", traceId));

        String pluginId = target.getTargetName();

        // Step 2: Validate Intent
        try {
            intentValidator.validateIntent(intent);
        } catch (SecurityException e) {
            flightRecorder.recordStep(target, "intent_blocked", Map.of(
                    "intent", intent,
                    "trace_id", traceId));
            return InvocationResult.securityBreach("INTENT BLOCKED: " + e.getMessage());
        }

        // Phase 2: Metadata Check
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest == null) {
            throw new IllegalArgumentException("Invocation target not registered in Metadata Registry: " + pluginId);
        }

        // Multi-Model Governance: Use OpenAI to simulate/generate response if needed,
        // then Gemini for Safety Audit on masked arguments.
        Map<String, Object> validatedArgs = schemaEnforcer.enforce(pluginId, arguments);

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
            flightRecorder.recordStep(target, "safety_audit_failed", Map.of("pluginId", pluginId));

            // Use structured audit to check for security breach
            if (intentValidator instanceof DefaultIntentValidator validator) {
                GuardrailResponse audit = validator.getGuardrailResponse(pluginId, maskedArgs.toString(), "Safety Guardrail Context");
                if ("REJECTED".equals(audit.status()) && audit.reasoning().contains("SECURITY_BREACH")) {
                    return InvocationResult.securityBreach("Governance Pipeline: SECURITY_BREACH detected by Gemini RailGuard: " + audit.reasoning());
                }
            }

            return InvocationResult.error("Governance Pipeline: Safety audit failed by Gemini RailGuard.");
        }

        // Phase 3: Agency Perimeters & HITL Triggers
        // 1. Perform 'Perimeter Intersection'
        SecurityPerimeter activePerimeter = manifest.defaultPerimeter();
        if (perimeter != null) {
            activePerimeter = activePerimeter.intersect(perimeter);
        }

        // Delegation Enforcement
        boolean isDelegationRequested = intent.startsWith("delegate:");
        if (isDelegationRequested && !activePerimeter.delegationAllowed()) {
            flightRecorder.recordStep(target, "delegation_blocked", Map.of("intent", intent));
            return InvocationResult.error("Delegation not allowed for this plugin perimeter.");
        }

        // 2. HITL Check for High-Risk Actions
        if ("system.execute_dynamic_script".equals(intent)) {
            flightRecorder.recordStep(target, "hitl_triggered_dynamic_script", Map.of("intent", intent));
            return InvocationResult.pendingApproval(intent, traceId, validatedArgs.get("script"));
        }

        if (isHighRiskAction(manifest, intent, validatedArgs)) {
            flightRecorder.recordStep(target, "hitl_triggered", Map.of("intent", intent));
            return InvocationResult.pendingApproval(intent, traceId);
        }

        // 3. JIT Credential Injection
        Map<String, String> jitCreds = null;
        if (!activePerimeter.accessScopes().isEmpty()) {
            jitCreds = secretManager.generateJitToken(target, activePerimeter.accessScopes().iterator().next());
        }

        try {
            Object result = guardedRuntime.execute(target, activePerimeter, validatedArgs, jitCreds);

            // Log output, but mask parameters marked as MASKED in the manifest
            Object loggedOutput = result;
            if (manifest.maskedParameters() != null && result instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outputMap = new java.util.HashMap<>((Map<String, Object>) result);
                for (String maskedKey : manifest.maskedParameters()) {
                    if (outputMap.containsKey(maskedKey)) {
                        outputMap.put(maskedKey, "[MASKED]");
                    }
                }
                loggedOutput = outputMap;
            }

            flightRecorder.recordToolOutput(target, pluginId, loggedOutput);
            return InvocationResult.success(result);
        } catch (Exception e) {
            flightRecorder.recordStep(target, "execution_failed", Map.of("error", e.getMessage()));
            return InvocationResult.error(e.getMessage());
        } finally {
            // Only clear if we were the root of the trace context
            if (traceId.equals(TraceContextHolder.getTraceId())) {
                TraceContextHolder.clear();
            }
        }
    }

    /**
     * Secure callback handler for containerized agents.
     * ADR 0006: Implements the 'Ref-in-the-Middle' pattern for isolated code.
     * 
     * @param jitToken  The temporary execution token.
     * @param component The target system component.
     * @param method    The method to invoke.
     * @param arguments The arguments map.
     * @return The execution result.
     * @throws Exception if security validation or execution fails.
     */
    public Object handleCallback(String jitToken, String component, String method, Map<String, Object> arguments)
            throws Exception {
        if (!secretManager.validateToken(jitToken)) {
            throw new com.reveila.error.SecurityException("Invalid or expired JIT token: " + jitToken);
        }

        // Logic to execute tool on behalf of the isolated agent
        return context.getProxy(component)
                .invoke(method, arguments != null ? arguments.values().toArray() : new Object[0]);
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
