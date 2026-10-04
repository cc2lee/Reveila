package com.reveila.tools

import com.reveila.error.SystemException
import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.platform.PlatformSystem

expect fun executePlatformProcess(command: List<String>, timeoutSeconds: Long = 30L): String

/**
 * Multiplatform Terminal execution utility for Reveila-Suite.
 */
class Terminal private constructor(val secureScriptDirectory: String) {

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 30L

        fun getInstance(secureScriptDirectory: String): Terminal {
            return Terminal(secureScriptDirectory)
        }
    }

    @Throws(SystemException::class)
    fun executeSafeScript(scriptPath: String, args: List<String>): String {
        val isWindows = PlatformSystem.getOsInfo().name.lowercase().contains("win")
        val command = ArrayList<String>()

        if (isWindows) {
            command.addAll(listOf("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptPath))
        } else {
            command.addAll(listOf("bash", scriptPath))
        }

        command.addAll(args)
        return executePlatformProcess(command, DEFAULT_TIMEOUT_SECONDS)
    }

    fun executeDynamicScript(rawScriptContent: String): String {
        val fs = PlatformFileSystem()
        val isWindows = PlatformSystem.getOsInfo().name.lowercase().contains("win")
        val extension = if (isWindows) ".ps1" else ".sh"
        val tempPath = fs.resolve(secureScriptDirectory, "reveila_dynamic_script_${PlatformSystem.currentTimeMillis()}$extension")

        try {
            fs.writeText(tempPath, rawScriptContent, append = false)
            val command = if (isWindows) {
                listOf("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", tempPath)
            } else {
                listOf("bash", tempPath)
            }
            return executePlatformProcess(command, DEFAULT_TIMEOUT_SECONDS)
        } catch (e: Exception) {
            return "Execution Error: ${e.message}"
        } finally {
            try {
                fs.delete(tempPath)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
