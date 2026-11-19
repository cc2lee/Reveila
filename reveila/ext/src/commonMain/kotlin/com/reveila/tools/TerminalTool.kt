package com.reveila.tools

import com.reveila.ai.ReveilaTool
import com.reveila.safety.SecretManager
import com.reveila.util.json.JsonUtil

/**
 * ReveilaTool wrapper exposing [Terminal] to autonomous AI agents and user takeover workflows.
 * Updates [ToolActivityHub] with live script/output state and supports blind environment secret injection
 * from [SecretManager].
 */
class TerminalTool(
    val terminal: Terminal,
    private val secretManagerProvider: () -> SecretManager? = { null },
    private val defaultWorkingDirProvider: () -> String? = { null }
) : ReveilaTool {

    override fun getName(): String = "terminal"

    override fun getDescription(): String = """
        Executes scripts or shell commands on the target device inside a governed terminal sandbox.
        Supports live output streaming into the Terminal UI window and blind secret injection via SecretManager.
        Actions:
        - execute_script: { "action": "execute_script", "script": "...", "workingDir": "...", "timeoutSeconds": 30, "secretEnv": { "API_KEY": "SEC:my_api_key" } }
        - run_command: { "action": "run_command", "command": "...", "workingDir": "...", "timeoutSeconds": 30, "secretEnv": { ... } }
        - stop: { "action": "stop" } (aborts active running script)
        - status: { "action": "status" } (returns latest script, exit code, and output)
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase()?.trim() ?: when {
            args.containsKey("script") -> "execute_script"
            args.containsKey("command") -> "run_command"
            else -> "status"
        }
        val executionId = (args["executionId"] as? String)?.trim().orEmpty().ifBlank { "default" }

        return try {
            when (action) {
                "execute_script", "script", "run_script" -> {
                    val script = (args["script"] as? String)?.takeIf { it.isNotBlank() }
                        ?: return "Error: 'script' parameter is required for execute_script."
                    val timeoutSeconds = (args["timeoutSeconds"] as? Number)?.toLong()?.coerceIn(1L, 300L)
                        ?: Terminal.DEFAULT_TIMEOUT_SECONDS
                    val workingDir = (args["workingDir"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                        ?: defaultWorkingDirProvider()
                    val extraEnv = resolveSecretEnv(args["secretEnv"] as? Map<*, *>)

                    ToolActivityHub.updateTerminal(
                        executionId = executionId,
                        shellName = terminal.resolveShellLabel(),
                        workingDir = workingDir.orEmpty(),
                        script = script,
                        outputText = "Executing script via ${terminal.resolveShellLabel()}...",
                        exitCode = null,
                        status = "RUNNING",
                        controlOwner = if (ToolActivityHub.isTerminalUserControlled()) "USER" else "AGENT"
                    )

                    val result = terminal.executeScriptDetailed(
                        rawScriptContent = script,
                        timeoutSeconds = timeoutSeconds,
                        extraEnv = extraEnv,
                        workingDir = workingDir,
                        executionId = executionId
                    )

                    val statusLabel = when {
                        result.cancelled -> "CANCELLED"
                        result.timedOut -> "FAILED"
                        result.exitCode == 0 -> "COMPLETED"
                        else -> "FAILED"
                    }
                    ToolActivityHub.updateTerminal(
                        executionId = executionId,
                        shellName = terminal.resolveShellLabel(),
                        workingDir = workingDir.orEmpty(),
                        script = script,
                        outputText = result.output.ifBlank { "(Exited with code ${result.exitCode}, no output)" },
                        exitCode = result.exitCode,
                        status = statusLabel
                    )

                    JsonUtil.toJsonString(
                        mapOf(
                            "status" to statusLabel,
                            "exitCode" to result.exitCode,
                            "durationMs" to result.durationMs,
                            "timedOut" to result.timedOut,
                            "cancelled" to result.cancelled,
                            "output" to result.output
                        )
                    )
                }

                "run_command", "exec", "command" -> {
                    val cmd = (args["command"] as? String)?.takeIf { it.isNotBlank() }
                        ?: (args["cmd"] as? String)?.takeIf { it.isNotBlank() }
                        ?: return "Error: 'command' parameter is required for run_command."
                    val timeoutSeconds = (args["timeoutSeconds"] as? Number)?.toLong()?.coerceIn(1L, 300L)
                        ?: Terminal.DEFAULT_TIMEOUT_SECONDS
                    val workingDir = (args["workingDir"] as? String)?.trim()?.takeIf { it.isNotBlank() }
                        ?: defaultWorkingDirProvider()
                    val extraEnv = resolveSecretEnv(args["secretEnv"] as? Map<*, *>)

                    ToolActivityHub.updateTerminal(
                        executionId = executionId,
                        shellName = terminal.resolveShellLabel(),
                        workingDir = workingDir.orEmpty(),
                        script = cmd,
                        outputText = "Running command: $cmd",
                        exitCode = null,
                        status = "RUNNING"
                    )

                    val result = terminal.executeCommandDetailed(
                        commandLine = cmd,
                        timeoutSeconds = timeoutSeconds,
                        extraEnv = extraEnv,
                        workingDir = workingDir,
                        executionId = executionId
                    )

                    val statusLabel = when {
                        result.cancelled -> "CANCELLED"
                        result.timedOut -> "FAILED"
                        result.exitCode == 0 -> "COMPLETED"
                        else -> "FAILED"
                    }
                    ToolActivityHub.updateTerminal(
                        executionId = executionId,
                        shellName = terminal.resolveShellLabel(),
                        workingDir = workingDir.orEmpty(),
                        script = cmd,
                        outputText = result.output.ifBlank { "(Exited with code ${result.exitCode}, no output)" },
                        exitCode = result.exitCode,
                        status = statusLabel
                    )

                    JsonUtil.toJsonString(
                        mapOf(
                            "status" to statusLabel,
                            "exitCode" to result.exitCode,
                            "durationMs" to result.durationMs,
                            "timedOut" to result.timedOut,
                            "cancelled" to result.cancelled,
                            "output" to result.output
                        )
                    )
                }

                "stop", "cancel", "kill" -> {
                    val cancelled = terminal.cancelExecution(executionId)
                    ToolActivityHub.updateTerminal(
                        executionId = executionId,
                        status = if (cancelled) "CANCELLED" else ToolActivityHub.getSnapshot().terminal.status
                    )
                    JsonUtil.toJsonString(mapOf("executionId" to executionId, "cancelled" to cancelled))
                }

                "status" -> {
                    val snap = ToolActivityHub.getSnapshot().terminal
                    JsonUtil.toJsonString(
                        mapOf(
                            "status" to snap.status,
                            "shellName" to snap.shellName,
                            "workingDir" to snap.workingDir,
                            "exitCode" to snap.exitCode,
                            "controlOwner" to snap.controlOwner,
                            "output" to snap.outputText
                        )
                    )
                }

                else -> "Unknown terminal action '$action'. Supported actions: execute_script, run_command, stop, status."
            }
        } catch (e: Exception) {
            ToolActivityHub.updateTerminal(
                executionId = executionId,
                outputText = "Terminal Error: ${e.message}",
                status = "FAILED"
            )
            "Terminal Tool Error [$action]: ${e.message}"
        }
    }

    private fun resolveSecretEnv(rawSecretEnv: Map<*, *>?): Map<String, String> {
        if (rawSecretEnv.isNullOrEmpty()) return emptyMap()
        val sm = secretManagerProvider() ?: return emptyMap()
        val resolved = LinkedHashMap<String, String>()
        for ((kObj, vObj) in rawSecretEnv) {
            val envKey = kObj?.toString()?.trim().orEmpty()
            val secretRef = vObj?.toString()?.trim().orEmpty()
            if (envKey.isNotEmpty() && secretRef.isNotEmpty()) {
                val value = sm.resolveSecretReference(secretRef)
                if (value != null) {
                    resolved[envKey] = value
                }
            }
        }
        return resolved
    }
}
