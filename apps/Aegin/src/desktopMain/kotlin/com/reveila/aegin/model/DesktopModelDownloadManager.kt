package com.reveila.aegin.model

import com.reveila.aegin.bridge.LocalModelDownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop coroutine-backed resumable GGUF download manager (Windows / macOS / Linux JVM).
 * Uses SupervisorJob + exponential backoff retries while preserving `.part` files on pause/network loss,
 * delegating HTTP Range + SHA-256/GGUF streaming to [ResumableModelDownloader].
 */
class DesktopModelDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val _state = MutableStateFlow(LocalModelDownloadState())
    val state: StateFlow<LocalModelDownloadState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeModelName: String = ""

    private val pauseRequested = AtomicBoolean(false)

    suspend fun downloadAndAwait(
        modelName: String,
        candidateUrls: List<String>,
        targetFile: File,
        expectedSha256: String,
        approxSizeBytes: Long
    ): Boolean = queueMutex.withLock {
        pauseRequested.set(false)
        activeModelName = modelName

        val completion = CompletableDeferred<Boolean>()
        val job = scope.launch {
            try {
                val maxAttempts = 4
                var backoffMs = 2000L

                for (attempt in 1..maxAttempts) {
                    if (!isActive || pauseRequested.get()) {
                        emitPaused(modelName, targetFile, approxSizeBytes, candidateUrls.firstOrNull().orEmpty())
                        completion.complete(false)
                        return@launch
                    }

                    val outcome = ResumableModelDownloader.downloadWithResume(
                        urls = candidateUrls,
                        targetFile = targetFile,
                        expectedSha256 = expectedSha256,
                        approxSizeBytes = approxSizeBytes,
                        userAgent = "Aegin-Desktop-Downloader/1.0",
                        shouldStop = { !isActive || pauseRequested.get() },
                        onProgress = { phase, dlBytes, totBytes, pct, activeUrl, msg ->
                            _state.value = LocalModelDownloadState(
                                status = phase,
                                modelName = modelName,
                                downloadedBytes = dlBytes,
                                totalBytes = if (totBytes > 0L) totBytes else approxSizeBytes,
                                percent = pct.coerceAtLeast(0),
                                sourceUrl = activeUrl,
                                message = msg
                            )
                        }
                    )

                    if (outcome.succeeded) {
                        _state.value = LocalModelDownloadState(
                            status = "COMPLETED",
                            modelName = modelName,
                            downloadedBytes = outcome.bytesDownloaded,
                            totalBytes = outcome.bytesDownloaded,
                            percent = 100,
                            sourceUrl = outcome.usedUrl,
                            message = "$modelName downloaded and verified at ${targetFile.absolutePath}."
                        )
                        completion.complete(true)
                        return@launch
                    }

                    if (outcome.stopped || pauseRequested.get()) {
                        emitPaused(modelName, targetFile, approxSizeBytes, outcome.usedUrl)
                        completion.complete(false)
                        return@launch
                    }

                    if (attempt < maxAttempts) {
                        _state.value = LocalModelDownloadState(
                            status = "DOWNLOADING",
                            modelName = modelName,
                            downloadedBytes = outcome.bytesDownloaded,
                            totalBytes = approxSizeBytes,
                            percent = if (approxSizeBytes > 0L) ((outcome.bytesDownloaded * 100L) / approxSizeBytes).toInt().coerceIn(0, 99) else 0,
                            sourceUrl = outcome.usedUrl,
                            message = "Retrying $modelName (attempt ${attempt + 1}/$maxAttempts)..."
                        )
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    } else {
                        _state.value = LocalModelDownloadState(
                            status = "FAILED",
                            modelName = modelName,
                            downloadedBytes = outcome.bytesDownloaded,
                            totalBytes = approxSizeBytes,
                            percent = if (approxSizeBytes > 0L) ((outcome.bytesDownloaded * 100L) / approxSizeBytes).toInt().coerceIn(0, 99) else 0,
                            sourceUrl = outcome.usedUrl,
                            message = outcome.errorMessage ?: "Download failed after $maxAttempts attempts."
                        )
                        completion.complete(false)
                        return@launch
                    }
                }
            } catch (_: CancellationException) {
                emitPaused(modelName, targetFile, approxSizeBytes, candidateUrls.firstOrNull().orEmpty())
                completion.complete(false)
            } catch (e: Exception) {
                _state.value = LocalModelDownloadState(
                    status = "FAILED",
                    modelName = modelName,
                    sourceUrl = candidateUrls.firstOrNull().orEmpty(),
                    message = e.message ?: "Download failed"
                )
                completion.complete(false)
            } finally {
                activeJob = null
            }
        }
        activeJob = job
        completion.await()
    }

    fun pauseActiveDownload(modelName: String = activeModelName) {
        if (modelName.isBlank() || activeModelName.equals(modelName, ignoreCase = true)) {
            pauseRequested.set(true)
            activeJob?.cancel()
        }
    }

    private fun emitPaused(modelName: String, targetFile: File, approxSizeBytes: Long, sourceUrl: String) {
        val partFile = File("${targetFile.absolutePath}.part")
        val partialBytes = if (partFile.exists()) partFile.length() else 0L
        val pct = if (approxSizeBytes > 0L && partialBytes > 0L) {
            ((partialBytes * 100L) / approxSizeBytes).toInt().coerceIn(0, 99)
        } else {
            0
        }
        _state.value = LocalModelDownloadState(
            status = "PAUSED",
            modelName = modelName,
            downloadedBytes = partialBytes,
            totalBytes = approxSizeBytes,
            percent = pct,
            sourceUrl = sourceUrl,
            message = if (partialBytes > 0L) {
                "Paused at ${partialBytes / (1024 * 1024)} MB (.part kept for resume)."
            } else {
                "Download paused."
            }
        )
    }
}
