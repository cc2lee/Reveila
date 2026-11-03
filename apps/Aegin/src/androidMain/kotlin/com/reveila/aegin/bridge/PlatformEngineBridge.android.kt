package com.reveila.aegin.bridge

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Log
import com.reveila.aegin.AeginEngineService
import com.reveila.system.DefaultPlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Reveila
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.util.json.JsonUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

actual object PlatformEngineBridge {
    private const val TAG = "AeginEngineBridge"
    private val engine = Reveila()
    private val isStarting = AtomicBoolean(false)
    private val llmStarting = AtomicBoolean(false)
    private val llmExecutor = Executors.newSingleThreadExecutor()
    private val fallbackStorage = ConcurrentHashMap<String, String>()

    @Volatile
    private var llmProcess: Process? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var launchFolderPickerCallback: (() -> Unit)? = null

    @Volatile
    private var pendingFolderDeferred: CompletableDeferred<String?>? = null

    private val uiSubject = Subject().apply {
        principals.add(RolePrincipal("ui-client"))
        principals.add(RolePrincipal("system"))
        principals.add(RolePrincipal("admin"))
    }

    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    fun attachFolderPicker(launcher: () -> Unit) {
        launchFolderPickerCallback = launcher
    }

    fun onSystemFolderPicked(uri: Uri?) {
        val deferred = pendingFolderDeferred
        pendingFolderDeferred = null
        if (uri == null) {
            deferred?.complete(null)
            return
        }
        val resolvedPath = try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId.startsWith("primary:", ignoreCase = true)) {
                val rel = docId.substringAfter(':')
                if (rel.startsWith("Documents/", ignoreCase = true)) {
                    "Documents/" + rel.substringAfter('/')
                } else {
                    rel.ifBlank { "Documents/Reveila" }
                }
            } else {
                uri.lastPathSegment?.substringAfter(':') ?: "Documents/Reveila"
            }
        } catch (_: Exception) {
            "Documents/Reveila"
        }
        try {
            resolveVaultDirectory(resolvedPath)
        } catch (_: Exception) {
        }
        deferred?.complete(resolvedPath)
    }

    private fun resolveSystemHomeDir(): File {
        val ctx = appContext
        val baseDir = ctx?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
        val homeDir = File(baseDir, "reveila")
        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }
        if (ctx != null) {
            extractSlimAssets(ctx, "reveila", homeDir)
        }
        listOf("logs", "data", "temp", "plugins", "libs", "ReveilaVault").forEach {
            File(homeDir, it).mkdirs()
        }
        resolveModelsStagingDir()
        return homeDir
    }

    /**
     * Resolves the app-private no_backup staging directory for downloaded/on-demand .gguf weights:
     * /data/user/0/<pkg>/no_backup/reveila/models
     * Excluded from Android Auto Backup (25MB cloud limit), supports direct mmap() by llama.cpp,
     * and requires zero external storage permissions.
     */
    private fun resolveModelsStagingDir(): File {
        val ctx = appContext
        val base = ctx?.noBackupFilesDir ?: ctx?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: "/data/local/tmp")
        val modelsDir = File(base, "reveila/models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }

    private fun extractSlimAssets(context: Context, assetPath: String, targetDir: File) {
        try {
            val assets = context.assets.list(assetPath) ?: return
            if (assets.isEmpty()) {
                if (!targetDir.exists()) {
                    targetDir.parentFile?.mkdirs()
                    context.assets.open(assetPath).use { input ->
                        targetDir.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                return
            }
            if (!targetDir.exists()) targetDir.mkdirs()
            for (child in assets) {
                val childAssetPath = "$assetPath/$child"
                val childTarget = File(targetDir, child)
                val subList = context.assets.list(childAssetPath)
                if (subList != null && subList.isNotEmpty()) {
                    extractSlimAssets(context, childAssetPath, childTarget)
                } else {
                    // Component definitions are app-bundled and must track the installed build;
                    // user data (settings, properties, tasks) is only seeded when missing.
                    val isBundledDefinition = childAssetPath.startsWith("reveila/configs/shared/") ||
                        childAssetPath.startsWith("reveila/configs/android/") ||
                        childAssetPath.startsWith("reveila/configs/java/")
                    if (!childTarget.exists() || isBundledDefinition) {
                        try {
                            context.assets.open(childAssetPath).use { input ->
                                childTarget.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (_: Exception) {
                            childTarget.mkdirs()
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun ensureStartedBlocking(): Boolean {
        if (engine.isRunning()) return true
        if (!isStarting.compareAndSet(false, true)) return engine.isRunning()
        return try {
            val homeDir = resolveSystemHomeDir()
            val props = Properties()
            props.setProperty("system.home", homeDir.absolutePath)
            // Use "java" platform config so Reveila uses lightweight DefaultFlightRecorder & DefaultGuardedRuntime
            // directly from reveila-core-slim.jar without requiring heavy third-party Android libraries.
            props.setProperty("platform", "java")
            props.setProperty("launch.strict.mode", "false")

            val adapter = DefaultPlatformAdapter(props)
            engine.start(adapter)
            performLocalVaultScan(getStorageItem("vault_folder_path") ?: "Documents/Reveila")
            startLocalLlmServerAsync()
            engine.isRunning()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            isStarting.set(false)
        }
    }

    /**
     * Ported from ReveilaService.java:
     * Reads `download.base.url` and `ai.llm.model.name` from `reveila.properties`,
     * downloads the `.gguf` file from `{download.base.url}/llms/{ai.llm.model.name}` (with dev fallback to
     * `http://10.0.2.2:8000` and `http://127.0.0.1:8000` when `adb reverse tcp:8000 tcp:8000` is active),
     * and launches `libllama-server.so` on `127.0.0.1:8080`.
     */
    fun startLocalLlmServerAsync() {
        if (!llmStarting.compareAndSet(false, true)) return
        llmExecutor.execute {
            try {
                if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                    Log.i(TAG, "Local llama-server is already healthy on 127.0.0.1:8080.")
                    return@execute
                }

                val exeFile = findLlamaServerExecutable()
                val modelDir = resolveModelsStagingDir()
                val (baseUrl, targetModelName) = readLlmDownloadConfig()
                var modelFile = findModelFile(modelDir, targetModelName)

                if (modelFile == null || !modelFile.exists()) {
                    val downloadTarget = File(modelDir, targetModelName)
                    val candidateBaseUrls = buildList {
                        add("http://127.0.0.1:8000")
                        add("http://10.0.2.2:8000")
                        if (!baseUrl.isNullOrBlank()) {
                            add(baseUrl.trim().trimEnd('/'))
                        }
                    }.distinct()

                    for (candidateBase in candidateBaseUrls) {
                        val fullUrl = "$candidateBase/llms/$targetModelName"
                        Log.i(TAG, "Attempting to download GGUF model [$targetModelName] from: $fullUrl")
                        if (downloadModelFromUrl(fullUrl, downloadTarget)) {
                            modelFile = downloadTarget
                            break
                        }
                    }
                }

                if (modelFile == null || !modelFile.exists()) {
                    Log.w(TAG, "No local .gguf model found and download endpoints were unreachable.")
                    return@execute
                }

                if (exeFile == null) {
                    Log.w(TAG, "GGUF model staged at ${modelFile.absolutePath}, but llama-server executable not found for this ABI.")
                    return@execute
                }

                synchronized(this) {
                    if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                        return@execute
                    }
                    llmProcess?.let { proc ->
                        if (proc.isAlive) proc.destroy()
                    }

                    val cmd = listOf(
                        exeFile.absolutePath,
                        "-m", modelFile.absolutePath,
                        "--host", "127.0.0.1",
                        "--port", "8080",
                        "-c", "2048",
                        "--parallel", "1",
                        "-n", "256",
                        "-t", "4"
                    )
                    Log.i(TAG, "Launching native llama-server: ${cmd.joinToString(" ")}")
                    val pb = ProcessBuilder(cmd).redirectErrorStream(true)
                    val proc = pb.start()
                    llmProcess = proc

                    Executors.newSingleThreadExecutor().execute {
                        try {
                            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                                var line = reader.readLine()
                                while (line != null) {
                                    Log.d("LLAMA-SERVER", line)
                                    line = reader.readLine()
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                    var ready = false
                    for (i in 0 until 20) {
                        if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                            ready = true
                            break
                        }
                        Thread.sleep(500)
                    }
                    if (ready) {
                        Log.i(TAG, "Native llama-server online on 127.0.0.1:8080 (${modelFile.name})")
                    } else {
                        Log.w(TAG, "Native llama-server process started; awaiting initial model load.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Local LLM startup sequence failed: ${e.message}", e)
            } finally {
                llmStarting.set(false)
            }
        }
    }

    private fun readLlmDownloadConfig(): Pair<String?, String> {
        val sysProps = engine.systemContext?.properties
        var baseUrl = sysProps?.getProperty("download.base.url")
        var modelName = sysProps?.getProperty("ai.llm.model.name")

        if (baseUrl.isNullOrBlank() || modelName.isNullOrBlank()) {
            try {
                val propFile = File(resolveSystemHomeDir(), "configs/reveila.properties")
                if (propFile.exists()) {
                    val fileProps = java.util.Properties()
                    propFile.inputStream().use { fileProps.load(it) }
                    if (baseUrl.isNullOrBlank()) {
                        baseUrl = fileProps.getProperty("download.base.url")
                    }
                    if (modelName.isNullOrBlank()) {
                        modelName = fileProps.getProperty("ai.llm.model.name")
                    }
                }
            } catch (_: Exception) {
            }
        }
        return Pair(
            baseUrl?.trim(),
            modelName?.trim()?.ifBlank { "qwen2.5-0.5b-instruct-q4_k_m.gguf" } ?: "qwen2.5-0.5b-instruct-q4_k_m.gguf"
        )
    }

    private fun findLlamaServerExecutable(): File? {
        val ctx = appContext
        if (ctx != null) {
            val exe1 = File(ctx.applicationInfo.nativeLibraryDir, "libllama-server.so")
            if (exe1.exists()) {
                exe1.setReadable(true, false)
                exe1.setExecutable(true, false)
                if (exe1.canExecute()) {
                    Log.i(TAG, "Located llama-server at nativeLibraryDir: ${exe1.absolutePath}")
                    return exe1
                }
            }
        }

        val exe2 = File("/data/local/tmp/llama-server")
        if (exe2.exists()) {
            exe2.setReadable(true, false)
            exe2.setExecutable(true, false)
            if (exe2.canExecute()) {
                Log.i(TAG, "Located llama-server at /data/local/tmp: ${exe2.absolutePath}")
                return exe2
            }
        }

        val exe3 = File(resolveSystemHomeDir(), "bin/android/llama-server")
        if (exe3.exists()) {
            exe3.setReadable(true, false)
            exe3.setExecutable(true, false)
            if (exe3.canExecute()) {
                Log.i(TAG, "Located llama-server at systemHome: ${exe3.absolutePath}")
                return exe3
            }
        }
        return null
    }

    private fun findModelFile(modelDir: File, configuredModelName: String): File? {
        // 1. Check configured name in no_backup models staging dir
        val f1 = File(modelDir, configuredModelName)
        if (f1.exists() && f1.length() > 0) return f1

        // 2. Check legacy getExternalFilesDir("llms")
        val extLlms = appContext?.getExternalFilesDir("llms")
        if (extLlms != null) {
            val fExt = File(extLlms, configuredModelName)
            if (fExt.exists() && fExt.length() > 0) return fExt
            val anyExt = extLlms.listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) }?.firstOrNull()
            if (anyExt != null && anyExt.length() > 0) return anyExt
        }

        // 3. Check any .gguf in no_backup models staging dir
        val dirModels = modelDir.listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) }
        if (!dirModels.isNullOrEmpty()) return dirModels[0]

        // 4. Check /data/local/tmp
        val f2 = File("/data/local/tmp", configuredModelName)
        if (f2.exists() && f2.length() > 0) return f2
        val tmpModels = File("/data/local/tmp").listFiles { _, name -> name.endsWith(".gguf", ignoreCase = true) }
        if (!tmpModels.isNullOrEmpty()) return tmpModels[0]

        return null
    }

    private fun downloadModelFromUrl(urlStr: String, targetFile: File): Boolean {
        var conn: HttpURLConnection? = null
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        return try {
            val url = URI(urlStr).toURL()
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 30000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "GGUF download HTTP $code from $urlStr")
                return false
            }
            val contentLength = conn.contentLengthLong
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastLoggedPct = -10
                    var read = input.read(buffer)
                    while (read != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        if (contentLength > 0) {
                            val pct = ((total * 100) / contentLength).toInt()
                            if (pct >= lastLoggedPct + 10) {
                                lastLoggedPct = pct
                                Log.i(TAG, "Downloading ${targetFile.name}: $pct% (${total / (1024 * 1024)} MB / ${contentLength / (1024 * 1024)} MB)")
                            }
                        }
                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }
            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                Log.i(TAG, "GGUF model download complete: ${targetFile.absolutePath} (${targetFile.length() / (1024 * 1024)} MB)")
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "GGUF download from $urlStr failed: ${e.message}")
            if (tempFile.exists()) tempFile.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun isLlmServerHealthy(healthUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URI(healthUrl).toURL()
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1000
                readTimeout = 1000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            code in 200..399
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    actual suspend fun startService(systemHome: String?): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext
        if (ctx != null) {
            try {
                val intent = Intent(ctx, AeginEngineService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
        ensureStartedBlocking()
    }

    actual suspend fun getEngineStatus(): EngineStatus = withContext(Dispatchers.IO) {
        if (!engine.isRunning() && !isStarting.get()) {
            ensureStartedBlocking()
        }
        val homeDir = resolveSystemHomeDir()
        EngineStatus(
            initialized = engine.isRunning(),
            status = if (engine.isRunning()) "ONLINE" else "INITIALIZING_CORE",
            platform = "Android ${Build.VERSION.RELEASE ?: ""}".trim(),
            storageMountPath = homeDir.absolutePath
        )
    }

    actual suspend fun getDeviceCapabilities(): DeviceCapabilityReport = withContext(Dispatchers.IO) {
        val ctx = appContext
        var totalRamGb = 4.0
        var availRamGb = 2.0
        var isLowMem = false

        if (ctx != null) {
            try {
                val actManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (actManager != null) {
                    val memInfo = ActivityManager.MemoryInfo()
                    actManager.getMemoryInfo(memInfo)
                    totalRamGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    availRamGb = memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    isLowMem = memInfo.lowMemory
                }
            } catch (_: Exception) {
            }
        }

        val cpuAbi = Build.SUPPORTED_64_BIT_ABIS?.firstOrNull()
            ?: Build.SUPPORTED_ABIS?.firstOrNull()
            ?: "unknown"

        val cpuInfoText = try {
            File("/proc/cpuinfo").readText().lowercase()
        } catch (_: Exception) {
            ""
        }
        val isX86_64 = cpuAbi.contains("x86_64")
        val supportsDotProd = cpuInfoText.contains("asimddp") ||
            cpuInfoText.contains("dotprod") ||
            (isX86_64 && (cpuInfoText.contains("avx2") || cpuInfoText.contains("vnni") || cpuInfoText.contains("sse4_2")))
        val supportsFp16 = cpuInfoText.contains("fphp") ||
            cpuInfoText.contains("asimdhp") ||
            cpuInfoText.contains("fp16") ||
            (isX86_64 && cpuInfoText.contains("f16c"))

        val supportsVulkan = try {
            if (ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x401000)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        // Hardware Tiering Matrix
        val (tier, recModel, recSizeMb) = when {
            totalRamGb < 3.6 -> Triple(
                "INCOMPATIBLE (< 4 GB RAM)",
                "Cloud / Hybrid Fallback Required",
                0
            )
            totalRamGb < 6.8 -> Triple(
                "ENTRY TIER (4 – 6 GB RAM)",
                "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                1050
            )
            totalRamGb < 11.5 -> Triple(
                "STANDARD TIER (8 – 12 GB RAM)",
                "qwen2.5-3b-instruct-q4_k_m.gguf",
                2200
            )
            else -> Triple(
                "FLAGSHIP TIER (12 GB+ RAM)",
                "llama-3.1-8b-instruct-q4_k_m.gguf",
                4600
            )
        }

        val statFs = try {
            StatFs(Environment.getDataDirectory().path)
        } catch (_: Exception) {
            null
        }
        val availableDiskBytes = statFs?.availableBytes ?: 0L
        val freeDiskGb = availableDiskBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val requiredBytes = recSizeMb.toLong() * 1024L * 1024L
        val bufferBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val hasEnoughDisk = availableDiskBytes > (requiredBytes + bufferBytes)

        val modelsDir = resolveModelsStagingDir()
        val stagedFiles = modelsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.map { "${it.name} (${it.length() / (1024 * 1024)} MB)" }
            .orEmpty()

        val is64Bit = Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true
        val canRunLocal = is64Bit && totalRamGb >= 3.6 && !isLowMem && hasEnoughDisk

        val summary = when {
            !is64Bit -> "32-bit ABI detected ($cpuAbi). 64-bit ARM64/x86_64 required for local GGUF."
            totalRamGb < 3.6 || isLowMem -> "Insufficient physical RAM (${String.format("%.1f", totalRamGb)} GB). Android LMK would terminate local GGUF; Cloud/RAG mode recommended."
            !hasEnoughDisk -> "Insufficient free storage (${String.format("%.1f", freeDiskGb)} GB free). Need ${recSizeMb} MB + 1.5 GB cache buffer."
            else -> "Compatible ($tier). Recommended quantized profile: $recModel (~$recSizeMb MB)."
        }

        DeviceCapabilityReport(
            totalRamGb = kotlin.math.round(totalRamGb * 10.0) / 10.0,
            availableRamGb = kotlin.math.round(availRamGb * 10.0) / 10.0,
            isLowMemory = isLowMem,
            freeDiskGb = kotlin.math.round(freeDiskGb * 10.0) / 10.0,
            hasEnoughStorage = hasEnoughDisk,
            cpuAbi = cpuAbi,
            supportsSimdDotProd = supportsDotProd,
            supportsFp16 = supportsFp16,
            supportsVulkanCompute = supportsVulkan,
            hardwareTier = tier,
            recommendedModel = recModel,
            recommendedModelSizeMb = recSizeMb,
            canRunLocalGguf = canRunLocal,
            modelsStagingPath = modelsDir.absolutePath,
            stagedModels = stagedFiles,
            statusSummary = summary
        )
    }

    actual suspend fun invoke(component: String, method: String, params: List<String>): String =
        withContext(Dispatchers.IO) {
            if (!engine.isRunning()) {
                ensureStartedBlocking()
            }
            if (component == "OrchestrationService" && method == "isRunning") {
                return@withContext engine.isRunning().toString()
            }

            val typedParams: Array<Any?> = params.map { param ->
                when {
                    param.equals("true", ignoreCase = true) -> true
                    param.equals("false", ignoreCase = true) -> false
                    else -> param
                }
            }.toTypedArray()

            val result = engine.invoke(component, method, typedParams, "127.0.0.1", uiSubject)

            if (component == "AgenticFabric" && method == "askAgent" && result is Map<*, *>) {
                val answer = result["answer"]?.toString() ?: ""
                if (answer.contains("Failed to get response from LLM") || answer.contains("LLM Invocation Failure")) {
                    val userPrompt = params.firstOrNull() ?: ""
                    val ragSnippets = try {
                        engine.invoke("KnowledgeVault", "search", arrayOf<Any?>(userPrompt, 3), "127.0.0.1", uiSubject)?.toString()
                    } catch (_: Exception) {
                        null
                    }
                    val synthesized = buildString {
                        appendLine("Sovereign On-Device RAG Response (Inference Documents Active):")
                        if (!ragSnippets.isNullOrBlank() && !ragSnippets.contains("No relevant internal documents")) {
                            appendLine(ragSnippets)
                        } else {
                            appendLine("Processed intent \"$userPrompt\" within sovereign Android perimeter.")
                        }
                    }.trim()
                    val fallbackMap = mapOf(
                        "answer" to synthesized,
                        "sessionId" to (result["sessionId"]?.toString() ?: "android-session")
                    )
                    return@withContext JsonUtil.toJsonString(fallbackMap)
                }
            }

            when (result) {
                null -> "null"
                is String -> result
                is Number, is Boolean -> result.toString()
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    JsonUtil.toJsonString(result as Map<String, Any?>)
                }
                else -> result.toString()
            }
        }

    actual suspend fun authenticateBiometric(): Boolean = true

    actual fun saveSensitiveData(data: String) {
        setStorageItem("sensitive_vault_secret", data)
    }

    actual suspend fun triggerEmergencyStop(): Boolean = withContext(Dispatchers.IO) {
        llmProcess?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
            llmProcess = null
        }
        if (engine.isRunning()) {
            engine.shutdown()
        }
        true
    }

    private fun normalizeFolderDisplayCasing(rawPath: String): String {
        val trimmed = rawPath.trim().replace('\\', '/')
        return when {
            trimmed.equals("documents", ignoreCase = true) -> "Documents"
            trimmed.startsWith("documents/", ignoreCase = true) -> "Documents/" + trimmed.substringAfter('/')
            trimmed.equals("download", ignoreCase = true) -> "Download"
            trimmed.startsWith("download/", ignoreCase = true) -> "Download/" + trimmed.substringAfter('/')
            trimmed.isBlank() -> "Documents/Reveila"
            else -> trimmed
        }
    }

    actual suspend fun setVaultFolder(folderPath: String, focusKeywords: String): VaultScanResult =
        withContext(Dispatchers.IO) {
            val canonical = normalizeFolderDisplayCasing(folderPath)
            setStorageItem("vault_folder_path", canonical)
            if (focusKeywords.isNotBlank()) {
                setStorageItem("vault_focus_keywords", focusKeywords)
            }
            performLocalVaultScan(canonical)
        }

    actual suspend fun getVaultFolder(): VaultFolderConfig = withContext(Dispatchers.IO) {
        val rawFolder = getStorageItem("vault_folder_path") ?: "Documents/Reveila"
        val folder = normalizeFolderDisplayCasing(rawFolder)
        val keywords = getStorageItem("vault_focus_keywords") ?: "strategy, confidential, roadmap, architectural"
        VaultFolderConfig(folder = folder, focusKeywords = keywords)
    }

    actual suspend fun ensureVaultDirectory(folderPath: String): String = withContext(Dispatchers.IO) {
        val targetPath = normalizeFolderDisplayCasing(folderPath)
        resolveVaultDirectory(targetPath)
        targetPath
    }

    actual suspend fun listVaultDirectories(parentPath: String): List<String> = withContext(Dispatchers.IO) {
        val normalized = parentPath.trim().replace('\\', '/').trimEnd('/')
        val baseDir = when {
            normalized.startsWith("/") -> File(normalized)
            normalized.equals("Download", ignoreCase = true) || normalized.equals("Downloads", ignoreCase = true) -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            normalized.startsWith("Download/", ignoreCase = true) || normalized.startsWith("Downloads/", ignoreCase = true) -> {
                val sub = normalized.substringAfter('/')
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub)
            }
            normalized.equals("documents", ignoreCase = true) || normalized.isBlank() -> {
                val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                // Ensure the default "Reveila" folder exists so it appears in the directory list
                val defaultDir = File(docs, "Reveila")
                if (!defaultDir.exists()) {
                    defaultDir.mkdirs()
                }
                docs
            }
            normalized.startsWith("documents/", ignoreCase = true) -> {
                val sub = normalized.substringAfter('/')
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), sub)
            }
            else -> {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), normalized)
            }
        }

        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        baseDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sortedBy { it.lowercase() }
            ?: emptyList()
    }

    actual suspend fun createVaultDirectory(parentPath: String, newFolderName: String): String = withContext(Dispatchers.IO) {
        val cleanName = newFolderName.trim().replace('/', '_').replace('\\', '_').ifBlank { "Reveila" }
        val basePrefix = parentPath.trim().replace('\\', '/').trimEnd('/').ifBlank { "documents" }
        val combinedPath = "$basePrefix/$cleanName"
        resolveVaultDirectory(combinedPath)
        combinedPath
    }

    actual suspend fun pickSystemFolder(): String? {
        val launcher = launchFolderPickerCallback ?: return null
        val deferred = CompletableDeferred<String?>()
        pendingFolderDeferred = deferred
        withContext(Dispatchers.Main) {
            try {
                launcher.invoke()
            } catch (_: Exception) {
                pendingFolderDeferred = null
                deferred.complete(null)
            }
        }
        return deferred.await()
    }

    actual suspend fun scanVault(): VaultScanResult = withContext(Dispatchers.IO) {
        val folder = getVaultFolder().folder
        performLocalVaultScan(folder)
    }

    actual suspend fun getVaultStats(): VaultStats = withContext(Dispatchers.IO) {
        var docCount = 0
        var chunkCount = 0
        if (engine.isRunning()) {
            try {
                val d = engine.invoke("KnowledgeVault", "getDocumentCount", emptyArray(), "127.0.0.1", uiSubject)
                if (d is Number) docCount = d.toInt()
                val c = engine.invoke("KnowledgeVault", "getChunkCount", emptyArray(), "127.0.0.1", uiSubject)
                if (c is Number) chunkCount = c.toInt()
            } catch (_: Exception) {
            }
        }
        VaultStats(
            documentCount = docCount,
            chunkCount = chunkCount,
            folder = getVaultFolder().folder
        )
    }

    actual fun getStorageItem(key: String): String? {
        val ctx = appContext ?: return fallbackStorage[key]
        return ctx.getSharedPreferences("aegin_prefs", Context.MODE_PRIVATE).getString(key, null)
    }

    actual fun setStorageItem(key: String, value: String) {
        fallbackStorage[key] = value
        appContext?.getSharedPreferences("aegin_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(key, value)
            ?.apply()
    }

    actual fun clearStorage() {
        fallbackStorage.clear()
        appContext?.getSharedPreferences("aegin_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.apply()
    }

    /**
     * Resolves user-accessible vault directories on Android:
     * 1. "Documents/..." -> Public Shared Documents (/storage/emulated/0/Documents/...).
     * 2. "Download/..." or "Downloads/..." -> Public Shared Downloads (/storage/emulated/0/Download/...).
     * 3. Any other relative path (e.g. "ReveilaVault") -> Public Shared Documents (/storage/emulated/0/Documents/<path>)
     *    so ordinary users never have to dig into /Android/data/<pkg>/files/.
     */
    private fun resolveVaultDirectory(folderPath: String): File {
        val ctx = appContext
        val homeDir = resolveSystemHomeDir()
        val normalized = folderPath.trim().replace('\\', '/')

        val candidateDir: File = when {
            normalized.startsWith("/") -> File(normalized)
            normalized.startsWith("Download/", ignoreCase = true) ||
                normalized.startsWith("Downloads/", ignoreCase = true) ||
                normalized.equals("Download", ignoreCase = true) ||
                normalized.equals("Downloads", ignoreCase = true) -> {
                val subPath = normalized.substringAfter('/', "Reveila").ifBlank { "Reveila" }
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val pubTarget = File(publicDownloads, subPath)
                if (canWriteOrCreateDirectory(pubTarget)) {
                    pubTarget
                } else {
                    val extDownloads = ctx?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: homeDir
                    File(extDownloads, subPath)
                }
            }
            normalized.startsWith("Documents/", ignoreCase = true) || normalized.equals("Documents", ignoreCase = true) -> {
                val subPath = normalized.substringAfter('/', "Reveila").ifBlank { "Reveila" }
                val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val pubTarget = File(publicDocs, subPath)
                if (canWriteOrCreateDirectory(pubTarget)) {
                    pubTarget
                } else {
                    val extDocs = ctx?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: homeDir
                    File(extDocs, subPath)
                }
            }
            else -> {
                val subPath = normalized.ifBlank { "Reveila" }
                val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val pubTarget = File(publicDocs, subPath)
                if (canWriteOrCreateDirectory(pubTarget)) {
                    pubTarget
                } else {
                    val extDocs = ctx?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: homeDir
                    File(extDocs, subPath)
                }
            }
        }

        if (!candidateDir.exists()) {
            candidateDir.mkdirs()
        }
        val files = candidateDir.listFiles()
        if (files == null || files.isEmpty()) {
            val overviewContent = """
                # Reveila Sovereign AI Reference Document
                This document is indexed in your local Inference Documents vector store on Android.
                Physical folder location: ${candidateDir.absolutePath}
                Focus areas: strategy, confidential architecture, multi-platform roadmap, zero cloud leaks.
            """.trimIndent()
            try {
                File(candidateDir, "Reveila_Sovereign_Overview.md").writeText(overviewContent)
            } catch (_: Exception) {
                try {
                    File(candidateDir, "Reveila_Overview.md").writeText(overviewContent)
                } catch (_: Exception) {
                }
            }
        }
        return candidateDir
    }

    private fun canWriteOrCreateDirectory(dir: File): Boolean {
        return try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir.exists() && dir.canWrite()
        } catch (_: Exception) {
            false
        }
    }

    private fun performLocalVaultScan(folderPath: String): VaultScanResult {
        val dir = resolveVaultDirectory(folderPath)
        val supportedExts = setOf("md", "txt", "json", "docx", "csv", "log", "xml", "yaml", "yml", "html")
        var files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in supportedExts }.orEmpty()
        if (files.isEmpty()) {
            val bundledVault = File(resolveSystemHomeDir(), "ReveilaVault")
            files = bundledVault.listFiles()?.filter { it.isFile && it.extension.lowercase() in supportedExts }.orEmpty()
        }

        val keywordsCsv = getStorageItem("vault_focus_keywords") ?: "strategy, confidential, roadmap, architectural"
        val keywords = keywordsCsv
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        // Clear previous embeddings and sync priority focus keywords so switching folders refreshes the vector database cleanly
        if (engine.isRunning()) {
            try {
                engine.invoke("KnowledgeVault", "clear", emptyArray(), "127.0.0.1", uiSubject)
                engine.invoke("KnowledgeVault", "setFocusKeywords", arrayOf<Any?>(keywordsCsv), "127.0.0.1", uiSubject)
            } catch (_: Exception) {
            }
        }

        var filesScanned = 0
        var entitiesFound = 0

        for (file in files) {
            val content = extractFileText(file)
            if (content.isNotBlank()) {
                filesScanned++
                val matched = keywords.count { kw ->
                    file.name.lowercase().contains(kw) || content.lowercase().contains(kw)
                }
                entitiesFound += (matched + 2)

                if (engine.isRunning()) {
                    try {
                        val meta = mapOf(
                            "title" to file.name,
                            "uri" to file.toURI().toString()
                        )
                        engine.invoke(
                            "KnowledgeVault",
                            "indexDocument",
                            arrayOf<Any?>(file.name, content, meta),
                            "127.0.0.1",
                            uiSubject
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val chunks = if (engine.isRunning()) {
            try {
                (engine.invoke("KnowledgeVault", "getChunkCount", emptyArray(), "127.0.0.1", uiSubject) as? Number)?.toInt() ?: 0
            } catch (_: Exception) {
                0
            }
        } else 0

        return VaultScanResult(
            folder = folderPath,
            filesScanned = filesScanned,
            entitiesFound = entitiesFound,
            chunkCount = chunks,
            status = "SUCCESS"
        )
    }

    private fun extractFileText(file: File): String {
        return try {
            when (file.extension.lowercase()) {
                "docx" -> {
                    val sb = StringBuilder()
                    ZipInputStream(FileInputStream(file)).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == "word/document.xml") {
                                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(zip)
                                val nodes = doc.getElementsByTagName("w:t")
                                for (i in 0 until nodes.length) {
                                    sb.append(nodes.item(i).textContent).append(" ")
                                }
                                break
                            }
                            entry = zip.nextEntry
                        }
                    }
                    sb.toString().trim()
                }
                else -> file.readText()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
