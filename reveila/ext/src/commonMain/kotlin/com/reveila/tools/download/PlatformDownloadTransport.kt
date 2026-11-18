package com.reveila.tools.download

/**
 * Outcome of a single platform-level streaming transfer attempt.
 */
data class StreamTransferResult(
    val succeeded: Boolean,
    val stopped: Boolean = false,
    val fatal: Boolean = false,
    val usedUrl: String = "",
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val error: Throwable? = null
)

/**
 * Platform-specific filesystem, storage-probe, SHA-256, and streaming HTTP transfer bridge.
 */
interface PlatformDownloadTransport {
    fun getAvailableStorageBytes(path: String): Long
    fun getFileSize(path: String): Long
    fun getLastModifiedEpochMs(path: String): Long
    fun listFiles(directory: String): List<String>
    fun moveOrReplace(sourcePath: String, destinationPath: String): Boolean
    fun computeSha256Hex(path: String): String
    fun streamToFile(
        urls: List<String>,
        tempFilePath: String,
        expectedSizeBytes: Long,
        minSafetyBufferBytes: Long,
        headers: Map<String, String>,
        userAgent: String,
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        shouldStop: () -> Boolean,
        onRegisterAbort: (() -> Unit) -> Unit = {},
        onProgress: (downloadedBytes: Long, totalBytes: Long, activeUrl: String) -> Unit
    ): StreamTransferResult
}

expect fun createPlatformDownloadTransport(): PlatformDownloadTransport
