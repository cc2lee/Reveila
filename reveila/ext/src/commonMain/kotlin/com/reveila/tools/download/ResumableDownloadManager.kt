package com.reveila.tools.download

import com.reveila.error.SecurityException
import com.reveila.system.SystemComponent
import com.reveila.system.concurrency.PlatformCancellable
import com.reveila.system.concurrency.PlatformLock
import com.reveila.system.concurrency.PlatformScheduler
import com.reveila.system.io.PlatformFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmOverloads

/**
 * Generic resumable download manager tool for Reveila apps.
 *
 * Features:
 * - Pre-download & in-stream storage checks across temp and destination volumes.
 * - Pause, resume (HTTP Range), and cancel with `.part` + `.part.meta` staging in `system-home/temp`.
 * - SHA-256 integrity verification prior to atomic promotion.
 * - Periodic housekeeping (`runHousekeeping`) to prevent abandoned temp files from accumulating.
 */
open class ResumableDownloadManager @JvmOverloads constructor(
    private var configuredSystemHome: String? = null,
    private var configuredTempDir: String? = null,
    private val defaultTempTtlMillis: Long = DEFAULT_TEMP_TTL_MS,
    private val maxTempBytes: Long = DEFAULT_MAX_TEMP_BYTES,
    private val fs: PlatformFileSystem = PlatformFileSystem(),
    private val transport: PlatformDownloadTransport = createPlatformDownloadTransport()
) : SystemComponent(), DownloadManager {

    companion object {
        const val COMPONENT_NAME = "ResumableDownloadManager"
        const val TEMP_DOWNLOADS_SUBDIR = "temp/downloads"
        const val DEFAULT_TEMP_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        const val DEFAULT_MAX_TEMP_BYTES = 0L
        const val PART_SUFFIX = ".part"
        const val META_SUFFIX = ".part.meta"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    }

    private inner class ManagedDownload(
        var request: DownloadRequest,
        val tempFilePath: String,
        val metaFilePath: String
    ) : DownloadHandle {
        override val downloadId: String = request.effectiveId()

        val _state = MutableStateFlow(
            DownloadProgress(
                downloadId = downloadId,
                status = DownloadStatus.IDLE,
                tempFilePath = tempFilePath,
                destinationPath = request.destinationPath
            )
        )
        override val state: StateFlow<DownloadProgress> = _state.asStateFlow()

        @Volatile
        var pauseRequested: Boolean = false

        @Volatile
        var cancelRequested: Boolean = false

        @Volatile
        var activeJob: Job? = null

        @Volatile
        var callback: DownloadCallback? = null

        val transferMutex = Mutex()

        @Volatile
        var abortActiveConnection: (() -> Unit)? = null

        override fun pause(): Boolean = this@ResumableDownloadManager.pause(downloadId)

        override fun resume(callback: DownloadCallback?): Boolean {
            return this@ResumableDownloadManager.resume(downloadId, callback) != null
        }

        override fun cancel(): Boolean = this@ResumableDownloadManager.cancel(downloadId)

        override fun getStatus(): DownloadStatus = _state.value.status
    }

    private val lock = PlatformLock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = mutableMapOf<String, ManagedDownload>()

    private var standaloneScheduler: PlatformScheduler? = null
    private var housekeepingTask: PlatformCancellable? = null

    @Throws(Exception::class)
    override fun onStart() {
        resolveTempDirectory()
        val report = runHousekeeping()
        logger.info(
            "[ResumableDownloadManager] Started (temp=${resolveTempDirectory()}, cleaned=${report.deletedFiles} files)."
        )
    }

    @Throws(Exception::class)
    override fun onStop() {
        val activeIds = lock.withLock {
            entries.values
                .filter { isStatusActive(it._state.value.status) }
                .map { it.downloadId }
        }
        activeIds.forEach { pause(it) }
        stopPeriodicHousekeeping()
        scope.cancel()
        logger.info("[ResumableDownloadManager] Stopped.")
    }

    /**
     * Resolves the temp staging directory (defaults to `<systemHome>/temp/downloads`).
     */
    fun resolveTempDirectory(overrideTempDir: String? = null): String {
        val explicit = overrideTempDir?.trim()?.takeIf { it.isNotEmpty() }
            ?: configuredTempDir?.trim()?.takeIf { it.isNotEmpty() }
        val resolved = if (explicit != null) {
            explicit
        } else {
            val sysHome = configuredSystemHome?.trim()?.takeIf { it.isNotEmpty() }
                ?: context?.properties?.getProperty("system.home")?.trim()?.takeIf { it.isNotEmpty() }
                ?: fs.getDefaultSystemHome()
            fs.resolve(sysHome, TEMP_DOWNLOADS_SUBDIR)
        }
        if (!fs.exists(resolved)) {
            fs.createDirectories(resolved)
        }
        return resolved
    }

    override fun checkAvailableStorage(targetPath: String): Long {
        return transport.getAvailableStorageBytes(targetPath)
    }

    override fun hasEnoughStorage(
        requiredBytes: Long,
        destinationPath: String,
        tempDir: String?,
        safetyBufferBytes: Long
    ): Boolean {
        if (requiredBytes <= 0L) return true
        val needed = requiredBytes + safetyBufferBytes.coerceAtLeast(0L)
        val destFree = transport.getAvailableStorageBytes(destinationPath)
        if (destFree in 0L until needed) return false
        val staging = resolveTempDirectory(tempDir)
        val tempFree = transport.getAvailableStorageBytes(staging)
        if (tempFree in 0L until needed) return false
        return true
    }

    override fun download(request: DownloadRequest, callback: DownloadCallback?): DownloadHandle {
        val id = request.effectiveId()
        val stagingDir = resolveTempDirectory(request.tempDir)
        val tempFilePath = fs.resolve(stagingDir, "$id$PART_SUFFIX")
        val metaFilePath = fs.resolve(stagingDir, "$id$META_SUFFIX")

        var shouldStart = true
        val entry = lock.withLock {
            val existing = entries[id]
            if (existing != null) {
                if (isStatusActive(existing._state.value.status)) {
                    if (callback != null) existing.callback = callback
                    shouldStart = false
                    existing
                } else {
                    existing.request = request
                    existing.callback = callback
                    existing
                }
            } else {
                ManagedDownload(request, tempFilePath, metaFilePath).also {
                    it.callback = callback
                    entries[id] = it
                }
            }
        }

        if (shouldStart) {
            launchTransfer(entry)
        }
        return entry
    }

    override fun pause(downloadId: String): Boolean {
        val key = sanitizeId(downloadId)
        val entry = lock.withLock { entries[key] ?: entries[downloadId] } ?: return false
        val status = entry._state.value.status
        if (status == DownloadStatus.COMPLETED || status == DownloadStatus.CANCELLED) {
            return false
        }
        entry.cancelRequested = false
        entry.pauseRequested = true
        entry.abortActiveConnection?.invoke()
        entry.activeJob?.cancel()

        val partial = transport.getFileSize(entry.tempFilePath).coerceAtLeast(0L)
        val total = entry._state.value.totalBytes.takeIf { it > 0L } ?: entry.request.expectedSizeBytes
        val pct = computePercent(partial, total)
        val pausedState = DownloadProgress(
            downloadId = entry.downloadId,
            status = DownloadStatus.PAUSED,
            percentage = pct,
            downloadedBytes = partial,
            totalBytes = total,
            activeUrl = entry._state.value.activeUrl,
            tempFilePath = entry.tempFilePath,
            destinationPath = entry.request.destinationPath,
            message = "Paused at $partial bytes (.part retained for resume)."
        )
        entry._state.value = pausedState
        entry.callback?.onProgressDetailed(pausedState)
        return true
    }

    override fun resume(downloadId: String, callback: DownloadCallback?): DownloadHandle? {
        val key = sanitizeId(downloadId)
        val existing = lock.withLock { entries[key] ?: entries[downloadId] }
        if (existing != null) {
            if (isStatusActive(existing._state.value.status)) {
                if (callback != null) existing.callback = callback
                return existing
            }
            if (callback != null) existing.callback = callback
            launchTransfer(existing)
            return existing
        }

        // Recover from persisted .part.meta sidecar if app restarted
        val meta = listIncompleteDownloads().firstOrNull {
            it.downloadId == downloadId || it.downloadId == key
        } ?: return null

        val recoveredRequest = DownloadRequest(
            downloadId = meta.downloadId,
            url = meta.url,
            fallbackUrls = meta.fallbackUrls,
            destinationPath = meta.destinationPath,
            tempDir = meta.tempFilePath.replace('\\', '/').substringBeforeLast('/', ""),
            expectedSha256 = meta.expectedSha256,
            expectedSizeBytes = meta.expectedSizeBytes
        )
        return download(recoveredRequest, callback)
    }

    override fun cancel(downloadId: String): Boolean {
        val key = sanitizeId(downloadId)
        val entry = lock.withLock { entries[key] ?: entries[downloadId] }
        if (entry == null) {
            // Even if not tracked in memory, wipe matching temp/meta files if present
            val stagingDir = resolveTempDirectory()
            val part = fs.resolve(stagingDir, "$key$PART_SUFFIX")
            val meta = fs.resolve(stagingDir, "$key$META_SUFFIX")
            var removed = false
            if (fs.exists(part)) removed = fs.delete(part, recursive = false) || removed
            if (fs.exists(meta)) removed = fs.delete(meta, recursive = false) || removed
            return removed
        }

        entry.pauseRequested = false
        entry.cancelRequested = true
        entry.abortActiveConnection?.invoke()
        entry.activeJob?.cancel()

        fs.delete(entry.tempFilePath, recursive = false)
        fs.delete(entry.metaFilePath, recursive = false)

        val cancelledState = DownloadProgress(
            downloadId = entry.downloadId,
            status = DownloadStatus.CANCELLED,
            percentage = 0,
            downloadedBytes = 0L,
            totalBytes = entry.request.expectedSizeBytes,
            tempFilePath = entry.tempFilePath,
            destinationPath = entry.request.destinationPath,
            message = "Download cancelled and partial temp file removed."
        )
        entry._state.value = cancelledState
        entry.callback?.onProgressDetailed(cancelledState)
        return true
    }

    override fun getProgress(downloadId: String): DownloadProgress? {
        val key = sanitizeId(downloadId)
        return lock.withLock { (entries[key] ?: entries[downloadId])?._state?.value }
    }

    override fun listIncompleteDownloads(): List<DownloadMetadata> {
        val stagingDir = resolveTempDirectory()
        if (!fs.exists(stagingDir)) return emptyList()
        return transport.listFiles(stagingDir)
            .filter { it.endsWith(META_SUFFIX) }
            .mapNotNull { metaPath ->
                try {
                    val meta = json.decodeFromString<DownloadMetadata>(fs.readText(metaPath))
                    if (fs.exists(meta.tempFilePath)) meta else null
                } catch (_: Exception) {
                    null
                }
            }
    }

    /**
     * Scheduled entry point invoked by Reveila `standard.json` `"runnable"` or manually.
     */
    open fun runHousekeeping(): TempCleanupReport {
        val ttlHours = context?.properties?.getProperty("download.temp.ttl.hours")?.toLongOrNull()
        val ttlMs = if (ttlHours != null && ttlHours > 0L) {
            ttlHours * 3600L * 1000L
        } else {
            defaultTempTtlMillis
        }
        val report = cleanupTempDirectory(ttlMs)
        if (report.deletedFiles > 0) {
            logger.info(
                "[ResumableDownloadManager] Housekeeping deleted ${report.deletedFiles} stale temp file(s), " +
                    "reclaimed ${report.reclaimedBytes / (1024 * 1024)} MB."
            )
        }
        return report
    }

    /**
     * Starts a standalone periodic housekeeping schedule when running outside Reveila's `standard.json` container.
     */
    fun startPeriodicHousekeeping(intervalSeconds: Long = 3600L, initialDelaySeconds: Long = 60L) {
        lock.withLock {
            if (housekeepingTask == null) {
                val scheduler = standaloneScheduler ?: PlatformScheduler(poolSize = 1).also {
                    standaloneScheduler = it
                }
                housekeepingTask = scheduler.scheduleWithFixedDelay(initialDelaySeconds, intervalSeconds) {
                    try {
                        runHousekeeping()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    fun stopPeriodicHousekeeping() {
        lock.withLock {
            housekeepingTask?.cancel(false)
            housekeepingTask = null
            standaloneScheduler?.shutdown()
            standaloneScheduler = null
        }
    }

    override fun cleanupTempDirectory(maxAgeMillis: Long): TempCleanupReport {
        val stagingDir = resolveTempDirectory()
        if (!fs.exists(stagingDir)) return TempCleanupReport()

        val now = Clock.System.now().toEpochMilliseconds()
        val activePaths = lock.withLock {
            entries.values
                .filter { isStatusActive(it._state.value.status) }
                .flatMap { listOf(it.tempFilePath, it.metaFilePath) }
                .toSet()
        }

        val allFiles = transport.listFiles(stagingDir)
        var deletedCount = 0
        var reclaimed = 0L
        var skippedActive = 0
        val deletedPaths = mutableSetOf<String>()
        val survivingInactiveParts = mutableListOf<Triple<String, Long, Long>>()

        fun deleteIfExists(path: String) {
            if (path in deletedPaths || !fs.exists(path)) return
            val sz = transport.getFileSize(path).coerceAtLeast(0L)
            if (fs.delete(path, recursive = false)) {
                deletedPaths.add(path)
                deletedCount++
                reclaimed += sz
            }
        }

        for (filePath in allFiles) {
            if (filePath in deletedPaths) continue
            if (filePath in activePaths) {
                skippedActive++
                continue
            }

            if (filePath.endsWith(META_SUFFIX)) {
                val correspondingPart = filePath.removeSuffix(META_SUFFIX) + PART_SUFFIX
                if (!fs.exists(correspondingPart)) {
                    deleteIfExists(filePath)
                    continue
                }
            }

            val mtime = transport.getLastModifiedEpochMs(filePath)
            val ageMs = if (mtime > 0L) (now - mtime).coerceAtLeast(0L) else 0L
            val isExpired = maxAgeMillis >= 0L && mtime > 0L && ageMs >= maxAgeMillis
            if (isExpired) {
                deleteIfExists(filePath)
                if (filePath.endsWith(PART_SUFFIX)) {
                    deleteIfExists(filePath.removeSuffix(PART_SUFFIX) + META_SUFFIX)
                }
            } else if (filePath.endsWith(PART_SUFFIX) || filePath.endsWith(".tmp")) {
                val sz = transport.getFileSize(filePath).coerceAtLeast(0L)
                survivingInactiveParts.add(Triple(filePath, mtime, sz))
            }
        }

        // Enforce optional maxTempBytes budget by evicting oldest inactive temp files first
        val configuredCap = context?.properties?.getProperty("download.temp.max.bytes")?.toLongOrNull()
            ?: maxTempBytes
        if (configuredCap > 0L) {
            var totalBytes = survivingInactiveParts.sumOf { it.third }
            if (totalBytes > configuredCap) {
                val oldestFirst = survivingInactiveParts.sortedBy { it.second }
                for ((path, _, sz) in oldestFirst) {
                    if (totalBytes <= configuredCap) break
                    if (path !in deletedPaths && fs.exists(path)) {
                        deleteIfExists(path)
                        totalBytes -= sz
                        if (path.endsWith(PART_SUFFIX)) {
                            deleteIfExists(path.removeSuffix(PART_SUFFIX) + META_SUFFIX)
                        }
                    }
                }
            }
        }

        return TempCleanupReport(
            scannedFiles = allFiles.size,
            deletedFiles = deletedCount,
            reclaimedBytes = reclaimed,
            skippedActiveFiles = skippedActive
        )
    }

    private fun launchTransfer(entry: ManagedDownload) {
        entry.pauseRequested = false
        entry.cancelRequested = false

        val job = scope.launch {
            entry.transferMutex.withLock {
                if (entry.pauseRequested || entry.cancelRequested) {
                    return@withLock
                }
                val req = entry.request
                val candidateUrls = buildList {
                    if (req.url.isNotBlank()) add(req.url.trim())
                    req.fallbackUrls.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
                }.distinct()

                if (candidateUrls.isEmpty()) {
                    failEntry(entry, IllegalArgumentException("No download URL specified for ${entry.downloadId}."))
                    return@withLock
                }

                // 1. Pre-download storage check
                emitState(
                    entry,
                    DownloadProgress(
                        downloadId = entry.downloadId,
                        status = DownloadStatus.CHECKING_STORAGE,
                        percentage = 0,
                        downloadedBytes = transport.getFileSize(entry.tempFilePath).coerceAtLeast(0L),
                        totalBytes = req.expectedSizeBytes,
                        activeUrl = candidateUrls.first(),
                        tempFilePath = entry.tempFilePath,
                        destinationPath = req.destinationPath,
                        message = "Checking available device storage..."
                    )
                )

                val existingPartial = transport.getFileSize(entry.tempFilePath).coerceAtLeast(0L)
                if (req.expectedSizeBytes > 0L) {
                    val remainingBytes = (req.expectedSizeBytes - existingPartial).coerceAtLeast(0L)
                    val needed = remainingBytes + req.minSafetyBufferBytes.coerceAtLeast(0L)
                    val stagingFree = transport.getAvailableStorageBytes(entry.tempFilePath)
                    val destFree = transport.getAvailableStorageBytes(req.destinationPath)
                    val minFree = listOf(stagingFree, destFree).filter { it >= 0L }.minOrNull() ?: -1L
                    if (minFree in 0L until needed) {
                        failEntry(
                            entry,
                            InsufficientStorageException(
                                message = "Insufficient storage: need $needed more bytes, only $minFree bytes available.",
                                requiredBytes = needed,
                                availableBytes = minFree
                            )
                        )
                        return@withLock
                    }
                }

                persistMeta(entry)

                var lastCallbackPct = -1
                val initPct = computePercent(existingPartial, req.expectedSizeBytes)
                emitState(
                    entry,
                    DownloadProgress(
                        downloadId = entry.downloadId,
                        status = DownloadStatus.DOWNLOADING,
                        percentage = initPct.coerceAtLeast(0),
                        downloadedBytes = existingPartial,
                        totalBytes = req.expectedSizeBytes,
                        activeUrl = candidateUrls.first(),
                        tempFilePath = entry.tempFilePath,
                        destinationPath = req.destinationPath,
                        message = if (existingPartial > 0L) "Resuming download..." else "Starting download..."
                    )
                )

                val result = try {
                    transport.streamToFile(
                        urls = candidateUrls,
                        tempFilePath = entry.tempFilePath,
                        expectedSizeBytes = req.expectedSizeBytes,
                        minSafetyBufferBytes = req.minSafetyBufferBytes,
                        headers = req.headers,
                        userAgent = req.userAgent,
                        connectTimeoutSeconds = req.connectTimeoutSeconds,
                        readTimeoutSeconds = req.readTimeoutSeconds,
                        shouldStop = { entry.pauseRequested || entry.cancelRequested },
                        onRegisterAbort = { abortFn -> entry.abortActiveConnection = abortFn },
                        onProgress = { dlBytes, totalBytes, activeUrl ->
                            val effectiveTotal = if (totalBytes > 0L) totalBytes else req.expectedSizeBytes
                            val pct = computePercent(dlBytes, effectiveTotal)
                            val progress = DownloadProgress(
                                downloadId = entry.downloadId,
                                status = DownloadStatus.DOWNLOADING,
                                percentage = pct,
                                downloadedBytes = dlBytes,
                                totalBytes = effectiveTotal,
                                activeUrl = activeUrl,
                                tempFilePath = entry.tempFilePath,
                                destinationPath = req.destinationPath,
                                message = "Downloading ($pct%)"
                            )
                            entry._state.value = progress
                            entry.callback?.onProgressDetailed(progress)
                            if (pct >= 0 && pct != lastCallbackPct) {
                                lastCallbackPct = pct
                                entry.callback?.onProgress(pct)
                            }
                        }
                    )
                } finally {
                    entry.abortActiveConnection = null
                }

                if (entry.cancelRequested) {
                    fs.delete(entry.tempFilePath, recursive = false)
                    fs.delete(entry.metaFilePath, recursive = false)
                    return@withLock
                }

                if (result.stopped || entry.pauseRequested) {
                    persistMeta(entry)
                    val partialNow = transport.getFileSize(entry.tempFilePath).coerceAtLeast(0L)
                    val totalNow = if (result.totalBytes > 0L) result.totalBytes else req.expectedSizeBytes
                    emitState(
                        entry,
                        DownloadProgress(
                            downloadId = entry.downloadId,
                            status = DownloadStatus.PAUSED,
                            percentage = computePercent(partialNow, totalNow),
                            downloadedBytes = partialNow,
                            totalBytes = totalNow,
                            activeUrl = result.usedUrl,
                            tempFilePath = entry.tempFilePath,
                            destinationPath = req.destinationPath,
                            message = "Paused at $partialNow bytes."
                        )
                    )
                    return@withLock
                }

                if (!result.succeeded) {
                    failEntry(entry, result.error ?: RuntimeException("Download failed for ${entry.downloadId}."))
                    return@withLock
                }

                // 2. SHA-256 verification (if expectedSha256 provided)
                val expectedSha = req.expectedSha256.trim().lowercase()
                if (expectedSha.isNotEmpty()) {
                    emitState(
                        entry,
                        DownloadProgress(
                            downloadId = entry.downloadId,
                            status = DownloadStatus.VERIFYING,
                            percentage = 99,
                            downloadedBytes = result.bytesDownloaded,
                            totalBytes = result.bytesDownloaded,
                            activeUrl = result.usedUrl,
                            tempFilePath = entry.tempFilePath,
                            destinationPath = req.destinationPath,
                            message = "Verifying SHA-256 integrity..."
                        )
                    )
                    val actualSha = transport.computeSha256Hex(entry.tempFilePath)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        fs.delete(entry.tempFilePath, recursive = false)
                        fs.delete(entry.metaFilePath, recursive = false)
                        failEntry(
                            entry,
                            SecurityException("Integrity verification failed (expected $expectedSha, got $actualSha).")
                        )
                        return@withLock
                    }
                }

                // 3. Promote from temp folder to final destination
                if (!transport.moveOrReplace(entry.tempFilePath, req.destinationPath)) {
                    failEntry(
                        entry,
                        RuntimeException("Failed to move verified file from ${entry.tempFilePath} to ${req.destinationPath}.")
                    )
                    return@withLock
                }
                fs.delete(entry.metaFilePath, recursive = false)

                val finalSize = transport.getFileSize(req.destinationPath).coerceAtLeast(result.bytesDownloaded)
                val completed = DownloadProgress(
                    downloadId = entry.downloadId,
                    status = DownloadStatus.COMPLETED,
                    percentage = 100,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    activeUrl = result.usedUrl,
                    tempFilePath = entry.tempFilePath,
                    destinationPath = req.destinationPath,
                    message = "Download complete."
                )
                emitState(entry, completed)
                if (lastCallbackPct != 100) {
                    entry.callback?.onProgress(100)
                }
                entry.callback?.onSuccess(req.destinationPath)
            }
        }
        entry.activeJob = job
    }

    private fun persistMeta(entry: ManagedDownload) {
        try {
            val req = entry.request
            val meta = DownloadMetadata(
                downloadId = entry.downloadId,
                url = req.url,
                fallbackUrls = req.fallbackUrls,
                destinationPath = req.destinationPath,
                tempFilePath = entry.tempFilePath,
                expectedSha256 = req.expectedSha256,
                expectedSizeBytes = req.expectedSizeBytes,
                updatedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            fs.writeText(entry.metaFilePath, json.encodeToString(meta), append = false)
        } catch (_: Exception) {
        }
    }

    private fun emitState(entry: ManagedDownload, progress: DownloadProgress) {
        entry._state.value = progress
        entry.callback?.onProgressDetailed(progress)
    }

    private fun failEntry(entry: ManagedDownload, error: Throwable) {
        val partial = transport.getFileSize(entry.tempFilePath).coerceAtLeast(0L)
        val total = entry._state.value.totalBytes.takeIf { it > 0L } ?: entry.request.expectedSizeBytes
        val failed = DownloadProgress(
            downloadId = entry.downloadId,
            status = DownloadStatus.FAILED,
            percentage = computePercent(partial, total).coerceAtLeast(0),
            downloadedBytes = partial,
            totalBytes = total,
            activeUrl = entry._state.value.activeUrl,
            tempFilePath = entry.tempFilePath,
            destinationPath = entry.request.destinationPath,
            message = error.message ?: "Download failed."
        )
        emitState(entry, failed)
        entry.callback?.onError(error)
    }

    private fun isStatusActive(status: DownloadStatus): Boolean =
        status == DownloadStatus.QUEUED ||
            status == DownloadStatus.CHECKING_STORAGE ||
            status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.VERIFYING

    private fun computePercent(downloaded: Long, total: Long): Int =
        if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else -1

    private fun sanitizeId(raw: String): String =
        raw.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
