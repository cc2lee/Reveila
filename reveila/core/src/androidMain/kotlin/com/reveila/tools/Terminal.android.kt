package com.reveila.tools

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

actual fun executePlatformProcess(command: List<String>, timeoutSeconds: Long): String {
    return try {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(true)
        val process = pb.start()

        val output = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
        }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            output.append("\n[ERROR] Process timed out after $timeoutSeconds seconds.")
        }
        output.toString().trim()
    } catch (e: Exception) {
        "Execution Error: ${e.message}"
    }
}
