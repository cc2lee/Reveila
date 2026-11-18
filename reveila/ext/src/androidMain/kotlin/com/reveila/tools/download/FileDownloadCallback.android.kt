package com.reveila.tools.download

import java.io.File

/**
 * Android/Java-friendly callback contract matching ModelManager.DownloadCallback:
 *   void onProgress(int percentage);
 *   void onSuccess(File modelFile);
 *   void onError(Exception e);
 */
interface FileDownloadCallback {
    fun onProgress(percentage: Int)
    fun onSuccess(modelFile: File)
    fun onError(e: Exception)
}

/**
 * Adapts [FileDownloadCallback] to the multiplatform [DownloadCallback] contract.
 */
fun FileDownloadCallback.asDownloadCallback(): DownloadCallback = object : DownloadCallback {
    override fun onProgress(percentage: Int) {
        this@asDownloadCallback.onProgress(percentage)
    }

    override fun onSuccess(filePath: String) {
        this@asDownloadCallback.onSuccess(File(filePath))
    }

    override fun onError(error: Throwable) {
        val ex = (error as? Exception) ?: RuntimeException(error.message ?: "Download failed", error)
        this@asDownloadCallback.onError(ex)
    }
}

fun ResumableDownloadManager.downloadFile(
    url: String,
    expectedSha256: String,
    destinationFile: File,
    callback: FileDownloadCallback,
    expectedSizeBytes: Long = -1L,
    tempDir: File? = null
): DownloadHandle = download(
    request = DownloadRequest(
        downloadId = destinationFile.name,
        url = url,
        destinationPath = destinationFile.absolutePath,
        tempDir = tempDir?.absolutePath.orEmpty(),
        expectedSha256 = expectedSha256,
        expectedSizeBytes = expectedSizeBytes
    ),
    callback = callback.asDownloadCallback()
)
