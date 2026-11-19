package com.reveila.tools

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val activeJvmProcesses = ConcurrentHashMap<String, Process>()
private val cancelledJvmExecutions = ConcurrentHashMap.newKeySet<String>()

actual fun executePlatformProcess(command: List<String>, timeoutSeconds: Long): String {
    return executePlatformProcessDetailed(command, timeoutSeconds).output
}

actual fun executePlatformProcessDetailed(
    command: List<String>,
    timeoutSeconds: Long,
    extraEnv: Map<String, String>,
    workingDir: String?,
    executionId: String
): TerminalExecutionResult {
    val startMs = System.currentTimeMillis()
    cancelledJvmExecutions.remove(executionId)
    return try {
        val resolvedCommand = command.map { arg ->
            if ((arg.endsWith(".ps1", ignoreCase = true) || arg.endsWith(".sh", ignoreCase = true)) && File(arg).exists()) {
                File(arg).absolutePath
            } else {
                arg
            }
        }
        val pb = ProcessBuilder(resolvedCommand)
        pb.redirectErrorStream(true)
        if (!workingDir.isNullOrBlank()) {
            val dir = File(workingDir)
            if (dir.exists() && dir.isDirectory) {
                pb.directory(dir.absoluteFile)
            }
        }
        if (extraEnv.isNotEmpty()) {
            pb.environment().putAll(extraEnv)
        }

        val process = pb.start()
        activeJvmProcesses[executionId] = process

        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val wasCancelled = cancelledJvmExecutions.remove(executionId)
        val durationMs = System.currentTimeMillis() - startMs

        if (!finished) {
            process.destroyForcibly()
            output.append("\n[ERROR] Process timed out after $timeoutSeconds seconds.")
            TerminalExecutionResult(
                exitCode = -1,
                output = output.toString().trim(),
                durationMs = durationMs,
                timedOut = true,
                cancelled = wasCancelled
            )
        } else {
            val code = process.exitValue()
            if (wasCancelled) {
                output.append("\n[CANCELLED] Execution terminated by user.")
            }
            TerminalExecutionResult(
                exitCode = code,
                output = output.toString().trim(),
                durationMs = durationMs,
                timedOut = false,
                cancelled = wasCancelled
            )
        }
    } catch (e: Exception) {
        TerminalExecutionResult(
            exitCode = -1,
            output = "Execution Error: ${e.message}",
            durationMs = System.currentTimeMillis() - startMs
        )
    } finally {
        activeJvmProcesses.remove(executionId)
    }
}

actual fun cancelPlatformProcess(executionId: String): Boolean {
    cancelledJvmExecutions.add(executionId)
    val proc = activeJvmProcesses[executionId] ?: return false
    return try {
        proc.destroyForcibly()
        true
    } catch (_: Exception) {
        false
    }
}
