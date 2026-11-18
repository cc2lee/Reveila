package com.reveila.aegin.model

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ConnectException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ResumableDownloadOutcome(
    val succeeded: Boolean,
    val stopped: Boolean = false,
    val fatal: Boolean = false,
    val usedUrl: String = "",
    val bytesDownloaded: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Shared JVM/Android Ktor + OkHttp streaming GGUF transfer engine.
 * Handles redirect resolution, HTTP Range resume into `.part` files,
 * GGUF magic header + SHA-256 verification, and atomic promotion.
 */
object ResumableModelDownloader {
    const val MIN_GGUF_BYTES = 1024L
    const val BUFFER_SIZE = 256 * 1024 // 256 KB chunks to avoid heap pressure on multi-GB weights
    const val NOTIFY_INTERVAL_MS = 750L

    fun hasValidGgufHeader(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() <= MIN_GGUF_BYTES) return false
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) < 4) return false
                magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun isVerifiedGgufFile(file: File, expectedSha256: String = ""): Boolean {
        if (!hasValidGgufHeader(file)) return false
        val cleanSha = expectedSha256.trim().lowercase()
        if (cleanSha.isEmpty()) return true
        return computeSha256Hex(file) == cleanSha
    }

    private fun isInsufficientStorageError(t: Throwable): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            val msg = cur.message?.lowercase().orEmpty()
            if (msg.contains("no space left") ||
                msg.contains("enospc") ||
                msg.contains("disk full") ||
                msg.contains("not enough space")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    suspend fun downloadWithResume(
        urls: List<String>,
        targetFile: File,
        expectedSha256: String = "",
        approxSizeBytes: Long = 0L,
        userAgent: String = "Aegin-Reveila-Downloader/1.0",
        shouldStop: () -> Boolean = { false },
        onProgress: suspend (
            phase: String,
            downloadedBytes: Long,
            totalBytes: Long,
            percent: Int,
            activeUrl: String,
            message: String
        ) -> Unit = { _, _, _, _, _, _ -> }
    ): ResumableDownloadOutcome {
        val cleanSha = expectedSha256.trim().lowercase()
        targetFile.parentFile?.mkdirs()

        // Short-circuit if final verified file already exists
        if (isVerifiedGgufFile(targetFile, cleanSha)) {
            val len = targetFile.length()
            return ResumableDownloadOutcome(
                succeeded = true,
                usedUrl = urls.firstOrNull().orEmpty(),
                bytesDownloaded = len
            )
        }

        val candidateUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (candidateUrls.isEmpty()) {
            return ResumableDownloadOutcome(
                succeeded = false,
                fatal = true,
                errorMessage = "No download URL configured for ${targetFile.name}."
            )
        }

        val partFile = File("${targetFile.absolutePath}.part")
        val existingPartial = if (partFile.exists()) partFile.length() else 0L
        if (approxSizeBytes > 0L) {
            val neededBytes = (approxSizeBytes - existingPartial).coerceAtLeast(0L)
            val parentDir = partFile.parentFile
            if (neededBytes > 0L && parentDir != null && parentDir.exists()) {
                val freeBytes = parentDir.usableSpace
                if (freeBytes < neededBytes) {
                    return ResumableDownloadOutcome(
                        succeeded = false,
                        fatal = true,
                        usedUrl = candidateUrls.first(),
                        bytesDownloaded = existingPartial,
                        errorMessage = "Insufficient on-device storage: need ${neededBytes / (1024 * 1024)} MB more, only ${freeBytes / (1024 * 1024)} MB free."
                    )
                }
            }
        }

        val client = HttpClient(OkHttp) {
            followRedirects = true
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

        return try {
            var lastErrorMsg: String? = null
            var primaryErrorMsg: String? = null
            for (url in candidateUrls) {
                if (shouldStop()) {
                    return ResumableDownloadOutcome(
                        succeeded = false,
                        stopped = true,
                        usedUrl = url,
                        bytesDownloaded = if (partFile.exists()) partFile.length() else 0L
                    )
                }
                try {
                    val outcome = downloadFromSingleEndpoint(
                        client = client,
                        url = url,
                        targetFile = targetFile,
                        partFile = partFile,
                        cleanSha = cleanSha,
                        approxSizeBytes = approxSizeBytes,
                        userAgent = userAgent,
                        shouldStop = shouldStop,
                        onProgress = onProgress
                    )
                    if (outcome.succeeded || outcome.stopped || outcome.fatal) {
                        return outcome
                    }
                    if (primaryErrorMsg == null && !outcome.errorMessage.isNullOrBlank()) {
                        primaryErrorMsg = outcome.errorMessage
                    }
                    lastErrorMsg = outcome.errorMessage
                } catch (ce: CancellationException) {
                    if (shouldStop()) {
                        return ResumableDownloadOutcome(
                            succeeded = false,
                            stopped = true,
                            usedUrl = url,
                            bytesDownloaded = if (partFile.exists()) partFile.length() else 0L
                        )
                    }
                    throw ce
                } catch (e: Exception) {
                    if (shouldStop()) {
                        return ResumableDownloadOutcome(
                            succeeded = false,
                            stopped = true,
                            usedUrl = url,
                            bytesDownloaded = if (partFile.exists()) partFile.length() else 0L
                        )
                    }
                    if (isInsufficientStorageError(e)) {
                        return ResumableDownloadOutcome(
                            succeeded = false,
                            fatal = true,
                            usedUrl = url,
                            bytesDownloaded = if (partFile.exists()) partFile.length() else 0L,
                            errorMessage = "Insufficient on-device storage while writing ${targetFile.name}: ${e.message ?: "disk full"}"
                        )
                    }
                    val err = e.message ?: "Download failed"
                    if (primaryErrorMsg == null) {
                        primaryErrorMsg = err
                    }
                    // Do not mask an earlier remote/stream error with a local fallback ConnectException
                    if (e !is ConnectException || lastErrorMsg == null) {
                        lastErrorMsg = err
                    }
                }
            }
            ResumableDownloadOutcome(
                succeeded = false,
                usedUrl = candidateUrls.first(),
                bytesDownloaded = if (partFile.exists()) partFile.length() else 0L,
                errorMessage = primaryErrorMsg ?: lastErrorMsg ?: "Unable to download ${targetFile.name}."
            )
        } finally {
            client.close()
        }
    }

    private suspend fun downloadFromSingleEndpoint(
        client: HttpClient,
        url: String,
        targetFile: File,
        partFile: File,
        cleanSha: String,
        approxSizeBytes: Long,
        userAgent: String,
        shouldStop: () -> Boolean,
        onProgress: suspend (String, Long, Long, Int, String, String) -> Unit
    ): ResumableDownloadOutcome {
        var bytesTransferred = if (partFile.exists()) partFile.length() else 0L
        var expectedExactTotalBytes = -1L

        for (attempt in 0..1) {
            var retryFromZero = false
            var fatalOutcome: ResumableDownloadOutcome? = null
            val existingBytes = if (partFile.exists()) partFile.length() else 0L

            client.prepareGet(url) {
                header(HttpHeaders.UserAgent, userAgent)
                if (existingBytes > 0L) {
                    header(HttpHeaders.Range, "bytes=$existingBytes-")
                }
            }.execute { response ->
                if (response.status == HttpStatusCode.RequestedRangeNotSatisfiable && existingBytes > 0L) {
                    partFile.delete()
                    retryFromZero = true
                    return@execute
                }

                if (!response.status.isSuccess()) {
                    throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
                }

                val isResuming = response.status == HttpStatusCode.PartialContent && existingBytes > 0L
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                val startOffset = if (isResuming) existingBytes else 0L
                expectedExactTotalBytes = if (contentLength > 0L) startOffset + contentLength else -1L
                val totalBytes = when {
                    expectedExactTotalBytes > 0L -> expectedExactTotalBytes
                    approxSizeBytes > 0L -> approxSizeBytes
                    else -> -1L
                }

                val bytesNeeded = if (totalBytes > 0L) (totalBytes - startOffset).coerceAtLeast(0L) else 0L
                val parentDir = partFile.parentFile
                if (bytesNeeded > 0L && parentDir != null && parentDir.exists()) {
                    val freeBytes = parentDir.usableSpace
                    if (freeBytes < bytesNeeded) {
                        fatalOutcome = ResumableDownloadOutcome(
                            succeeded = false,
                            fatal = true,
                            usedUrl = url,
                            bytesDownloaded = startOffset,
                            errorMessage = "Insufficient on-device storage: need ${bytesNeeded / (1024 * 1024)} MB more, only ${freeBytes / (1024 * 1024)} MB free."
                        )
                        return@execute
                    }
                }

                val channel = response.bodyAsChannel()
                var bytesDownloaded = startOffset
                val initialPct = if (totalBytes > 0L) {
                    ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                } else {
                    -1
                }
                val initialMb = bytesDownloaded / (1024 * 1024)
                val initialStatusText = if (totalBytes > 0L) {
                    "$initialMb MB / ${totalBytes / (1024 * 1024)} MB"
                } else {
                    "$initialMb MB downloaded"
                }
                onProgress(
                    "DOWNLOADING",
                    bytesDownloaded,
                    totalBytes.coerceAtLeast(0L),
                    initialPct,
                    url,
                    "${if (isResuming) "Resuming" else "Downloading"} ${targetFile.name} ($initialStatusText)"
                )
                var lastNotifyTime = System.currentTimeMillis()

                FileOutputStream(partFile, isResuming).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (shouldStop()) {
                            bytesTransferred = bytesDownloaded
                            return@execute
                        }

                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read > 0) {
                            fos.write(buffer, 0, read)
                            bytesDownloaded += read
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime >= NOTIFY_INTERVAL_MS) {
                            lastNotifyTime = now
                            val pct = if (totalBytes > 0L) {
                                ((bytesDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                            } else {
                                -1
                            }
                            val mbDone = bytesDownloaded / (1024 * 1024)
                            val statusText = if (totalBytes > 0L) {
                                "$mbDone MB / ${totalBytes / (1024 * 1024)} MB"
                            } else {
                                "$mbDone MB downloaded"
                            }
                            onProgress(
                                "DOWNLOADING",
                                bytesDownloaded,
                                totalBytes.coerceAtLeast(0L),
                                pct,
                                url,
                                "Downloading ${targetFile.name} ($statusText)"
                            )
                        }
                    }
                    fos.fd.sync()
                    bytesTransferred = bytesDownloaded
                }
            }

            if (fatalOutcome != null) {
                return fatalOutcome!!
            }

            if (shouldStop()) {
                return ResumableDownloadOutcome(
                    succeeded = false,
                    stopped = true,
                    usedUrl = url,
                    bytesDownloaded = if (partFile.exists()) partFile.length() else bytesTransferred
                )
            }
            if (!retryFromZero) break
        }

        val partLen = partFile.length()
        if (expectedExactTotalBytes > 0L && partLen < expectedExactTotalBytes) {
            return ResumableDownloadOutcome(
                succeeded = false,
                usedUrl = url,
                bytesDownloaded = partLen,
                errorMessage = "Incomplete download stream ($partLen / $expectedExactTotalBytes bytes); partial file retained for resume."
            )
        }

        onProgress(
            "VERIFYING",
            partLen,
            partLen,
            99,
            url,
            "Verifying GGUF integrity..."
        )

        if (!isVerifiedGgufFile(partFile, cleanSha)) {
            partFile.delete()
            return ResumableDownloadOutcome(
                succeeded = false,
                fatal = true,
                usedUrl = url,
                errorMessage = "Integrity verification failed for ${targetFile.name} (invalid GGUF header or SHA-256 mismatch)."
            )
        }

        if (targetFile.exists()) targetFile.delete()
        if (!partFile.renameTo(targetFile)) {
            return ResumableDownloadOutcome(
                succeeded = false,
                usedUrl = url,
                errorMessage = "Failed to rename verified model file to ${targetFile.name}."
            )
        }

        val finalSize = targetFile.length()
        return ResumableDownloadOutcome(
            succeeded = true,
            usedUrl = url,
            bytesDownloaded = finalSize
        )
    }
}
