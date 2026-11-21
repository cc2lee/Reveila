package com.reveila.aegin.model

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages a local `llama-server` subprocess on Desktop (Windows, macOS, Linux) so downloaded
 * `.gguf` models run smoothly over loopback HTTP (`127.0.0.1:<port>`) with full GBNF grammar support.
 */
class DesktopLlamaServerManager(
    val host: String = "127.0.0.1",
    val port: Int = 8888
) {
    private val lock = Any()
    private val logExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "aegin-desktop-llama-log").apply { isDaemon = true }
    }

    @Volatile
    private var process: Process? = null

    @Volatile
    private var activeModelPath: String? = null

    init {
        try {
            Runtime.getRuntime().addShutdownHook(Thread {
                stop()
            })
        } catch (_: Exception) {
        }
    }

    val healthUrl: String
        get() = "http://$host:$port/health"

    val completionUrl: String
        get() = "http://$host:$port/completion"

    fun getLoadedModelPath(): String? = activeModelPath

    /**
     * Locates a `llama-server` executable in explicit overrides, `<systemHome>/bin`,
     * working directory, or system `PATH`.
     */
    fun findExecutable(systemHome: File): File? {
        val isWindows = (System.getProperty("os.name") ?: "").contains("win", ignoreCase = true)
        val names = if (isWindows) listOf("llama-server.exe", "llama-server") else listOf("llama-server")

        val explicit = (System.getProperty("reveila.llama.server.bin")
            ?: System.getenv("REVEILA_LLAMA_SERVER_BIN"))?.trim().orEmpty()
        if (explicit.isNotEmpty()) {
            val f = File(explicit)
            if (f.isFile && (isWindows || f.canExecute())) return f
        }

        val searchDirs = buildList {
            add(File(systemHome, "bin"))
            add(File(systemHome, "libs"))
            val cwd = File(System.getProperty("user.dir") ?: ".")
            add(File(cwd, "system-home/standard/bin"))
            add(File(cwd, "bin"))
            val envPath = System.getenv("PATH").orEmpty()
            val sep = File.pathSeparator
            envPath.split(sep).filter { it.isNotBlank() }.forEach { add(File(it)) }
        }

        for (dir in searchDirs) {
            for (name in names) {
                val candidate = File(dir, name)
                if (candidate.isFile && (isWindows || candidate.canExecute())) {
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Returns true if a local `llama-server` is already healthy on the loopback port
     * OR a `llama-server` executable is installed and ready to launch.
     */
    fun isRuntimeAvailable(systemHome: File): Boolean {
        return isServerHealthy() || findExecutable(systemHome) != null
    }

    fun isServerHealthy(timeoutMs: Int = 1500): Boolean {
        return try {
            val conn = (URI(healthUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts or reloads `llama-server` with [modelFile] if a `llama-server` executable is available.
     * Returns `true` when the server responds healthy on [healthUrl].
     */
    fun startOrReload(
        systemHome: File,
        modelFile: File,
        contextSize: Int = 4096,
        startupTimeoutMs: Long = 25_000L
    ): Boolean {
        if (!modelFile.isFile || !ResumableModelDownloader.hasValidGgufHeader(modelFile)) {
            return false
        }
        synchronized(lock) {
            val currentProc = process
            if (currentProc != null && currentProc.isAlive &&
                activeModelPath.equals(modelFile.absolutePath, ignoreCase = true) &&
                isServerHealthy()
            ) {
                return true
            }

            val exe = findExecutable(systemHome)
            if (exe == null) {
                // External user-managed llama-server might already be running on host:port
                if (isServerHealthy()) {
                    activeModelPath = modelFile.absolutePath
                    return true
                }
                return false
            }

            stopLocked()

            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
            val cmd = listOf(
                exe.absolutePath,
                "-m", modelFile.absolutePath,
                "--host", host,
                "--port", port.toString(),
                "-c", contextSize.toString(),
                "--parallel", "1",
                "-t", threads.toString()
            )
            return try {
                val proc = ProcessBuilder(cmd)
                    .directory(exe.parentFile)
                    .redirectErrorStream(true)
                    .start()
                process = proc
                activeModelPath = modelFile.absolutePath

                logExecutor.execute {
                    try {
                        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                            while (reader.readLine() != null) {
                                // Drain stdout/stderr so subprocess never blocks on a full pipe
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                val deadline = System.currentTimeMillis() + startupTimeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (!proc.isAlive) {
                        activeModelPath = null
                        return false
                    }
                    if (isServerHealthy(timeoutMs = 1000)) {
                        return true
                    }
                    try {
                        Thread.sleep(300)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                isServerHealthy()
            } catch (_: Exception) {
                stopLocked()
                false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopLocked()
        }
    }

    private fun stopLocked() {
        val p = process
        process = null
        activeModelPath = null
        if (p != null) {
            try {
                p.destroy()
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            } catch (_: Exception) {
                try {
                    p.destroyForcibly()
                } catch (_: Exception) {
                }
            }
        }
    }
}
