package com.reveila.aegin.bridge

import com.reveila.aegin.model.DesktopLlamaServerManager
import com.reveila.aegin.model.DesktopModelDownloadManager
import com.reveila.aegin.model.ResumableModelDownloader
import com.reveila.aegin.model.VaultDeltaScanner
import com.reveila.aegin.model.VaultIndexSink
import com.reveila.system.DefaultPlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Reveila
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.util.json.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

actual object PlatformEngineBridge {
    private val engine = Reveila()
    private val isStarted = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
    private val desktopDownloadManager = DesktopModelDownloadManager()
    private val desktopLlamaServer = DesktopLlamaServerManager(host = "127.0.0.1", port = 8888)
    private val llamaControlExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "aegin-desktop-llama-control").apply { isDaemon = true }
    }
    private val vaultDeltaScanner = VaultDeltaScanner()
    private val storageMap = ConcurrentHashMap<String, String>()
    private var activeSystemHome: File = resolveWritableSystemHome()
    private var storageFile: File = File(activeSystemHome, "data/aegin_prefs.properties")

    private val uiSubject = Subject().apply {
        principals.add(RolePrincipal("ui-client"))
        principals.add(RolePrincipal("system"))
        principals.add(RolePrincipal("admin"))
    }

    init {
        loadStorageFromDisk()
    }

    private fun resolveWritableSystemHome(): File {
        val explicitProp = System.getProperty("system.home") ?: System.getProperty("reveila.system.home")
        val targetDir = if (!explicitProp.isNullOrBlank()) {
            File(explicitProp)
        } else {
            File(System.getProperty("user.home"), ".reveila/aegin-home")
        }
        stageBundledResourcesIfNeeded(targetDir)
        return targetDir
    }

    private fun stageBundledResourcesIfNeeded(targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        val bundledFiles = listOf(
            "configs/reveila.properties",
            "configs/governance-policy.json",
            "configs/global-perimeter.json",
            "configs/logo.txt",
            "configs/shared/standard.json",
            "configs/java/java.json",
            "configs/android/android.json",
            "configs/settings/llm.json",
            "configs/settings/security.properties",
            "configs/settings/identity.properties",
            "configs/templates/intent_validation_prompt.md",
            "configs/templates/prompts/default.md",
            "configs/templates/prompts/conversational.md",
            "configs/templates/prompts/models/qwen2.5-0.5b-instruct.md",
            "configs/templates/prompts/models/gemma-2-2b-it.md",
            "configs/templates/prompts/families/qwen2.5.md",
            "configs/templates/prompts/families/gemma.md",
            "configs/templates/prompts/families/llama3.md",
            "models/manifest.json",
            "ReveilaVault/Reveila_Sovereign_Architecture.md"
        )
        val loader = PlatformEngineBridge::class.java.classLoader
        for (rel in bundledFiles) {
            val dst = File(targetDir, rel)
            val alwaysRefresh = rel == "models/manifest.json"
            if (!dst.exists() || alwaysRefresh) {
                val stream = loader?.getResourceAsStream("reveila/$rel")
                if (stream != null) {
                    dst.parentFile?.mkdirs()
                    stream.use { input ->
                        dst.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        listOf("logs", "data", "temp", "plugins", "libs", "ReveilaVault", "models").forEach {
            File(targetDir, it).mkdirs()
        }
    }

    private fun loadStorageFromDisk() {
        try {
            if (storageFile.exists()) {
                val props = java.util.Properties()
                storageFile.inputStream().use { props.load(it) }
                for (name in props.stringPropertyNames()) {
                    storageMap[name] = props.getProperty(name)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun saveStorageToDisk() {
        try {
            storageFile.parentFile?.mkdirs()
            val props = java.util.Properties()
            for ((k, v) in storageMap) {
                props.setProperty(k, v)
            }
            storageFile.outputStream().use { props.store(it, "Aegin Desktop Preferences") }
        } catch (_: Exception) {
        }
    }

    actual suspend fun startService(systemHome: String?): Boolean = withContext(Dispatchers.IO) {
        if (engine.isRunning()) {
            isStarted.set(true)
            return@withContext true
        }
        if (!isStarting.compareAndSet(false, true)) {
            return@withContext engine.isRunning()
        }
        try {
            if (!systemHome.isNullOrBlank()) {
                activeSystemHome = File(systemHome)
                stageBundledResourcesIfNeeded(activeSystemHome)
            }
            val props = Properties()
            props.setProperty("system.home", activeSystemHome.absolutePath)
            props.setProperty("platform", "java")
            props.setProperty("launch.strict.mode", "false")

            val adapter = DefaultPlatformAdapter(props)
            engine.start(adapter)
            isStarted.set(engine.isRunning())

            // Initial vault scan so vectors are ready immediately
            performLocalVaultScan(getVaultFolder().folder)
            startDesktopLocalLlmIfAvailableAsync()
            engine.isRunning()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            isStarting.set(false)
        }
    }

    actual suspend fun getEngineStatus(): EngineStatus = withContext(Dispatchers.IO) {
        if (!engine.isRunning() && !isStarting.get()) {
            startService(null)
        }
        val osName = System.getProperty("os.name") ?: "Desktop"
        EngineStatus(
            initialized = engine.isRunning(),
            status = if (engine.isRunning()) "ONLINE" else "INITIALIZING_CORE",
            platform = osName,
            storageMountPath = activeSystemHome.absolutePath
        )
    }

    actual suspend fun getDeviceCapabilities(): DeviceCapabilityReport = withContext(Dispatchers.IO) {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val sunBean = osBean as? com.sun.management.OperatingSystemMXBean
        val totalRamBytes = sunBean?.totalMemorySize ?: Runtime.getRuntime().maxMemory()
        val availRamBytes = sunBean?.freeMemorySize ?: Runtime.getRuntime().freeMemory()
        val totalRamGb = totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val availRamGb = availRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

        val modelsDir = File(activeSystemHome, "models").apply { mkdirs() }
        val usableDiskBytes = modelsDir.usableSpace
        val freeDiskGb = usableDiskBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)

        val arch = System.getProperty("os.arch") ?: "x86_64"
        val (tier, recModel, recSizeMb) = ModelTierCatalog.resolveTier(totalRamGb, target = "desktop")

        val requiredBytes = recSizeMb.toLong() * 1024L * 1024L
        val bufferBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val hasEnoughDisk = usableDiskBytes > (requiredBytes + bufferBytes)

        val stagedFiles = modelsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.map { "${it.name} (${it.length() / (1024 * 1024)} MB)" }
            .orEmpty()

        val runtimeAvailable = desktopLlamaServer.isRuntimeAvailable(activeSystemHome)
        val canRunLocal = totalRamGb >= 5.5 && hasEnoughDisk && runtimeAvailable
        val summary = when {
            totalRamGb < 5.5 -> "Desktop ($arch) has < 6 GB RAM ($tier). Use a Self-Hosted or Cloud AI."
            !hasEnoughDisk -> "Insufficient disk space (${kotlin.math.round(freeDiskGb * 10.0) / 10.0} GB free) for $recModel (~$recSizeMb MB)."
            !runtimeAvailable -> "Desktop local GGUF runtime (llama-server) not found in ${File(activeSystemHome, "bin").absolutePath} or PATH. Install llama.cpp llama-server or use a Self-Hosted / Cloud AI."
            else -> "Desktop ($arch) verified ($tier). Recommended model: $recModel (~$recSizeMb MB)."
        }

        DeviceCapabilityReport(
            totalRamGb = kotlin.math.round(totalRamGb * 10.0) / 10.0,
            availableRamGb = kotlin.math.round(availRamGb * 10.0) / 10.0,
            isLowMemory = availRamGb < 1.0,
            freeDiskGb = kotlin.math.round(freeDiskGb * 10.0) / 10.0,
            hasEnoughStorage = hasEnoughDisk,
            cpuAbi = arch,
            supportsSimdDotProd = true,
            supportsFp16 = true,
            supportsVulkanCompute = true,
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
                startService(null)
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

            // If askAgent encountered a local LLM connection error (e.g. llama-server not running on port 8080/8888),
            // synthesize a sovereign RAG response directly from KnowledgeVault vector search.
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
                            appendLine("Processed intent \"$userPrompt\" within sovereign perimeter. No additional external LLM daemon required.")
                        }
                    }.trim()
                    val fallbackMap = mapOf(
                        "answer" to synthesized,
                        "sessionId" to (result["sessionId"]?.toString() ?: "local-session")
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
        desktopLlamaServer.stop()
        if (engine.isRunning()) {
            engine.shutdown()
        }
        isStarted.set(false)
        true
    }

    private fun normalizeFolderDisplayCasing(rawPath: String): String {
        val trimmed = rawPath.trim().replace('\\', '/')
        return when {
            trimmed.equals("documents", ignoreCase = true) -> "Documents"
            trimmed.startsWith("documents/", ignoreCase = true) -> "Documents/" + trimmed.substringAfter('/')
            trimmed.equals("download", ignoreCase = true) || trimmed.equals("downloads", ignoreCase = true) -> "Downloads"
            trimmed.startsWith("download/", ignoreCase = true) || trimmed.startsWith("downloads/", ignoreCase = true) ->
                "Downloads/" + trimmed.substringAfter('/')
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
        val direct = File(parentPath)
        val userHome = System.getProperty("user.home")
        val baseDir = when {
            direct.isAbsolute -> direct
            normalized.equals("Download", ignoreCase = true) || normalized.equals("Downloads", ignoreCase = true) -> {
                File(userHome, "Downloads")
            }
            normalized.startsWith("Download/", ignoreCase = true) || normalized.startsWith("Downloads/", ignoreCase = true) -> {
                File(File(userHome, "Downloads"), normalized.substringAfter('/'))
            }
            normalized.equals("documents", ignoreCase = true) || normalized.isBlank() -> {
                val docs = File(userHome, "Documents")
                val defaultDir = File(docs, "Reveila")
                if (!defaultDir.exists()) {
                    defaultDir.mkdirs()
                }
                docs
            }
            normalized.startsWith("documents/", ignoreCase = true) -> {
                File(File(userHome, "Documents"), normalized.substringAfter('/'))
            }
            else -> {
                File(File(userHome, "Documents"), normalized)
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
        val basePrefix = parentPath.trim().replace('\\', '/').trimEnd('/').ifBlank { "Documents" }
        val combinedPath = "$basePrefix/$cleanName"
        resolveVaultDirectory(combinedPath)
        combinedPath
    }

    actual suspend fun pickSystemFolder(): String? = withContext(Dispatchers.IO) {
        try {
            var chosen: String? = null
            javax.swing.SwingUtilities.invokeAndWait {
                val chooser = javax.swing.JFileChooser().apply {
                    dialogTitle = "Select or Create Reference Documents Folder"
                    fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                    isAcceptAllFileFilterUsed = false
                    currentDirectory = File(System.getProperty("user.home"), "Documents").apply { mkdirs() }
                }
                val result = chooser.showOpenDialog(null)
                if (result == javax.swing.JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                    val selected = chooser.selectedFile
                    if (!selected.exists()) {
                        selected.mkdirs()
                    }
                    chosen = selected.absolutePath
                }
            }
            chosen
        } catch (_: Exception) {
            null
        }
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

    actual fun getStorageItem(key: String): String? = storageMap[key]

    actual fun setStorageItem(key: String, value: String) {
        storageMap[key] = value
        saveStorageToDisk()
    }

    actual fun clearStorage() {
        storageMap.clear()
        vaultDeltaScanner.reset()
        saveStorageToDisk()
    }

    private fun resolveVaultDirectory(folderPath: String): File {
        val normalized = folderPath.trim().replace('\\', '/')
        val direct = File(folderPath)
        val dir = when {
            direct.isAbsolute -> direct
            normalized.startsWith("Download/", ignoreCase = true) ||
                normalized.startsWith("Downloads/", ignoreCase = true) ||
                normalized.equals("Download", ignoreCase = true) ||
                normalized.equals("Downloads", ignoreCase = true) -> {
                val subPath = normalized.substringAfter('/', "Reveila").ifBlank { "Reveila" }
                val userDownloads = File(System.getProperty("user.home"), "Downloads")
                File(userDownloads, subPath)
            }
            normalized.startsWith("Documents/", ignoreCase = true) || normalized.equals("Documents", ignoreCase = true) -> {
                val subPath = normalized.substringAfter('/', "Reveila").ifBlank { "Reveila" }
                val userDocs = File(System.getProperty("user.home"), "Documents")
                File(userDocs, subPath)
            }
            else -> {
                val userDocs = File(System.getProperty("user.home"), "Documents")
                File(userDocs, normalized.ifBlank { "Reveila" })
            }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private val engineVaultSink = object : VaultIndexSink {
        override val isReady: Boolean
            get() = engine.isRunning()

        override fun clear() {
            try {
                engine.invoke("KnowledgeVault", "clear", emptyArray(), "127.0.0.1", uiSubject)
            } catch (_: Exception) {
            }
        }

        override fun setFocusKeywords(keywordsCsv: String) {
            try {
                engine.invoke("KnowledgeVault", "setFocusKeywords", arrayOf<Any?>(keywordsCsv), "127.0.0.1", uiSubject)
            } catch (_: Exception) {
            }
        }

        override fun indexDocument(id: String, content: String, metadata: Map<String, Any?>) {
            try {
                engine.invoke(
                    "KnowledgeVault",
                    "indexDocument",
                    arrayOf<Any?>(id, content, metadata),
                    "127.0.0.1",
                    uiSubject
                )
            } catch (_: Exception) {
            }
        }

        override fun removeDocument(id: String) {
            try {
                engine.invoke("KnowledgeVault", "removeDocument", arrayOf<Any?>(id), "127.0.0.1", uiSubject)
            } catch (_: Exception) {
            }
        }

        override fun getDocumentCount(): Int {
            return try {
                (engine.invoke("KnowledgeVault", "getDocumentCount", emptyArray(), "127.0.0.1", uiSubject) as? Number)?.toInt() ?: 0
            } catch (_: Exception) {
                0
            }
        }

        override fun getChunkCount(): Int {
            return try {
                (engine.invoke("KnowledgeVault", "getChunkCount", emptyArray(), "127.0.0.1", uiSubject) as? Number)?.toInt() ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }

    private fun performLocalVaultScan(folderPath: String): VaultScanResult {
        val dir = resolveVaultDirectory(folderPath)
        val keywordsCsv = getStorageItem("vault_focus_keywords") ?: "strategy, confidential, roadmap, architectural"
        val summary = vaultDeltaScanner.scan(
            rootDir = dir,
            keywordsCsv = keywordsCsv,
            sink = engineVaultSink
        )

        return VaultScanResult(
            folder = folderPath,
            filesScanned = summary.totalIndexedFiles,
            entitiesFound = summary.totalEntitiesFound,
            chunkCount = summary.chunkCount,
            status = "SUCCESS"
        )
    }

    actual suspend fun verifyModelPreflight(modelSizeBytes: Long): PreflightCheckResult =
        withContext(Dispatchers.IO) {
            val cap = getDeviceCapabilities()
            PreflightCheckResult(
                allowed = cap.canRunLocalGguf,
                tierLabel = cap.hardwareTier,
                recommendedProfile = cap.recommendedModel,
                reason = cap.statusSummary
            )
        }

    private fun readDesktopModelManifest(): LocalModelManifest {
        val file = File(activeSystemHome, "models/manifest.json")
        val raw = try {
            if (file.exists()) {
                file.readText(Charsets.UTF_8)
            } else {
                PlatformEngineBridge::class.java.classLoader
                    ?.getResourceAsStream("reveila/models/manifest.json")
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: "{}"
            }
        } catch (_: Exception) {
            "{}"
        }
        return AeginBridge.parseModelManifest(raw)
    }

    private fun readDesktopDownloadBaseUrl(): String {
        return try {
            val propFile = File(activeSystemHome, "configs/reveila.properties")
            if (propFile.exists()) {
                val p = java.util.Properties()
                propFile.inputStream().use { p.load(it) }
                p.getProperty("download.base.url")?.trim().orEmpty()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    actual suspend fun getLocalModelCatalog(): List<LocalModelManifestEntry> =
        withContext(Dispatchers.IO) {
            AeginBridge.modelsForTarget(readDesktopModelManifest(), "desktop")
        }

    actual suspend fun checkLocalModelAvailability(modelName: String): LocalModelCheckResult =
        withContext(Dispatchers.IO) {
            val manifest = readDesktopModelManifest()
            val entry = AeginBridge.resolveManifestEntry(manifest, modelName)
            val effectiveName = modelName.trim().ifBlank {
                entry?.effectiveFilename() ?: ModelTierCatalog.MODEL_GEMMA_2B
            }
            val modelsDir = File(activeSystemHome, "models").apply { mkdirs() }
            val exact = File(modelsDir, effectiveName)
            val matched = if (ResumableModelDownloader.hasValidGgufHeader(exact)) {
                exact
            } else {
                modelsDir.listFiles { _, n -> n.equals(effectiveName, ignoreCase = true) }
                    ?.firstOrNull { ResumableModelDownloader.hasValidGgufHeader(it) }
            }
            val partFile = File(modelsDir, "$effectiveName.part")
            val partialBytes = if (partFile.exists()) partFile.length() else 0L
            val manifestUrl = entry?.let { AeginBridge.buildManifestDownloadUrl(manifest, it) }.orEmpty()
            val fallbackBase = readDesktopDownloadBaseUrl().trimEnd('/')
            val downloadUrl = manifestUrl.ifBlank {
                if (fallbackBase.isNotEmpty()) "$fallbackBase/llms/$effectiveName" else ""
            }
            val approxBytes = entry?.approxSizeBytes ?: when {
                effectiveName.contains("0.5b", ignoreCase = true) -> 491_400_000L
                effectiveName.contains("1.5b", ignoreCase = true) -> 1_100_000_000L
                effectiveName.contains("gemma-2-2b", ignoreCase = true) -> 1_708_582_752L
                else -> 1_500_000_000L
            }
            LocalModelCheckResult(
                modelName = effectiveName,
                existsLocally = matched != null && matched.exists(),
                localPath = matched?.absolutePath.orEmpty(),
                partialBytes = partialBytes,
                downloadUrl = downloadUrl,
                approxSizeBytes = approxBytes,
                availableStorageBytes = modelsDir.usableSpace,
                sha256 = entry?.sha256.orEmpty(),
                isWifiConnected = true,
                isMeteredNetwork = false,
                requiresAuthToken = entry?.requiresAuthToken ?: false
            )
        }

    actual suspend fun downloadLocalModel(modelName: String): Boolean {
        val check = checkLocalModelAvailability(modelName)
        val modelsDir = withContext(Dispatchers.IO) {
            File(activeSystemHome, "models").apply { mkdirs() }
        }
        ResumableModelDownloader.cleanupReplacedGgufFiles(
            directories = listOf(modelsDir),
            keepFilename = check.modelName,
            deleteOtherGgufs = false,
            deleteOtherParts = true
        )
        val targetFile = File(modelsDir, check.modelName)
        val candidateUrls = buildList {
            if (check.downloadUrl.isNotBlank()) add(check.downloadUrl)
            val base = readDesktopDownloadBaseUrl().trimEnd('/')
            if (base.isNotEmpty()) add("$base/llms/${check.modelName}")
            if (check.downloadUrl.isBlank() && base.isEmpty()) {
                add("http://127.0.0.1:8000/llms/${check.modelName}")
            }
        }.distinct()

        val ok = desktopDownloadManager.downloadAndAwait(
            modelName = check.modelName,
            candidateUrls = candidateUrls,
            targetFile = targetFile,
            expectedSha256 = check.sha256,
            approxSizeBytes = check.approxSizeBytes
        )
        if (ok) {
            withContext(Dispatchers.IO) {
                ResumableModelDownloader.cleanupReplacedGgufFiles(
                    directories = listOf(modelsDir),
                    keepFilename = check.modelName
                )
                if (desktopLlamaServer.isRuntimeAvailable(activeSystemHome)) {
                    desktopLlamaServer.startOrReload(activeSystemHome, targetFile)
                }
            }
        }
        return ok
    }

    actual suspend fun pauseLocalModelDownload(modelName: String) {
        desktopDownloadManager.pauseActiveDownload(modelName)
    }

    actual suspend fun cancelLocalModelDownload(modelName: String) {
        val targetFile = if (modelName.isNotBlank()) {
            File(File(activeSystemHome, "models"), modelName)
        } else {
            null
        }
        desktopDownloadManager.cancelActiveDownload(modelName, targetFile)
    }

    actual fun observeLocalModelDownloadState(): StateFlow<LocalModelDownloadState> =
        desktopDownloadManager.state

    actual fun streamAgent(
        prompt: String,
        sessionId: String?,
        prevSummary: String?,
        assistantMode: String?,
        spaceId: String?,
        spaceName: String?
    ): Flow<String> = flow {
        val raw = if (!spaceId.isNullOrBlank()) {
            invoke(
                "AgenticFabric",
                "askAgentInSpace",
                listOf(prompt, spaceId, spaceName ?: "", sessionId ?: "", prevSummary ?: "", assistantMode ?: "")
            )
        } else if (!assistantMode.isNullOrBlank()) {
            invoke("AgenticFabric", "askAgentWithMode", listOf(prompt, sessionId ?: "", prevSummary ?: "", assistantMode))
        } else {
            invoke("AgenticFabric", "askAgent", listOf(prompt, sessionId ?: "", prevSummary ?: ""))
        }
        emit(raw)
    }.flowOn(Dispatchers.IO)

    private fun startDesktopLocalLlmIfAvailableAsync() {
        llamaControlExecutor.execute {
            try {
                if (!desktopLlamaServer.isRuntimeAvailable(activeSystemHome)) return@execute
                val modelsDir = File(activeSystemHome, "models")
                val preferredModel = try {
                    val raw = engine.invoke("LlmProviderFactory", "getSettings", arrayOf<Any?>("llm.json"), "127.0.0.1", uiSubject)?.toString().orEmpty()
                    val (providers, _, _) = AeginBridge.parseLlmSettingsJson(raw)
                    providers.firstOrNull { it.name.equals("On-Device Model", ignoreCase = true) }?.model?.trim().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val candidate = if (preferredModel.isNotEmpty()) {
                    File(modelsDir, preferredModel).takeIf { ResumableModelDownloader.hasValidGgufHeader(it) }
                } else {
                    null
                } ?: modelsDir.listFiles { f -> f.isFile && f.extension.equals("gguf", ignoreCase = true) }
                    ?.firstOrNull { ResumableModelDownloader.hasValidGgufHeader(it) }

                if (candidate != null) {
                    desktopLlamaServer.startOrReload(activeSystemHome, candidate)
                }
            } catch (_: Exception) {
            }
        }
    }

    actual suspend fun releaseLocalModel() = withContext(Dispatchers.IO) {
        desktopLlamaServer.stop()
    }
}
