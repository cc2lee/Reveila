package com.reveila.safety

import com.reveila.ai.AgentSession
import com.reveila.ai.AiIntentValidator
import com.reveila.ai.ToolCall
import com.reveila.ai.TraceContextHolder
import com.reveila.system.Plugin
import com.reveila.system.Proxy
import com.reveila.system.SystemComponent
import com.reveila.util.Uuid

open class ManagedInvocation : SystemComponent() {

    private var intentValidator: IntentValidator? = null
    private var schemaEnforcer: SchemaEnforcer? = null
    private var guardedRuntime: GuardedRuntime? = null
    private var flightRecorder: FlightRecorder? = null
    private var metadataRegistry: MetadataRegistry? = null
    private var secretManager: SecretManager? = null

    @Throws(Exception::class)
    override fun onStart() {
        this.intentValidator = getComponent("IntentValidator") as? IntentValidator
        this.schemaEnforcer = getComponent("SchemaEnforcer") as? SchemaEnforcer
        this.guardedRuntime = getComponent("GuardedRuntime") as? GuardedRuntime
        this.flightRecorder = getComponent("FlightRecorder") as? FlightRecorder
        this.metadataRegistry = getComponent("MetadataRegistry") as? MetadataRegistry
        this.secretManager = getComponent("SecretManager") as? SecretManager
    }

    open fun invoke(
        toolCall: ToolCall,
        perimeter: SecurityPerimeter?,
        intent: String?,
        metaInfo: Map<String, Any?>?
    ): InvocationResult {
        val isManaged = intent != null && metaInfo != null

        if (isManaged) {
            return invokeManaged(toolCall, perimeter, intent, metaInfo!!)
        }

        val pluginId = toolCall.functionName ?: throw IllegalArgumentException("toolCall functionName cannot be null")

        var methodName = "defaultMethod"
        val methodArgs: Array<Any?> = when (val args = toolCall.arguments) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val argsMap = LinkedHashMap(args as Map<String, Any?>)
                val explicitMethod = argsMap.remove("method") as? String
                if (explicitMethod != null) {
                    methodName = explicitMethod
                }
                argsMap.values.toTypedArray()
            }
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                args as Array<Any?>
            }
            else -> {
                arrayOf(args)
            }
        }

        return try {
            val result = context?.getProxy(pluginId)?.invoke(methodName, methodArgs)
            InvocationResult.success(result)
        } catch (e: Exception) {
            InvocationResult.error("Execution failed: ${e.message}")
        }
    }

    @Throws(Exception::class)
    open fun handleCallback(
        jitToken: String,
        component: String,
        method: String,
        arguments: Map<String, Any>?
    ): Any? {
        val sm = secretManager ?: throw IllegalStateException("SecretManager not available")
        if (!sm.validateToken(jitToken)) {
            throw com.reveila.error.SecurityException("Invalid or expired JIT token: $jitToken")
        }

        val args = arguments?.values?.toTypedArray() ?: emptyArray()
        return context?.getProxy(component)?.invoke(method, args)
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    private fun invokeManaged(
        toolCall: ToolCall,
        perimeter: SecurityPerimeter?,
        intent: String,
        metaInfo: Map<String, Any?>
    ): InvocationResult {
        val iv = intentValidator ?: throw IllegalStateException("IntentValidator not available")
        val fr = flightRecorder ?: throw IllegalStateException("FlightRecorder not available")
        val mr = metadataRegistry ?: throw IllegalStateException("MetadataRegistry not available")
        val se = schemaEnforcer ?: throw IllegalStateException("SchemaEnforcer not available")
        val gr = guardedRuntime ?: throw IllegalStateException("GuardedRuntime not available")
        val sm = secretManager ?: throw IllegalStateException("SecretManager not available")

        val pluginId = toolCall.functionName ?: throw IllegalArgumentException("toolCall functionName cannot be null")
        val sessionId = metaInfo[AgentSession.ID] as? String
        val traceId = (metaInfo["traceId"] as? String) ?: Uuid.randomUuid()
        val tenantId = context?.properties?.getProperty("tenant-id", "default-tenant") ?: "default-tenant"

        val plugin = Plugin(
            sessionId ?: Uuid.randomUuid(),
            pluginId,
            tenantId,
            traceId
        )

        // Step 2: Validate Intent
        try {
            iv.validateIntent(intent)
        } catch (e: com.reveila.error.SecurityException) {
            fr.recordStep(
                plugin,
                "intent_blocked",
                mapOf("intent" to intent, "trace_id" to traceId)
            )
            return InvocationResult.securityBreach("INTENT BLOCKED: ${e.message}")
        }

        // Phase 2: Metadata Check
        val manifest = mr.getManifest(pluginId)
            ?: throw IllegalArgumentException("Invocation target not registered in Metadata Registry: $pluginId")

        @Suppress("UNCHECKED_CAST")
        val rawArguments: Map<String, Any> = ((metaInfo["arguments"] as? Map<String, Any?>)?.filterValues { it != null } as? Map<String, Any>) ?: emptyMap()

        val validatedArgs = se.enforce(pluginId, rawArguments)

        val maskedArgs = HashMap(validatedArgs)
        for (secretKey in manifest.secretParameters()) {
            if (maskedArgs.containsKey(secretKey)) {
                maskedArgs[secretKey] = "[REDACTED_SECRET]"
            }
        }

        val safe = iv.performSafetyAudit(pluginId, maskedArgs.toString(), "Safety Guardrail Context")
        if (!safe) {
            fr.recordStep(plugin, "safety_audit_failed", mapOf("pluginId" to pluginId))

            if (iv is AiIntentValidator) {
                val audit = iv.getGuardrailResponse(pluginId, maskedArgs.toString(), "Safety Guardrail Context")
                if ("REJECTED" == audit.status() && audit.reasoning().contains("SECURITY_BREACH")) {
                    return InvocationResult.securityBreach("Governance Pipeline: SECURITY_BREACH detected by Gemini RailGuard: ${audit.reasoning()}")
                }
            }
            return InvocationResult.error("Governance Pipeline: Safety audit failed by Gemini RailGuard.")
        }

        val activePerimeter = if (perimeter != null) manifest.defaultPerimeter().intersect(perimeter) else manifest.defaultPerimeter()

        val isDelegationRequested = intent.startsWith("delegate:")
        if (isDelegationRequested && !activePerimeter.delegationAllowed()) {
            fr.recordStep(plugin, "delegation_blocked", mapOf("intent" to intent))
            return InvocationResult.error("Delegation not allowed for this plugin perimeter.")
        }

        if ("system.execute_dynamic_script" == intent) {
            fr.recordStep(plugin, "hitl_triggered_dynamic_script", mapOf("intent" to intent))
            return InvocationResult.pendingApproval(intent, traceId, validatedArgs["script"])
        }

        if (isHighRiskAction(manifest, intent, validatedArgs)) {
            fr.recordStep(plugin, "hitl_triggered", mapOf("intent" to intent))
            return InvocationResult.pendingApproval(intent, traceId)
        }

        var jitCreds: Map<String, String>? = null
        if (activePerimeter.accessScopes().isNotEmpty()) {
            jitCreds = sm.generateJitToken(plugin, activePerimeter.accessScopes().iterator().next())
        }

        return try {
            val result = gr.execute(plugin, activePerimeter, validatedArgs, jitCreds)

            var loggedOutput: Any? = result.data()
            if (manifest.maskedParameters().isNotEmpty() && loggedOutput is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val maskedMap = HashMap(loggedOutput as Map<String, Any?>)
                for (maskedKey in manifest.maskedParameters()) {
                    if (maskedMap.containsKey(maskedKey)) {
                        maskedMap[maskedKey] = "[MASKED]"
                    }
                }
                loggedOutput = maskedMap
            }

            fr.recordToolOutput(plugin, pluginId, loggedOutput)
            InvocationResult.success(result)
        } catch (e: Exception) {
            fr.recordStep(plugin, "execution_failed", mapOf("error" to (e.message ?: "Unknown error")))
            InvocationResult.error(e.message ?: "Unknown error")
        } finally {
            if (traceId == TraceContextHolder.getTraceId()) {
                TraceContextHolder.clear()
            }
        }
    }

    private fun isHighRiskAction(
        manifest: MetadataRegistry.PluginManifest,
        intent: String,
        args: Map<String, Any>
    ): Boolean {
        if (manifest.hitlRequiredIntents().contains(intent)) {
            return true
        }
        val lower = intent.lowercase()
        return lower.contains("delete") || lower.contains("transfer") || lower.contains("purchase")
    }
}
