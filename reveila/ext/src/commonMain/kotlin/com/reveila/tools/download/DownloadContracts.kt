package com.reveila.tools.download

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Lifecycle states for a managed resumable download.
 */
enum class DownloadStatus {
    IDLE,
    QUEUED,
    CHECKING_STORAGE,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    CANCELLED,
    FAILED
}

/**
 * Exception raised when available device storage is insufficient for a download.
 */
class InsufficientStorageException(
    message: String,
    val requiredBytes: Long,
    val availableBytes: Long
) : RuntimeException(message)

/**
 * Parameters describing a resumable file download.
 */
data class DownloadRequest(
    val downloadId: String = "",
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val destinationPath: String,
    val tempDir: String = "",
    val expectedSha256: String = "",
    val expectedSizeBytes: Long = -1L,
    val minSafetyBufferBytes: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String = "Reveila-DownloadManager/1.0",
    val connectTimeoutSeconds: Long = 30L,
    val readTimeoutSeconds: Long = 30L
) {
    fun effectiveId(): String {
        val raw = downloadId.trim().ifEmpty {
            destinationPath.replace('\\', '/').substringAfterLast('/').ifEmpty { url }
        }
        return raw.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}

/**
 * Immutable progress snapshot emitted during a download lifecycle.
 */
data class DownloadProgress(
    val downloadId: String,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val percentage: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val activeUrl: String = "",
    val tempFilePath: String = "",
    val destinationPath: String = "",
    val message: String = ""
)

/**
 * Persisted sidecar metadata for a `.part` file so paused/interrupted downloads
 * can be resumed or cleaned up across app restarts.
 */
@Serializable
data class DownloadMetadata(
    val downloadId: String,
    val url: String,
    val fallbackUrls: List<String> = emptyList(),
    val destinationPath: String,
    val tempFilePath: String,
    val expectedSha256: String = "",
    val expectedSizeBytes: Long = -1L,
    val updatedAtEpochMs: Long = 0L
)

/**
 * Generic multiplatform callback contract compatible with ModelManager.DownloadCallback.
 */
interface DownloadCallback {
    fun onProgress(percentage: Int)
    fun onProgressDetailed(progress: DownloadProgress) {}
    fun onSuccess(filePath: String)
    fun onError(error: Throwable)
}

/**
 * Control handle for observing and controlling a single download.
 */
interface DownloadHandle {
    val downloadId: String
    val state: StateFlow<DownloadProgress>
    fun pause(): Boolean
    fun resume(callback: DownloadCallback? = null): Boolean
    fun cancel(): Boolean
    fun getStatus(): DownloadStatus
}

/**
 * Summary of a temp directory housekeeping pass.
 */
data class TempCleanupReport(
    val scannedFiles: Int = 0,
    val deletedFiles: Int = 0,
    val reclaimedBytes: Long = 0L,
    val skippedActiveFiles: Int = 0
)

/**
 * Generic resumable download manager contract for all Reveila applications.
 */
interface DownloadManager {
    fun checkAvailableStorage(targetPath: String): Long

    fun hasEnoughStorage(
        requiredBytes: Long,
        destinationPath: String,
        tempDir: String? = null,
        safetyBufferBytes: Long = 0L
    ): Boolean

    fun download(request: DownloadRequest, callback: DownloadCallback? = null): DownloadHandle

    fun pause(downloadId: String): Boolean

    fun resume(downloadId: String, callback: DownloadCallback? = null): DownloadHandle?

    fun cancel(downloadId: String): Boolean

    fun getProgress(downloadId: String): DownloadProgress?

    fun listIncompleteDownloads(): List<DownloadMetadata>

    fun cleanupTempDirectory(maxAgeMillis: Long = ResumableDownloadManager.DEFAULT_TEMP_TTL_MS): TempCleanupReport
}
