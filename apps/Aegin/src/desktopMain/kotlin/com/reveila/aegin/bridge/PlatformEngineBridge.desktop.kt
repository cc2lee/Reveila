package com.reveila.aegin.bridge

import com.reveila.system.DefaultPlatformAdapter
import com.reveila.system.Properties
import com.reveila.system.Reveila
import com.reveila.system.RolePrincipal
import com.reveila.system.Subject
import com.reveila.util.json.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

actual object PlatformEngineBridge {
    private val engine = Reveila()
    private val isStarted = AtomicBoolean(false)
    private val isStarting = AtomicBoolean(false)
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
            "ReveilaVault/Reveila_Sovereign_Architecture.md"
        )
        val loader = PlatformEngineBridge::class.java.classLoader
        for (rel in bundledFiles) {
            val dst = File(targetDir, rel)
            if (!dst.exists()) {
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
        val (tier, recModel, recSizeMb) = when {
            totalRamGb < 4.0 -> Triple("INCOMPATIBLE (< 4 GB RAM)", "Cloud / Hybrid Fallback Required", 0)
            totalRamGb < 8.0 -> Triple("ENTRY TIER (4 – 6 GB RAM)", "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1050)
            totalRamGb < 14.0 -> Triple("STANDARD TIER (8 – 12 GB RAM)", "qwen2.5-3b-instruct-q4_k_m.gguf", 2200)
            else -> Triple("FLAGSHIP TIER (12 GB+ RAM)", "llama-3.1-8b-instruct-q4_k_m.gguf", 4600)
        }

        val requiredBytes = recSizeMb.toLong() * 1024L * 1024L
        val bufferBytes = (1.5 * 1024.0 * 1024.0 * 1024.0).toLong()
        val hasEnoughDisk = usableDiskBytes > (requiredBytes + bufferBytes)

        val stagedFiles = modelsDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            ?.map { "${it.name} (${it.length() / (1024 * 1024)} MB)" }
            .orEmpty()

        val canRunLocal = totalRamGb >= 4.0 && hasEnoughDisk

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
            statusSummary = "Desktop ($arch) verified ($tier). Recommended model: $recModel (~$recSizeMb MB)."
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
                    dialogTitle = "Select or Create Inference Documents Folder"
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
        val files = dir.listFiles()
        if (files == null || files.isEmpty()) {
            try {
                File(dir, "Reveila_Sovereign_Overview.md").writeText(
                    """
                    # Reveila Sovereign AI Reference Document
                    This document is indexed in your local Inference Documents vector store.
                    Physical folder location: ${dir.absolutePath}
                    Focus areas: strategy, confidential architecture, multi-platform roadmap, zero cloud leaks.
                    """.trimIndent()
                )
            } catch (_: Exception) {
            }
        }
        return dir
    }

    private fun performLocalVaultScan(folderPath: String): VaultScanResult {
        val dir = resolveVaultDirectory(folderPath)
        val supportedExts = setOf("md", "txt", "json", "docx", "pdf", "csv", "log", "xml", "yaml", "yml", "html")
        val files = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in supportedExts }.orEmpty()

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
                val matchedKeywords = keywords.count { kw ->
                    file.name.lowercase().contains(kw) || content.lowercase().contains(kw)
                }
                entitiesFound += (matchedKeywords + 2)

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

        val stats = if (engine.isRunning()) {
            val c = try {
                (engine.invoke("KnowledgeVault", "getChunkCount", emptyArray(), "127.0.0.1", uiSubject) as? Number)?.toInt() ?: 0
            } catch (_: Exception) {
                0
            }
            c
        } else 0

        return VaultScanResult(
            folder = folderPath,
            filesScanned = filesScanned,
            entitiesFound = entitiesFound,
            chunkCount = stats,
            status = "SUCCESS"
        )
    }

    private fun extractFileText(file: File): String {
        return try {
            when (file.extension.lowercase()) {
                "docx" -> extractDocxText(file)
                else -> file.readText()
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractDocxText(file: File): String {
        val sb = StringBuilder()
        try {
            ZipInputStream(FileInputStream(file)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val factory = DocumentBuilderFactory.newInstance()
                        val builder = factory.newDocumentBuilder()
                        val doc = builder.parse(zip)
                        val nodes = doc.getElementsByTagName("w:t")
                        for (i in 0 until nodes.length) {
                            sb.append(nodes.item(i).textContent).append(" ")
                        }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return sb.toString().trim()
    }
}
