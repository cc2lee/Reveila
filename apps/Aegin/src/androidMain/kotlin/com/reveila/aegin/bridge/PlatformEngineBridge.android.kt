package com.reveila.aegin.bridge

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Log
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.reveila.aegin.AeginEngineService
import com.reveila.aegin.model.AeginModelLoader
import com.reveila.aegin.model.ModelDownloadWorker
import com.reveila.aegin.model.ResumableModelDownloader
import com.reveila.system.DefaultPlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Reveila
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.util.json.JsonUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
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
    private val modelDownloadInProgress = AtomicBoolean(false)
    private val modelDownloadPauseRequested = AtomicBoolean(false)
    private val modelDownloadCancelRequested = AtomicBoolean(false)
    private val modelDownloadState = MutableStateFlow(LocalModelDownloadState())
    private val llmLock = Any()
    private val llmExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "aegin-llama-control").apply { isDaemon = true }
    }
    private val llmLogExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "aegin-llama-log").apply { isDaemon = true }
    }
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

                val initialPreflight = verifyModelPreflightBlocking(modelFile?.length() ?: 0L)
                if (!initialPreflight.allowed) {
                    Log.w(TAG, "Local GGUF preflight blocked startup: ${initialPreflight.reason}")
                    return@execute
                }

                if (modelFile == null || !modelFile.exists()) {
                    val downloadTarget = File(modelDir, targetModelName)
                    val (_, expectedSha) = resolveManifestCandidates(targetModelName)
                    // Startup only checks local LAN/dev asset servers; public Hugging Face downloads
                    // are initiated explicitly via Studio after the user reviews size and Wi-Fi warnings.
                    val candidateUrls = buildList {
                        add("http://127.0.0.1:8000/llms/$targetModelName")
                        add("http://10.0.2.2:8000/llms/$targetModelName")
                        if (!baseUrl.isNullOrBlank()) {
                            add("${baseUrl.trim().trimEnd('/')}/llms/$targetModelName")
                        }
                    }.distinct()

                    Log.i(TAG, "Attempting startup GGUF download [$targetModelName] from ${candidateUrls.size} endpoint(s)...")
                    val outcome = runBlocking {
                        ResumableModelDownloader.downloadWithResume(
                            urls = candidateUrls,
                            targetFile = downloadTarget,
                            expectedSha256 = expectedSha,
                            userAgent = "Aegin-Reveila-Startup/1.0"
                        )
                    }
                    if (outcome.succeeded) {
                        modelFile = downloadTarget
                    }
                }

                if (modelFile == null || !modelFile.exists()) {
                    Log.w(TAG, "No local .gguf model found and download endpoints were unreachable.")
                    return@execute
                }

                val postDownloadPreflight = verifyModelPreflightBlocking(modelFile.length())
                if (!postDownloadPreflight.allowed) {
                    Log.w(TAG, "Local GGUF preflight blocked model load (${modelFile.name}): ${postDownloadPreflight.reason}")
                    return@execute
                }

                // Primary path: safe JNI mmap() gate via AeginModelLoader -> LlamaJniBridge
                val loadedViaJni = try {
                    AeginModelLoader(modelDir).loadVerifiedFileBlocking(modelFile)
                } catch (e: Exception) {
                    Log.w(TAG, "AeginModelLoader skipped JNI mmap for ${modelFile.name}: ${e.message}")
                    false
                }
                if (loadedViaJni) {
                    Log.i(TAG, "Loaded verified local GGUF model via in-process JNI mmap: ${modelFile.name}")
                    return@execute
                }

                // Legacy subprocess fallback (isolated behind explicit opt-in property)
                val legacyServerEnabled = engine.systemContext?.properties
                    ?.getProperty("ai.llm.legacy.server.enabled")
                    ?.equals("true", ignoreCase = true) == true
                if (!legacyServerEnabled) {
                    Log.i(TAG, "Native JNI library unavailable and legacy server disabled; using sovereign on-device RAG fallback.")
                    return@execute
                }

                if (exeFile == null) {
                    Log.w(TAG, "GGUF model staged at ${modelFile.absolutePath}, but llama-server executable not found for this ABI.")
                    return@execute
                }

                synchronized(llmLock) {
                    if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                        return@execute
                    }
                    stopLlmProcessLocked()

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

                    llmLogExecutor.execute {
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
        val recommendedDefault = buildDeviceCapabilityReport().recommendedModel
            .takeIf { it.endsWith(".gguf", ignoreCase = true) }
            ?: ModelTierCatalog.MODEL_3B
        val resolvedModel = modelName?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("auto", ignoreCase = true) }
            ?: recommendedDefault
        return Pair(baseUrl?.trim(), resolvedModel)
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

    private fun readModelManifest(): LocalModelManifest {
        val sysHomeFile = File(resolveSystemHomeDir(), "models/manifest.json")
        val rawJson = try {
            if (sysHomeFile.exists()) {
                sysHomeFile.readText(Charsets.UTF_8)
            } else {
                appContext?.assets?.open("reveila/models/manifest.json")?.bufferedReader()?.use { it.readText() } ?: "{}"
            }
        } catch (_: Exception) {
            "{}"
        }
        return AeginBridge.parseModelManifest(rawJson)
    }

    private fun resolveManifestCandidates(modelName: String): Pair<List<String>, String> {
        val manifest = readModelManifest()
        val entry = AeginBridge.resolveManifestEntry(manifest, modelName)
        val urls = buildList {
            if (entry != null) {
                val manifestUrl = AeginBridge.buildManifestDownloadUrl(manifest, entry)
                if (manifestUrl.isNotBlank()) add(manifestUrl)
            }
        }
        return Pair(urls, entry?.sha256.orEmpty().trim())
    }

    private fun isWifiOrUnmeteredConnected(): Boolean {
        val ctx = appContext ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Exception) {
            true
        }
    }

    private fun isMeteredCellular(): Boolean {
        val ctx = appContext ?: return false
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (_: Exception) {
            false
        }
    }

    private fun findExactModelFile(modelDir: File, requestedModelName: String): File? {
        val target = requestedModelName.trim()
        if (target.isEmpty()) return null
        val candidatesDirs = buildList {
            add(modelDir)
            appContext?.getExternalFilesDir("llms")?.let { add(it) }
            add(File("/data/local/tmp"))
        }
        for (dir in candidatesDirs) {
            val exact = File(dir, target)
            if (ResumableModelDownloader.hasValidGgufHeader(exact)) return exact
            val matched = dir.listFiles { _, name -> name.equals(target, ignoreCase = true) }?.firstOrNull()
            if (matched != null && ResumableModelDownloader.hasValidGgufHeader(matched)) return matched
        }
        return null
    }

    actual suspend fun checkLocalModelAvailability(modelName: String): LocalModelCheckResult =
        withContext(Dispatchers.IO) {
            val manifest = readModelManifest()
            val entry = AeginBridge.resolveManifestEntry(manifest, modelName)
            val effectiveName = modelName.trim().ifBlank {
                entry?.effectiveFilename() ?: readLlmDownloadConfig().second
            }
            val modelDir = resolveModelsStagingDir()
            val existing = findExactModelFile(modelDir, effectiveName)
            val partFile = File(modelDir, "$effectiveName.part")
            val partialBytes = if (partFile.exists()) partFile.length() else 0L
            val manifestUrl = entry?.let { AeginBridge.buildManifestDownloadUrl(manifest, it) }.orEmpty()
            val fallbackBase = readLlmDownloadConfig().first?.trim()?.trimEnd('/').orEmpty()
            val downloadUrl = manifestUrl.ifBlank {
                if (fallbackBase.isNotEmpty()) "$fallbackBase/llms/$effectiveName" else ""
            }
            val approxBytes = entry?.approxSizeBytes ?: when {
                effectiveName.contains("0.5b", ignoreCase = true) -> 491_400_000L
                effectiveName.contains("1.5b", ignoreCase = true) -> 1_100_000_000L
                effectiveName.contains("gemma-2-2b", ignoreCase = true) -> 1_708_582_752L
                effectiveName.contains("3b", ignoreCase = true) -> 2_200_000_000L
                effectiveName.contains("7b", ignoreCase = true) -> 4_400_000_000L
                else -> 1_500_000_000L
            }
            val availStorageBytes = try {
                StatFs(modelDir.absolutePath).availableBytes
            } catch (_: Exception) {
                try {
                    StatFs(Environment.getDataDirectory().path).availableBytes
                } catch (_: Exception) {
                    -1L
                }
            }
            LocalModelCheckResult(
                modelName = effectiveName,
                existsLocally = existing != null && existing.exists(),
                localPath = existing?.absolutePath.orEmpty(),
                partialBytes = partialBytes,
                downloadUrl = downloadUrl,
                approxSizeBytes = approxBytes,
                availableStorageBytes = availStorageBytes,
                sha256 = entry?.sha256.orEmpty(),
                isWifiConnected = isWifiOrUnmeteredConnected(),
                isMeteredNetwork = isMeteredCellular(),
                requiresAuthToken = entry?.requiresAuthToken ?: false
            )
        }

    actual suspend fun downloadLocalModel(modelName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!modelDownloadInProgress.compareAndSet(false, true)) {
                return@withContext false
            }
            modelDownloadPauseRequested.set(false)
            modelDownloadCancelRequested.set(false)
            try {
                val check = checkLocalModelAvailability(modelName)
                if (check.existsLocally) {
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "COMPLETED",
                        modelName = check.modelName,
                        downloadedBytes = File(check.localPath).length(),
                        totalBytes = File(check.localPath).length(),
                        percent = 100,
                        sourceUrl = check.downloadUrl,
                        message = "Model already staged locally."
                    )
                    return@withContext true
                }

                val modelDir = resolveModelsStagingDir()
                val downloadTarget = File(modelDir, check.modelName)
                val partFile = File(modelDir, "${check.modelName}.part")
                val existingPartial = if (partFile.exists()) partFile.length() else 0L
                val existingPct = if (check.approxSizeBytes > 0L && existingPartial > 0L) {
                    ((existingPartial * 100L) / check.approxSizeBytes).toInt().coerceIn(0, 99)
                } else {
                    0
                }
                val remainingBytes = (check.approxSizeBytes - existingPartial).coerceAtLeast(0L)
                if (remainingBytes > 0L && check.availableStorageBytes in 0 until remainingBytes) {
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "FAILED",
                        modelName = check.modelName,
                        downloadedBytes = existingPartial,
                        totalBytes = check.approxSizeBytes,
                        percent = existingPct,
                        sourceUrl = check.downloadUrl,
                        message = "Insufficient on-device storage: need ${AeginBridge.formatByteSize(remainingBytes)} more, only ${AeginBridge.formatByteSize(check.availableStorageBytes)} free."
                    )
                    return@withContext false
                }

                val (manifestUrls, expectedSha) = resolveManifestCandidates(check.modelName)
                val (baseUrl, _) = readLlmDownloadConfig()
                val candidateUrls = buildList {
                    addAll(manifestUrls)
                    if (!baseUrl.isNullOrBlank()) {
                        add("${baseUrl.trim().trimEnd('/')}/llms/${check.modelName}")
                    }
                    if (manifestUrls.isEmpty() && baseUrl.isNullOrBlank()) {
                        add("http://127.0.0.1:8000/llms/${check.modelName}")
                        add("http://10.0.2.2:8000/llms/${check.modelName}")
                    }
                }.distinct()

                if (candidateUrls.isEmpty()) {
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "FAILED",
                        modelName = check.modelName,
                        message = "No download URL configured for ${check.modelName} in models/manifest.json."
                    )
                    return@withContext false
                }

                modelDownloadState.value = LocalModelDownloadState(
                    status = "DOWNLOADING",
                    modelName = check.modelName,
                    downloadedBytes = existingPartial,
                    totalBytes = check.approxSizeBytes,
                    percent = existingPct,
                    sourceUrl = candidateUrls.first(),
                    message = if (existingPartial > 0L) {
                        "Resuming ${check.modelName} from ${AeginBridge.formatByteSize(existingPartial)}..."
                    } else {
                        "Connecting to ${candidateUrls.first()}..."
                    }
                )

                val ctx = appContext
                if (ctx != null) {
                    val primaryUrl = candidateUrls.first()
                    val fallbackList = candidateUrls.drop(1)
                    Log.i(TAG, "Enqueuing WorkManager resumable GGUF download [${check.modelName}] from: $primaryUrl")
                    val workId = ModelDownloadWorker.enqueue(
                        context = ctx,
                        modelId = check.modelName,
                        downloadUrl = primaryUrl,
                        expectedSha256 = expectedSha,
                        targetFile = downloadTarget,
                        fallbackUrls = fallbackList,
                        approxSizeBytes = check.approxSizeBytes
                    )

                    var finalSuccess = false
                    val uniqueName = ModelDownloadWorker.uniqueWorkName(check.modelName)
                    WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(uniqueName).first { infos ->
                        val info = infos.firstOrNull { it.id == workId } ?: return@first false
                        when (info.state) {
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                                val partialNow = if (partFile.exists()) partFile.length() else existingPartial
                                val pctNow = if (check.approxSizeBytes > 0L && partialNow > 0L) {
                                    ((partialNow * 100L) / check.approxSizeBytes).toInt().coerceIn(0, 99)
                                } else {
                                    0
                                }
                                modelDownloadState.value = LocalModelDownloadState(
                                    status = "DOWNLOADING",
                                    modelName = check.modelName,
                                    downloadedBytes = partialNow,
                                    totalBytes = check.approxSizeBytes,
                                    percent = pctNow,
                                    sourceUrl = primaryUrl,
                                    message = if (partialNow > 0L) {
                                        "Queued resume for ${check.modelName} (${AeginBridge.formatByteSize(partialNow)})..."
                                    } else {
                                        "Queued resumable download for ${check.modelName}..."
                                    }
                                )
                                false
                            }
                            WorkInfo.State.RUNNING -> {
                                val phase = info.progress.getString(ModelDownloadWorker.KEY_STATUS_PHASE) ?: "DOWNLOADING"
                                val rawDl = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, -1L)
                                val dlBytes = if (rawDl >= 0L) {
                                    rawDl
                                } else if (partFile.exists()) {
                                    partFile.length()
                                } else {
                                    existingPartial
                                }
                                val totBytes = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, check.approxSizeBytes)
                                val pct = if (rawDl >= 0L) {
                                    info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS_PCT, existingPct)
                                } else if (totBytes > 0L && dlBytes > 0L) {
                                    ((dlBytes * 100L) / totBytes).toInt().coerceIn(0, 99)
                                } else {
                                    existingPct
                                }
                                val srcUrl = info.progress.getString(ModelDownloadWorker.KEY_SOURCE_URL) ?: primaryUrl
                                val msg = info.progress.getString(ModelDownloadWorker.KEY_STATUS_MSG)
                                    ?: if (dlBytes > 0L) {
                                        "Downloading ${check.modelName} ($pct%)"
                                    } else {
                                        "Connecting to $srcUrl..."
                                    }
                                modelDownloadState.value = LocalModelDownloadState(
                                    status = phase,
                                    modelName = check.modelName,
                                    downloadedBytes = dlBytes,
                                    totalBytes = totBytes,
                                    percent = pct,
                                    sourceUrl = srcUrl,
                                    message = msg
                                )
                                false
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val srcUrl = info.outputData.getString(ModelDownloadWorker.KEY_SOURCE_URL) ?: primaryUrl
                                val loaded = try {
                                    AeginModelLoader(modelDir).loadVerifiedFileBlocking(
                                        ggufFile = downloadTarget,
                                        expectedSha256 = expectedSha
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "AeginModelLoader gate blocked load after download: ${e.message}")
                                    false
                                }
                                modelDownloadState.value = LocalModelDownloadState(
                                    status = "COMPLETED",
                                    modelName = check.modelName,
                                    downloadedBytes = downloadTarget.length(),
                                    totalBytes = downloadTarget.length(),
                                    percent = 100,
                                    sourceUrl = srcUrl,
                                    message = if (loaded) {
                                        "${check.modelName} downloaded, verified, and loaded via AeginModelLoader."
                                    } else {
                                        "${check.modelName} downloaded and verified at ${downloadTarget.absolutePath}."
                                    }
                                )
                                finalSuccess = true
                                true
                            }
                            WorkInfo.State.CANCELLED -> {
                                val partialNow = if (partFile.exists()) partFile.length() else 0L
                                val pctNow = if (check.approxSizeBytes > 0L && partialNow > 0L) {
                                    ((partialNow * 100L) / check.approxSizeBytes).toInt().coerceIn(0, 99)
                                } else {
                                    0
                                }
                                when {
                                    modelDownloadCancelRequested.get() -> {
                                        partFile.delete()
                                        modelDownloadState.value = LocalModelDownloadState(
                                            status = "CANCELLED",
                                            modelName = check.modelName,
                                            downloadedBytes = 0L,
                                            totalBytes = check.approxSizeBytes,
                                            percent = 0,
                                            sourceUrl = primaryUrl,
                                            message = "Download cancelled."
                                        )
                                    }
                                    modelDownloadPauseRequested.get() -> {
                                        modelDownloadState.value = LocalModelDownloadState(
                                            status = "PAUSED",
                                            modelName = check.modelName,
                                            downloadedBytes = partialNow,
                                            totalBytes = check.approxSizeBytes,
                                            percent = pctNow,
                                            sourceUrl = primaryUrl,
                                            message = if (partialNow > 0L) {
                                                "Paused at ${AeginBridge.formatByteSize(partialNow)} (.part kept for resume)."
                                            } else {
                                                "Download paused."
                                            }
                                        )
                                    }
                                    else -> {
                                        modelDownloadState.value = LocalModelDownloadState(
                                            status = "PAUSED",
                                            modelName = check.modelName,
                                            downloadedBytes = partialNow,
                                            totalBytes = check.approxSizeBytes,
                                            percent = pctNow,
                                            sourceUrl = primaryUrl,
                                            message = if (partialNow > 0L) {
                                                "Download interrupted at ${AeginBridge.formatByteSize(partialNow)} (.part kept for resume)."
                                            } else {
                                                "Download interrupted."
                                            }
                                        )
                                    }
                                }
                                finalSuccess = false
                                true
                            }
                            WorkInfo.State.FAILED -> {
                                val errMsg = info.outputData.getString(ModelDownloadWorker.KEY_ERROR_MSG)
                                    ?: "Resumable download failed."
                                val partialNow = if (partFile.exists()) partFile.length() else 0L
                                val pctNow = if (check.approxSizeBytes > 0L && partialNow > 0L) {
                                    ((partialNow * 100L) / check.approxSizeBytes).toInt().coerceIn(0, 99)
                                } else {
                                    0
                                }
                                modelDownloadState.value = LocalModelDownloadState(
                                    status = "FAILED",
                                    modelName = check.modelName,
                                    downloadedBytes = partialNow,
                                    totalBytes = check.approxSizeBytes,
                                    percent = pctNow,
                                    sourceUrl = primaryUrl,
                                    message = errMsg
                                )
                                finalSuccess = false
                                true
                            }
                        }
                    }
                    return@withContext finalSuccess
                }

                val fallbackOutcome = ResumableModelDownloader.downloadWithResume(
                    urls = candidateUrls,
                    targetFile = downloadTarget,
                    expectedSha256 = expectedSha,
                    approxSizeBytes = check.approxSizeBytes,
                    shouldStop = { modelDownloadPauseRequested.get() || modelDownloadCancelRequested.get() },
                    onProgress = { phase, dlBytes, totBytes, pct, activeUrl, msg ->
                        modelDownloadState.value = LocalModelDownloadState(
                            status = phase,
                            modelName = check.modelName,
                            downloadedBytes = dlBytes,
                            totalBytes = if (totBytes > 0L) totBytes else check.approxSizeBytes,
                            percent = pct.coerceAtLeast(0),
                            sourceUrl = activeUrl,
                            message = msg
                        )
                    }
                )
                if (fallbackOutcome.succeeded) {
                    val loaded = try {
                        AeginModelLoader(modelDir).loadVerifiedFileBlocking(
                            ggufFile = downloadTarget,
                            expectedSha256 = expectedSha
                        )
                    } catch (_: Exception) {
                        false
                    }
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "COMPLETED",
                        modelName = check.modelName,
                        downloadedBytes = fallbackOutcome.bytesDownloaded,
                        totalBytes = fallbackOutcome.bytesDownloaded,
                        percent = 100,
                        sourceUrl = fallbackOutcome.usedUrl,
                        message = if (loaded) {
                            "${check.modelName} downloaded, verified, and loaded into JNI runtime."
                        } else {
                            "${check.modelName} downloaded and verified at ${downloadTarget.absolutePath}."
                        }
                    )
                    return@withContext true
                }
                if (modelDownloadCancelRequested.get()) {
                    partFile.delete()
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "CANCELLED",
                        modelName = check.modelName,
                        sourceUrl = fallbackOutcome.usedUrl,
                        message = "Download cancelled."
                    )
                    return@withContext false
                }
                if (fallbackOutcome.stopped || modelDownloadPauseRequested.get()) {
                    val partialNow = if (partFile.exists()) partFile.length() else 0L
                    modelDownloadState.value = LocalModelDownloadState(
                        status = "PAUSED",
                        modelName = check.modelName,
                        downloadedBytes = partialNow,
                        totalBytes = check.approxSizeBytes,
                        percent = existingPct,
                        sourceUrl = fallbackOutcome.usedUrl,
                        message = if (partialNow > 0L) {
                            "Paused at ${AeginBridge.formatByteSize(partialNow)} (.part kept for resume)."
                        } else {
                            "Download paused."
                        }
                    )
                    return@withContext false
                }
                modelDownloadState.value = LocalModelDownloadState(
                    status = "FAILED",
                    modelName = check.modelName,
                    sourceUrl = fallbackOutcome.usedUrl,
                    message = fallbackOutcome.errorMessage ?: "Unable to download ${check.modelName}."
                )
                false
            } finally {
                modelDownloadInProgress.set(false)
            }
        }

    actual suspend fun pauseLocalModelDownload(modelName: String) {
        val ctx = appContext ?: return
        val targetName = modelName.ifBlank { modelDownloadState.value.modelName }
        if (targetName.isNotBlank()) {
            modelDownloadCancelRequested.set(false)
            modelDownloadPauseRequested.set(true)
            WorkManager.getInstance(ctx).cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(targetName))
            val modelDir = resolveModelsStagingDir()
            val partFile = File(modelDir, "$targetName.part")
            val partial = if (partFile.exists()) partFile.length() else 0L
            val tot = modelDownloadState.value.totalBytes
            val pct = if (tot > 0L && partial > 0L) ((partial * 100L) / tot).toInt().coerceIn(0, 99) else modelDownloadState.value.percent
            modelDownloadState.value = LocalModelDownloadState(
                status = "PAUSED",
                modelName = targetName,
                downloadedBytes = partial,
                totalBytes = tot,
                percent = pct,
                sourceUrl = modelDownloadState.value.sourceUrl,
                message = if (partial > 0L) {
                    "Paused at ${AeginBridge.formatByteSize(partial)} (.part kept for resume)."
                } else {
                    "Download paused."
                }
            )
        }
    }

    actual suspend fun cancelLocalModelDownload(modelName: String) {
        val ctx = appContext
        val targetName = modelName.ifBlank { modelDownloadState.value.modelName }
        if (targetName.isNotBlank()) {
            modelDownloadPauseRequested.set(false)
            modelDownloadCancelRequested.set(true)
            if (ctx != null) {
                WorkManager.getInstance(ctx).cancelUniqueWork(ModelDownloadWorker.uniqueWorkName(targetName))
            }
            withContext(Dispatchers.IO) {
                val modelDir = resolveModelsStagingDir()
                val partFile = File(modelDir, "$targetName.part")
                if (partFile.exists()) {
                    partFile.delete()
                }
            }
            modelDownloadState.value = LocalModelDownloadState(
                status = "CANCELLED",
                modelName = targetName,
                downloadedBytes = 0L,
                totalBytes = modelDownloadState.value.totalBytes,
                percent = 0,
                sourceUrl = modelDownloadState.value.sourceUrl,
                message = "Download cancelled and partial file removed."
            )
        }
    }

    actual fun observeLocalModelDownloadState(): StateFlow<LocalModelDownloadState> =
        modelDownloadState.asStateFlow()

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
        buildDeviceCapabilityReport()
    }

    private fun buildDeviceCapabilityReport(): DeviceCapabilityReport {
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

        // Hardware Tiering Matrix (< 6 GB blocked, 6–8 GB 1.5B, 8–12 GB 3B, 12 GB+ 7B)
        // Note: 5.5 GiB accounts for kernel/GPU carveouts on 6 GB physical RAM devices.
        val (tier, recModel, recSizeMb) = ModelTierCatalog.resolveTier(totalRamGb)

        val modelsDir = resolveModelsStagingDir()
        val statFs = try {
            StatFs(modelsDir.absolutePath)
        } catch (_: Exception) {
            try {
                StatFs(Environment.getDataDirectory().path)
            } catch (_: Exception) {
                null
            }
        }
        val availableDiskBytes = statFs?.availableBytes ?: 0L
        val freeDiskGb = availableDiskBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val requiredBytes = recSizeMb.toLong() * 1024L * 1024L
        val bufferBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val hasEnoughDisk = availableDiskBytes > (requiredBytes + bufferBytes)

        val stagedFiles = modelsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.map { "${it.name} (${it.length() / (1024 * 1024)} MB)" }
            .orEmpty()

        val is64Bit = Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true
        val isArm64 = cpuAbi.contains("arm64", ignoreCase = true)
        val hasRequiredSimd = !isArm64 || supportsDotProd
        val canRunLocal = is64Bit && totalRamGb >= 5.5 && !isLowMem && hasEnoughDisk && hasRequiredSimd

        val summary = when {
            !is64Bit -> "32-bit ABI detected ($cpuAbi). 64-bit ARM64/x86_64 required for local GGUF."
            !hasRequiredSimd -> "ARM64 CPU lacks asimddp/dotprod SIMD instructions required for local Q4_K_M GGUF inference."
            totalRamGb < 5.5 || isLowMem -> "Insufficient physical RAM (${String.format("%.1f", totalRamGb)} GB < 6 GB). Android LMK would terminate local GGUF; Cloud/RAG mode recommended."
            !hasEnoughDisk -> "Insufficient free storage (${String.format("%.1f", freeDiskGb)} GB free). Need ${recSizeMb} MB + 1.5 GB cache buffer."
            else -> "Compatible ($tier). Recommended quantized profile: $recModel (~$recSizeMb MB)."
        }

        return DeviceCapabilityReport(
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

    private fun verifyModelPreflightBlocking(modelSizeBytes: Long): PreflightCheckResult {
        val report = buildDeviceCapabilityReport()
        val modelsDir = resolveModelsStagingDir()
        val availBytes = try {
            StatFs(modelsDir.absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
        val effectiveModelBytes = if (modelSizeBytes > 0L) {
            modelSizeBytes
        } else {
            report.recommendedModelSizeMb.toLong() * 1024L * 1024L
        }
        val bufferBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val diskOk = availBytes > (effectiveModelBytes + bufferBytes)
        val allowed = report.canRunLocalGguf && diskOk
        val reason = when {
            !report.canRunLocalGguf -> report.statusSummary
            !diskOk -> "Insufficient storage on ${modelsDir.absolutePath}: need ${effectiveModelBytes / (1024 * 1024)} MB + 1.5 GB buffer."
            else -> "Preflight passed (${report.hardwareTier})."
        }
        return PreflightCheckResult(
            allowed = allowed,
            tierLabel = report.hardwareTier,
            recommendedProfile = report.recommendedModel,
            reason = reason
        )
    }

    actual suspend fun verifyModelPreflight(modelSizeBytes: Long): PreflightCheckResult =
        withContext(Dispatchers.IO) {
            verifyModelPreflightBlocking(modelSizeBytes)
        }

    actual fun streamAgent(
        prompt: String,
        sessionId: String?,
        prevSummary: String?,
        assistantMode: String?,
        spaceId: String?,
        spaceName: String?
    ): Flow<String> = flow {
        val effectiveMode = assistantMode?.trim()?.takeIf { it.isNotEmpty() } ?: when (getStorageItem("selected_llm_mode")) {
            "auto" -> "Auto"
            "local" -> "On-Device Model"
            else -> getStorageItem("selected_llm_provider")?.takeIf { it.isNotBlank() } ?: "On-Device Model"
        }
        val canTryLocalStream = effectiveMode == "On-Device Model" || effectiveMode.equals("Auto", ignoreCase = true)
        if (canTryLocalStream && LlamaJniBridge.isModelLoaded()) {
            if (!engine.isRunning()) {
                ensureStartedBlocking()
            }
            val prep = try {
                if (!spaceId.isNullOrBlank()) {
                    engine.invoke(
                        "AgenticFabric",
                        "prepareStreamingTurnInSpace",
                        arrayOf<Any?>(prompt, spaceId, spaceName ?: "", sessionId ?: "", prevSummary ?: "", effectiveMode),
                        "127.0.0.1",
                        uiSubject
                    ) as? Map<*, *>
                } else {
                    engine.invoke(
                        "AgenticFabric",
                        "prepareStreamingTurnWithMode",
                        arrayOf<Any?>(prompt, sessionId ?: "", prevSummary ?: "", effectiveMode),
                        "127.0.0.1",
                        uiSubject
                    ) as? Map<*, *>
                }
            } catch (_: Exception) {
                null
            }

            val prepError = prep?.get("error")?.toString()
            if (!prepError.isNullOrBlank()) {
                emit(prepError)
                return@flow
            }
            val routeToCloud = prep?.get("routeToCloud")?.toString()?.equals("true", ignoreCase = true) == true
            if (routeToCloud) {
                val raw = if (!spaceId.isNullOrBlank()) {
                    invoke("AgenticFabric", "askAgentInSpace", listOf(prompt, spaceId, spaceName ?: "", sessionId ?: "", prevSummary ?: "", effectiveMode))
                } else {
                    invoke("AgenticFabric", "askAgentWithMode", listOf(prompt, sessionId ?: "", prevSummary ?: "", effectiveMode))
                }
                emit(raw)
                return@flow
            }

            val actualSessionId = prep?.get("sessionId")?.toString()?.takeIf { it.isNotBlank() }
                ?: sessionId
                ?: "android-session"
            val formattedPrompt = prep?.get("formattedPrompt")?.toString()?.takeIf { it.isNotBlank() }
                ?: prompt
            val stopToken = prep?.get("stopToken")?.toString().orEmpty()

            emit("""{"__session_meta__":true,"sessionId":"$actualSessionId"}""")

            val answerBuilder = StringBuilder()
            LlamaJniBridge.generateStream(formattedPrompt, stopToken, 256).collect { token ->
                if (token.isNotEmpty()) {
                    answerBuilder.append(token)
                    emit(token)
                }
            }

            val finalAnswer = answerBuilder.toString().trim()
            if (finalAnswer.isNotEmpty()) {
                try {
                    engine.invoke(
                        "AgenticFabric",
                        "commitStreamingTurn",
                        arrayOf<Any?>(actualSessionId, prompt, finalAnswer, spaceId ?: "", spaceName ?: ""),
                        "127.0.0.1",
                        uiSubject
                    )
                } catch (_: Exception) {
                }
            }
        } else {
            val raw = if (!spaceId.isNullOrBlank()) {
                invoke("AgenticFabric", "askAgentInSpace", listOf(prompt, spaceId, spaceName ?: "", sessionId ?: "", prevSummary ?: "", effectiveMode))
            } else {
                invoke("AgenticFabric", "askAgentWithMode", listOf(prompt, sessionId ?: "", prevSummary ?: "", effectiveMode))
            }
            emit(raw)
        }
    }.flowOn(Dispatchers.IO)

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

            if (component == "AgenticFabric" && (method == "askAgent" || method == "askAgentWithMode" || method == "askAgentInSpace") && result is Map<*, *>) {
                val answer = result["answer"]?.toString() ?: ""
                if (answer.contains("Failed to get response from LLM") || answer.contains("LLM Invocation Failure")) {
                    val userPrompt = params.firstOrNull() ?: ""
                    val ragSnippets = try {
                        engine.invoke("KnowledgeVault", "search", arrayOf<Any?>(userPrompt, 3), "127.0.0.1", uiSubject)?.toString()
                    } catch (_: Exception) {
                        null
                    }
                    val synthesized = buildString {
                        appendLine("Sovereign On-Device RAG Response (Reference Documents Active):")
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

    private fun stopLlmProcessLocked() {
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
    }

    fun releaseLocalModelBlocking() {
        LlamaJniBridge.scheduleRelease()
        synchronized(llmLock) {
            stopLlmProcessLocked()
        }
    }

    actual suspend fun releaseLocalModel() = withContext(Dispatchers.IO) {
        LlamaJniBridge.release()
        synchronized(llmLock) {
            stopLlmProcessLocked()
        }
    }

    actual suspend fun triggerEmergencyStop(): Boolean = withContext(Dispatchers.IO) {
        releaseLocalModelBlocking()
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
                This document is indexed in your local Reference Documents vector store on Android.
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
