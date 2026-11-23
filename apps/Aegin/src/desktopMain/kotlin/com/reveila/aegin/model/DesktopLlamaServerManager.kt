package com.reveila.aegin.model

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import com.reveila.util.json.JsonUtil

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

    /**
     * Downloads and extracts the official prebuilt `llama-server` binary (plus companion shared libraries)
     * from `ggml-org/llama.cpp` GitHub Releases into `<systemHome>/bin`.
     */
    fun installRuntime(systemHome: File): Pair<Boolean, String> {
        if (findExecutable(systemHome) != null) {
            return true to "Local AI runtime (llama-server) is already installed."
        }
        val osName = (System.getProperty("os.name") ?: "").lowercase()
        val arch = (System.getProperty("os.arch") ?: "").lowercase()
        val isWin = osName.contains("win")
        val isMac = osName.contains("mac")
        val isArm = arch.contains("aarch64") || arch.contains("arm64")

        val assetMatchers: List<List<String>> = when {
            isWin && isArm -> listOf(
                listOf("win", "arm64", ".zip"),
                listOf("win", "cpu", "arm64", ".zip")
            )
            isWin -> listOf(
                listOf("win", "cpu", "x64", ".zip"),
                listOf("win", "vulkan", "x64", ".zip"),
                listOf("win", "x64", ".zip")
            )
            isMac && isArm -> listOf(
                listOf("macos", "arm64", ".zip")
            )
            isMac -> listOf(
                listOf("macos", "x64", ".zip")
            )
            else -> listOf(
                listOf("ubuntu", "x64", ".zip"),
                listOf("linux", "x64", ".zip")
            )
        }

        val binDir = File(systemHome, "bin").apply { mkdirs() }
        val tempZip = File(systemHome, "temp/llama-server-download.zip").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }

        return try {
            val apiConn = URI.create("https://api.github.com/repos/ggml-org/llama.cpp/releases/latest")
                .toURL().openConnection() as HttpURLConnection
            apiConn.requestMethod = "GET"
            apiConn.connectTimeout = 10_000
            apiConn.readTimeout = 20_000
            apiConn.setRequestProperty("Accept", "application/vnd.github+json")
            apiConn.setRequestProperty("User-Agent", "Aegin-Desktop")
            val apiCode = apiConn.responseCode
            if (apiCode !in 200..299) {
                return false to "Could not reach llama.cpp releases (HTTP $apiCode)."
            }
            val releaseJson = apiConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            apiConn.disconnect()

            val root = JsonUtil.parseJsonStringToMap(releaseJson)
            val assets = (root["assets"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
            var chosenUrl: String? = null
            var chosenName = ""
            for (tokens in assetMatchers) {
                val match = assets.firstOrNull { asset ->
                    val name = asset["name"]?.toString()?.lowercase().orEmpty()
                    tokens.all { tok -> name.contains(tok) } &&
                        !name.contains("cudart") &&
                        !name.contains("sycl") &&
                        !name.contains("hip")
                }
                val url = match?.get("browser_download_url")?.toString()?.trim()
                if (!url.isNullOrBlank()) {
                    chosenUrl = url
                    chosenName = match["name"]?.toString().orEmpty()
                    break
                }
            }
            if (chosenUrl == null) {
                return false to "No prebuilt llama-server archive found for this OS/architecture ($osName / $arch)."
            }

            val dlConn = URI.create(chosenUrl).toURL().openConnection() as HttpURLConnection
            dlConn.requestMethod = "GET"
            dlConn.instanceFollowRedirects = true
            dlConn.connectTimeout = 15_000
            dlConn.readTimeout = 120_000
            dlConn.setRequestProperty("User-Agent", "Aegin-Desktop")
            val dlCode = dlConn.responseCode
            if (dlCode !in 200..299) {
                dlConn.disconnect()
                return false to "Download failed for $chosenName (HTTP $dlCode)."
            }
            dlConn.inputStream.use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            }
            dlConn.disconnect()

            var extractedServer = false
            ZipInputStream(tempZip.inputStream()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val baseName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                        val lower = baseName.lowercase()
                        val isServerBin = lower == "llama-server.exe" || lower == "llama-server"
                        val isSharedLib = lower.endsWith(".dll") || lower.endsWith(".dylib") ||
                            lower.endsWith(".so") || lower.contains(".so.")
                        if (isServerBin || isSharedLib) {
                            val outFile = File(binDir, baseName)
                            FileOutputStream(outFile).use { fos ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val n = zis.read(buf)
                                    if (n < 0) break
                                    fos.write(buf, 0, n)
                                }
                            }
                            if (isServerBin) {
                                outFile.setExecutable(true, false)
                                extractedServer = true
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }
            tempZip.delete()

            if (!extractedServer || findExecutable(systemHome) == null) {
                return false to "Archive '$chosenName' did not contain a runnable llama-server binary."
            }
            true to "Installed local AI runtime ($chosenName) successfully. You can now download an offline model."
        } catch (e: Exception) {
            tempZip.delete()
            false to "Failed to install local AI runtime: ${e.message ?: "unknown error"}"
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
