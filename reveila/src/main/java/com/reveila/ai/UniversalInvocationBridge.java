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

    public UniversalInvocationBridge(
            IntentValidator intentValidator,
            SchemaEnforcer schemaEnforcer,
            GuardedRuntime guardedRuntime,
            FlightRecorder flightRecorder) {
        this.intentValidator = intentValidator;
        this.schemaEnforcer = schemaEnforcer;
        this.guardedRuntime = guardedRuntime;
        this.flightRecorder = flightRecorder;
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
        flightRecorder.recordStep(principal, "intent_intercepted", Map.of("intent", intent));

        String pluginId = intentValidator.validateIntent(intent);
        Map<String, Object> validatedArgs = schemaEnforcer.enforce(pluginId, rawArguments);

        Object result = guardedRuntime.execute(principal, perimeter, pluginId, validatedArgs);
        
        flightRecorder.recordToolOutput(principal, pluginId, result);
        return result;
    }
}
