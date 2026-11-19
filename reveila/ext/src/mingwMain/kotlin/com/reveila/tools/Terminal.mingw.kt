package com.reveila.tools

actual fun executePlatformProcess(command: List<String>, timeoutSeconds: Long): String {
    return "Process execution not available in native mode."
}

actual fun executePlatformProcessDetailed(
    command: List<String>,
    timeoutSeconds: Long,
    extraEnv: Map<String, String>,
    workingDir: String?,
    executionId: String
): TerminalExecutionResult {
    return TerminalExecutionResult(
        exitCode = -1,
        output = "Process execution not available in native mode.",
        durationMs = 0L
    )
}

actual fun cancelPlatformProcess(executionId: String): Boolean = false
