package com.reveila.tools

import com.reveila.error.SystemException
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem

data class TerminalExecutionResult(
    val exitCode: Int,
    val output: String,
    val durationMs: Long = 0L,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false
)

expect fun executePlatformProcess(command: List<String>, timeoutSeconds: Long = 30L): String

expect fun executePlatformProcessDetailed(
    command: List<String>,
    timeoutSeconds: Long = 30L,
    extraEnv: Map<String, String> = emptyMap(),
    workingDir: String? = null,
    executionId: String = "default"
): TerminalExecutionResult

expect fun cancelPlatformProcess(executionId: String = "default"): Boolean

/**
 * Multiplatform Terminal execution utility for Reveila-Suite.
 * Supports desktop (Windows PowerShell / Unix bash) and Android (/system/bin/sh),
 * live cancellation handles, working directories, and blind environment secret injection.
 */
class Terminal private constructor(val secureScriptDirectory: String) {

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L

        fun getInstance(secureScriptDirectory: String): Terminal {
            return Terminal(secureScriptDirectory)
        }
    }

    fun resolveShellLabel(): String {
        val osName = PlatformSystem.getOsInfo().name.lowercase()
        val fs = PlatformFileSystem()
        return when {
            osName.contains("win") -> "powershell.exe"
            fs.exists("/system/bin/sh") -> "/system/bin/sh"
            fs.exists("/bin/bash") || fs.exists("/usr/bin/bash") -> "bash"
            else -> "sh"
        }
    }

    private fun buildScriptCommand(scriptPath: String): List<String> {
        val shell = resolveShellLabel()
        return if (shell == "powershell.exe") {
            listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptPath)
        } else {
            listOf(shell, scriptPath)
        }
    }

    private fun buildCommandLineInvocation(commandLine: String): List<String> {
        val shell = resolveShellLabel()
        return if (shell == "powershell.exe") {
            listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", commandLine)
        } else {
            listOf(shell, "-c", commandLine)
        }
    }

    @Throws(SystemException::class)
    fun executeSafeScript(scriptPath: String, args: List<String>): String {
        val command = ArrayList<String>(buildScriptCommand(scriptPath))
        command.addAll(args)
        return executePlatformProcess(command, DEFAULT_TIMEOUT_SECONDS)
    }

    fun executeDynamicScript(rawScriptContent: String): String {
        val res = executeScriptDetailed(rawScriptContent)
        return if (res.exitCode == 0 || res.output.isNotBlank()) {
            res.output
        } else {
            "Execution exited with code ${res.exitCode}"
        }
    }

    fun executeScriptDetailed(
        rawScriptContent: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        extraEnv: Map<String, String> = emptyMap(),
        workingDir: String? = null,
        executionId: String = "default"
    ): TerminalExecutionResult {
        val fs = PlatformFileSystem()
        if (!fs.exists(secureScriptDirectory)) {
            fs.createDirectories(secureScriptDirectory)
        }
        val shell = resolveShellLabel()
        val extension = if (shell == "powershell.exe") ".ps1" else ".sh"
        val normalizedContent = if (shell == "powershell.exe") {
            rawScriptContent
        } else {
            rawScriptContent.replace("\r\n", "\n")
        }
        val tempPath = fs.normalize(
            fs.resolve(
                secureScriptDirectory,
                "reveila_dynamic_script_${PlatformSystem.currentTimeMillis()}$extension"
            )
        )

        return try {
            fs.writeText(tempPath, normalizedContent, append = false)
            executePlatformProcessDetailed(
                command = buildScriptCommand(tempPath),
                timeoutSeconds = timeoutSeconds,
                extraEnv = extraEnv,
                workingDir = workingDir,
                executionId = executionId
            )
        } catch (e: Exception) {
            TerminalExecutionResult(
                exitCode = -1,
                output = "Execution Error: ${e.message}",
                durationMs = 0L
            )
        } finally {
            try {
                fs.delete(tempPath)
            } catch (_: Exception) {
            }
        }
    }

    fun executeCommandDetailed(
        commandLine: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        extraEnv: Map<String, String> = emptyMap(),
        workingDir: String? = null,
        executionId: String = "default"
    ): TerminalExecutionResult {
        return try {
            executePlatformProcessDetailed(
                command = buildCommandLineInvocation(commandLine),
                timeoutSeconds = timeoutSeconds,
                extraEnv = extraEnv,
                workingDir = workingDir,
                executionId = executionId
            )
        } catch (e: Exception) {
            TerminalExecutionResult(
                exitCode = -1,
                output = "Execution Error: ${e.message}",
                durationMs = 0L
            )
        }
    }

    fun cancelExecution(executionId: String = "default"): Boolean {
        return cancelPlatformProcess(executionId)
    }
}
