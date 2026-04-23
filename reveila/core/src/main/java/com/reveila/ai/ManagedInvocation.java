package com.reveila.ai;

import java.util.Map;

import com.reveila.system.Plugin;
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
     * @param metaInfo The unvalidated arguments from the model.
     * @return An InvocationResult containing status and data.
     * @throws IllegalArgumentException If validation fails.
     */
    public InvocationResult invoke(ToolCall toolCall, SecurityPerimeter perimeter, String intent, Map<String, Object> metaInfo) {
        // Assume 'isManaged' is determined by presence of intent/metaInfo or a configuration flag
        boolean isManaged = intent != null && metaInfo != null;
        
        if (isManaged) {
            return invokeManaged(toolCall, perimeter, intent, metaInfo);
        }
        
        String pluginId = toolCall.getFunctionName();
        
        // When bypassing managed invocation, map the arguments appropriately
        Object[] methodArgs;
        String methodName = null;
        if (toolCall.getArguments() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> argsMap = new java.util.LinkedHashMap<>((Map<String, Object>) toolCall.getArguments());
            methodName = (String) argsMap.remove("method");
            if (methodName == null) {
                methodName = "defaultMethod";
            }
            methodArgs = argsMap.values().toArray();
        } else if (toolCall.getArguments() instanceof Object[]) {
            methodArgs = (Object[]) toolCall.getArguments();
            methodName = "defaultMethod";
        } else {
            methodArgs = new Object[]{ toolCall.getArguments() };
            methodName = "defaultMethod";
        }

        try {
            Object result = context.getProxy(pluginId).invoke(methodName, methodArgs);
            return InvocationResult.success(result);
        } catch (Exception e) {
            return InvocationResult.error("Execution failed: " + e.getMessage());
        }
    }

    private InvocationResult invokeManaged(ToolCall toolCall, SecurityPerimeter perimeter, String intent, Map<String, Object> metaInfo) {

        if (toolCall == null) {
            throw new IllegalArgumentException("ToolCall cannot be null");
        }
        if (perimeter == null) {
            throw new IllegalArgumentException("Perimeter cannot be null");
        }
        if (intent == null) {
            throw new IllegalArgumentException("Intent cannot be null");
        }
        if (metaInfo == null) {
            throw new IllegalArgumentException("MetaInfo cannot be null");
        }

        // Build an internal Plugin for routing/telemetry components
        String pluginId = toolCall.getFunctionName();
        String sessionId = metaInfo.containsKey(AgentSession.ID) ? (String) metaInfo.get(AgentSession.ID) : null;
        String traceId = metaInfo.containsKey("traceId") ? (String) metaInfo.get("traceId") : java.util.UUID.randomUUID().toString();
        String tenantId = context != null && context.getProperties() != null ? context.getProperties().getProperty("tenant-id", "default-tenant") : "default-tenant";
        
        Plugin plugin = new Plugin(
            sessionId != null ? java.util.UUID.fromString(sessionId) : java.util.UUID.randomUUID(),
            pluginId,
            tenantId,
            traceId
        );

        // Step 2: Validate Intent
        try {
            intentValidator.validateIntent(intent);
        } catch (com.reveila.error.SecurityException e) {
            flightRecorder.recordStep(plugin, "intent_blocked", Map.of(
                    "intent", intent,
                    "trace_id", traceId));
            return InvocationResult.securityBreach("INTENT BLOCKED: " + e.getMessage());
        }

        // Phase 2: Metadata Check
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest == null) {
            throw new IllegalArgumentException("Invocation target not registered in Metadata Registry: " + pluginId);
        }

        // Extract the actual MCP arguments from the LLM, mapping from the metaInfo map
        @SuppressWarnings("unchecked")
        Map<String, Object> rawArguments = metaInfo.containsKey("arguments") 
                ? (Map<String, Object>) metaInfo.get("arguments") 
                : new java.util.HashMap<>();

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
            flightRecorder.recordStep(plugin, "safety_audit_failed", Map.of("pluginId", pluginId));

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
            flightRecorder.recordStep(plugin, "delegation_blocked", Map.of("intent", intent));
            return InvocationResult.error("Delegation not allowed for this plugin perimeter.");
        }

        // 2. HITL Check for High-Risk Actions
        if ("system.execute_dynamic_script".equals(intent)) {
            flightRecorder.recordStep(plugin, "hitl_triggered_dynamic_script", Map.of("intent", intent));
            return InvocationResult.pendingApproval(intent, traceId, validatedArgs.get("script"));
        }

        if (isHighRiskAction(manifest, intent, validatedArgs)) {
            flightRecorder.recordStep(plugin, "hitl_triggered", Map.of("intent", intent));
            return InvocationResult.pendingApproval(intent, traceId);
        }

        // 3. JIT Credential Injection
        Map<String, String> jitCreds = null;
        if (!activePerimeter.accessScopes().isEmpty()) {
            jitCreds = secretManager.generateJitToken(plugin, activePerimeter.accessScopes().iterator().next());
        }

        try {
            InvocationResult result = guardedRuntime.execute(plugin, activePerimeter, validatedArgs, jitCreds);

            // Log output, but mask parameters marked as MASKED in the manifest
            Object loggedOutput = result.data();
            if (manifest.maskedParameters() != null && loggedOutput instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> maskedMap = new java.util.HashMap<>((Map<String, Object>) loggedOutput);
                for (String maskedKey : manifest.maskedParameters()) {
                    if (maskedMap.containsKey(maskedKey)) {
                        maskedMap.put(maskedKey, "[MASKED]");
                    }
                }
                loggedOutput = maskedMap;
            }

            flightRecorder.recordToolOutput(plugin, pluginId, loggedOutput);
            return InvocationResult.success(result);
        } catch (Exception e) {
            flightRecorder.recordStep(plugin, "execution_failed", Map.of("error", e.getMessage()));
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
