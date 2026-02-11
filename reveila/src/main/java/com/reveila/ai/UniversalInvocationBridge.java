package com.reveila.ai;

import java.util.Map;

/**
 * Every agent request is intercepted by the bridge to ensure the intent 
 * maps to a registered, metadata-defined plugin.
 * 
 * @author CL
 */
public class UniversalInvocationBridge {
    private final IntentValidator intentValidator;
    private final SchemaEnforcer schemaEnforcer;
    private final GuardedRuntime guardedRuntime;
    private final FlightRecorder flightRecorder;
    private final MetadataRegistry metadataRegistry;

    public UniversalInvocationBridge(
            IntentValidator intentValidator,
            SchemaEnforcer schemaEnforcer,
            GuardedRuntime guardedRuntime,
            FlightRecorder flightRecorder,
            MetadataRegistry metadataRegistry) {
        this.intentValidator = intentValidator;
        this.schemaEnforcer = schemaEnforcer;
        this.guardedRuntime = guardedRuntime;
        this.flightRecorder = flightRecorder;
        this.metadataRegistry = metadataRegistry;
    }

    /**
     * Invokes a plugin based on an agent's intent.
     *
     * @param principal The agent principal.
     * @param perimeter The defined perimeter for the agent.
     * @param intent The intent string.
     * @param rawArguments The unvalidated arguments from the model.
     * @return The execution result.
     * @throws IllegalArgumentException If validation fails.
     */
    public Object invoke(AgentPrincipal principal, AgencyPerimeter perimeter, String intent, Map<String, Object> rawArguments) {
        flightRecorder.recordStep(principal, "intent_intercepted", Map.of(
            "intent", intent,
            "trace_id", principal.traceId()
        ));

        String pluginId = intentValidator.validateIntent(intent);
        
        // Phase 2: Metadata Check
        MetadataRegistry.PluginManifest manifest = metadataRegistry.getManifest(pluginId);
        if (manifest == null) {
            throw new IllegalArgumentException("Plugin not registered in Metadata Registry: " + pluginId);
        }

        Map<String, Object> validatedArgs = schemaEnforcer.enforce(pluginId, rawArguments);

        // Merge or validate perimeter against manifest defaults if needed
        AgencyPerimeter activePerimeter = perimeter != null ? perimeter : manifest.defaultPerimeter();

        Object result = guardedRuntime.execute(principal, activePerimeter, pluginId, validatedArgs);
        
        flightRecorder.recordToolOutput(principal, pluginId, result);
        return result;
    }
}
