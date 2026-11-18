package com.reveila.tools.download

import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class AndroidPlatformDownloadTransport : PlatformDownloadTransport {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val MAX_REDIRECTS = 7
    }

    override fun getAvailableStorageBytes(path: String): Long {
        return try {
            var cursor: File? = File(path).absoluteFile
            while (cursor != null && !cursor.exists()) {
                cursor = cursor.parentFile
            }
            val existing = cursor ?: return -1L
            try {
                StatFs(existing.absolutePath).availableBytes
            } catch (_: Exception) {
                existing.usableSpace
            }
        } catch (_: Exception) {
            -1L
        }
    }

    override fun getFileSize(path: String): Long {
        val f = File(path)
        return if (f.exists() && f.isFile) f.length() else -1L
    }

    override fun getLastModifiedEpochMs(path: String): Long {
        val f = File(path)
        return if (f.exists()) f.lastModified() else 0L
    }

    override fun listFiles(directory: String): List<String> {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.map { it.absolutePath }.orEmpty()
    }

    override fun moveOrReplace(sourcePath: String, destinationPath: String): Boolean {
        val src = File(sourcePath)
        val dst = File(destinationPath)
        if (!src.exists()) return false
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.delete()
        if (src.renameTo(dst)) return true
        return try {
            FileInputStream(src).use { input ->
                FileOutputStream(dst, false).use { output ->
                    val buf = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                    }
                    output.fd.sync()
                }
            }
            src.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun computeSha256Hex(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(256 * 1024)
        FileInputStream(path).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun streamToFile(
        urls: List<String>,
        tempFilePath: String,
        expectedSizeBytes: Long,
        minSafetyBufferBytes: Long,
        headers: Map<String, String>,
        userAgent: String,
        connectTimeoutSeconds: Long,
        readTimeoutSeconds: Long,
        shouldStop: () -> Boolean,
        onRegisterAbort: (() -> Unit) -> Unit,
        onProgress: (downloadedBytes: Long, totalBytes: Long, activeUrl: String) -> Unit
    ): StreamTransferResult {
        val tempFile = File(tempFilePath)
        tempFile.parentFile?.mkdirs()

        var primaryError: Throwable? = null
        var lastError: Throwable? = null

        for (url in urls) {
            if (shouldStop()) {
                return StreamTransferResult(
                    succeeded = false,
                    stopped = true,
                    usedUrl = url,
                    bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L
                )
            }
            try {
                val res = transferSingleUrl(
                    url = url,
                    tempFile = tempFile,
                    expectedSizeBytes = expectedSizeBytes,
                    minSafetyBufferBytes = minSafetyBufferBytes,
                    headers = headers,
                    userAgent = userAgent,
                    connectTimeoutMs = (connectTimeoutSeconds.coerceAtLeast(1L) * 1000L).toInt(),
                    readTimeoutMs = (readTimeoutSeconds.coerceAtLeast(1L) * 1000L).toInt(),
                    shouldStop = shouldStop,
                    onRegisterAbort = onRegisterAbort,
                    onProgress = onProgress
                )
                if (res.succeeded || res.stopped || res.fatal) {
                    return res
                }
                if (primaryError == null && res.error != null) primaryError = res.error
                lastError = res.error
            } catch (e: Exception) {
                if (shouldStop()) {
                    return StreamTransferResult(
                        succeeded = false,
                        stopped = true,
                        usedUrl = url,
                        bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L
                    )
                }
                if (isInsufficientStorageError(e)) {
                    return StreamTransferResult(
                        succeeded = false,
                        fatal = true,
                        usedUrl = url,
                        bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L,
                        error = e
                    )
                }
                if (primaryError == null) primaryError = e
                if (e !is ConnectException || lastError == null) lastError = e
            }
        }

        return StreamTransferResult(
            succeeded = false,
            usedUrl = urls.firstOrNull().orEmpty(),
            bytesDownloaded = if (tempFile.exists()) tempFile.length() else 0L,
            error = primaryError ?: lastError ?: IOException("Unable to download from candidate URLs.")
        )
    }

    private fun transferSingleUrl(
        url: String,
        tempFile: File,
        expectedSizeBytes: Long,
        minSafetyBufferBytes: Long,
        headers: Map<String, String>,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        shouldStop: () -> Boolean,
        onRegisterAbort: (() -> Unit) -> Unit,
        onProgress: (Long, Long, String) -> Unit
    ): StreamTransferResult {
        var bytesTransferred = if (tempFile.exists()) tempFile.length() else 0L
        var exactTotalBytes = -1L

        for (attempt in 0..1) {
            var retryFromZero = false
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            val conn = openWithRedirects(
                initialUrl = url,
                existingBytes = existingBytes,
                headers = headers,
                userAgent = userAgent,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
            onRegisterAbort {
                try {
                    conn.disconnect()
                } catch (_: Exception) {
                }
            }

            try {
                val code = conn.responseCode
                if (code == 416 && existingBytes > 0L) {
                    tempFile.delete()
                    retryFromZero = true
                    continue
                }
                if (code !in 200..299) {
                    throw IOException("HTTP $code: ${conn.responseMessage ?: "Request failed"}")
                }

                val isResuming = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
                val contentLength = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                val startOffset = if (isResuming) existingBytes else 0L
                exactTotalBytes = if (contentLength > 0L) startOffset + contentLength else -1L
                val reportedTotal = when {
                    exactTotalBytes > 0L -> exactTotalBytes
                    expectedSizeBytes > 0L -> expectedSizeBytes
                    else -> -1L
                }

                if (reportedTotal > 0L) {
                    val remaining = (reportedTotal - startOffset).coerceAtLeast(0L) + minSafetyBufferBytes.coerceAtLeast(0L)
                    val freeBytes = getAvailableStorageBytes(tempFile.absolutePath)
                    if (freeBytes in 0L until remaining) {
                        return StreamTransferResult(
                            succeeded = false,
                            fatal = true,
                            usedUrl = url,
                            bytesDownloaded = startOffset,
                            totalBytes = reportedTotal,
                            error = InsufficientStorageException(
                                message = "Insufficient storage: need $remaining more bytes, only $freeBytes available.",
                                requiredBytes = remaining,
                                availableBytes = freeBytes
                            )
                        )
                    }
                }

                var bytesDownloaded = startOffset
                onProgress(bytesDownloaded, reportedTotal, url)
                var lastNotify = System.currentTimeMillis()

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile, isResuming).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            if (shouldStop()) {
                                fos.fd.sync()
                                bytesTransferred = bytesDownloaded
                                return StreamTransferResult(
                                    succeeded = false,
                                    stopped = true,
                                    usedUrl = url,
                                    bytesDownloaded = bytesTransferred,
                                    totalBytes = reportedTotal
                                )
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                fos.write(buffer, 0, read)
                                bytesDownloaded += read
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastNotify >= NOTIFY_INTERVAL_MS) {
                                lastNotify = now
                                onProgress(bytesDownloaded, reportedTotal, url)
                            }
                        }
                        fos.fd.sync()
                        bytesTransferred = bytesDownloaded
                        onProgress(bytesDownloaded, reportedTotal, url)
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (shouldStop()) {
                return StreamTransferResult(
                    succeeded = false,
                    stopped = true,
                    usedUrl = url,
                    bytesDownloaded = if (tempFile.exists()) tempFile.length() else bytesTransferred,
                    totalBytes = exactTotalBytes
                )
            }
            if (!retryFromZero) break
        }

        val finalPartLen = if (tempFile.exists()) tempFile.length() else bytesTransferred
        if (exactTotalBytes > 0L && finalPartLen < exactTotalBytes) {
            return StreamTransferResult(
                succeeded = false,
                usedUrl = url,
                bytesDownloaded = finalPartLen,
                totalBytes = exactTotalBytes,
                error = IOException("Incomplete download stream ($finalPartLen / $exactTotalBytes bytes).")
            )
        }

        return StreamTransferResult(
            succeeded = true,
            usedUrl = url,
            bytesDownloaded = finalPartLen,
            totalBytes = if (exactTotalBytes > 0L) exactTotalBytes else finalPartLen
        )
    }

    private fun openWithRedirects(
        initialUrl: String,
        existingBytes: Long,
        headers: Map<String, String>,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = initialUrl
        for (hop in 0..MAX_REDIRECTS) {
            val conn = (URI(currentUrl).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    throw IOException("HTTP $code redirect without Location header from $currentUrl")
                }
                currentUrl = URI(currentUrl).resolve(loc).toString()
                continue
            }
            return conn
        }
        throw IOException("Too many redirects (> $MAX_REDIRECTS) downloading $initialUrl")
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
}

actual fun createPlatformDownloadTransport(): PlatformDownloadTransport = AndroidPlatformDownloadTransport()
