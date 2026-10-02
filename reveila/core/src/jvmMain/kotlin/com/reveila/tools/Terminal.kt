package com.reveila.tools

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import com.reveila.error.SystemException

/**
 * Terminal execution utility for the Reveila-Suite.
 * Handles both predefined script execution and dynamic on-the-fly execution.
 * 
 * @author Charles Lee
 */
class Terminal private constructor(val secureScriptDirectory: Path) {

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private val LOGGER = Logger.getLogger(Terminal::class.java.name)

        @JvmStatic
        fun getInstance(secureScriptDirectory: String): Terminal {
            return Terminal(Paths.get(secureScriptDirectory))
        }
    }

    @Throws(SystemException::class)
    fun executeSafeScript(scriptPath: String, args: List<String>): String {
        try {
            val command = ArrayList<String>()
            val isWindows = isWindows()

            if (isWindows) {
                val shell = if (isCommandAvailable("pwsh")) "pwsh.exe" else "powershell.exe"
                command.addAll(listOf(shell, "-ExecutionPolicy", "Bypass", "-File", scriptPath))
            } else {
                command.addAll(listOf("bash", scriptPath))
            }

            command.addAll(args)
            return runProcess(command)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return "Script execution was interrupted: " + e.message
        } catch (e: IOException) {
            throw SystemException("Failed to execute script: " + e.message, e)
        }
    }

    fun executeDynamicScript(rawScriptContent: String): String {
        var tempScriptPath: Path? = null
        val isWindows = isWindows()
        val extension = if (isWindows) ".ps1" else ".sh"
        try {
            tempScriptPath = Files.createTempFile(secureScriptDirectory, "reveila_dynamic_script_", extension)
            Files.write(tempScriptPath, rawScriptContent.toByteArray(StandardCharsets.UTF_8))
        } catch (e: IOException) {
            return "Failed to create temporary script file: " + e.message
        }

        val command = ArrayList<String>()
        if (isWindows) {
            val shell: String = try {
                if (isCommandAvailable("pwsh")) "pwsh.exe" else "powershell.exe"
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return "Script execution was interrupted: " + e.message
            } catch (e: IOException) {
                return "Error checking shell availability: " + e.message
            }
            command.addAll(listOf(shell, "-ExecutionPolicy", "Bypass", "-File", tempScriptPath.toString()))
        } else {
            command.addAll(listOf("bash", tempScriptPath.toString()))
        }

        try {
            return runProcess(command)
        } catch (e: SystemException) {
            return "Execution Error: " + e.message
        } finally {
            try {
                if (tempScriptPath != null) {
                    Files.deleteIfExists(tempScriptPath)
                }
            } catch (e: Exception) {
                LOGGER.warning("Failed to delete temporary script file: $tempScriptPath cause: ${e.message}")
            }
        }
    }

    @Throws(SystemException::class)
    private fun runProcess(command: List<String>): String {
        val process: Process
        try {
            val pb = ProcessBuilder(command)
            pb.redirectErrorStream(true)
            process = pb.start()
        } catch (e: Exception) {
            throw SystemException("Script execution failed: " + e.message, e)
        }

        try {
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }

                val finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return output.append("\n[ERROR] Script execution timed out after ")
                        .append(DEFAULT_TIMEOUT_SECONDS).append(" seconds.")
                        .toString()
                }

                return output.toString().trim()
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw SystemException("Script execution was interrupted: " + e.message, e)
        } catch (e: Exception) {
            process.destroyForcibly()
            throw SystemException("Script execution failed: " + e.message, e)
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("win")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun isCommandAvailable(command: String): Boolean {
        var process: Process? = null
        try {
            process = ProcessBuilder(
                if (isWindows()) listOf("where", command) else listOf("which", command)
            ).start()
            return process.waitFor() == 0
        } catch (e: InterruptedException) {
            process?.destroyForcibly()
            throw e
        }
    }
}
