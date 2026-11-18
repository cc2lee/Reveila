package com.reveila.tools.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.*
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.DISPATCH_TIME_FOREVER

@OptIn(ExperimentalForeignApi::class)
class ApplePlatformDownloadTransport : PlatformDownloadTransport {

    private val fm = NSFileManager.defaultManager

    override fun getAvailableStorageBytes(path: String): Long {
        return try {
            var cursor = path
            while (cursor.isNotEmpty() && !fm.fileExistsAtPath(cursor)) {
                val parent = (cursor as NSString).stringByDeletingLastPathComponent
                if (parent == cursor) break
                cursor = parent
            }
            val attrs = fm.attributesOfFileSystemForPath(cursor, error = null) ?: return -1L
            (attrs[NSFileSystemFreeSize] as? NSNumber)?.longLongValue ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun getFileSize(path: String): Long {
        if (!fm.fileExistsAtPath(path)) return -1L
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: -1L
    }

    override fun getLastModifiedEpochMs(path: String): Long {
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return 0L
        val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000.0).toLong()
    }

    override fun listFiles(directory: String): List<String> {
        val items = fm.contentsOfDirectoryAtPath(directory, error = null) ?: return emptyList()
        val out = mutableListOf<String>()
        for (item in items) {
            val name = item as? String ?: continue
            val full = (directory as NSString).stringByAppendingPathComponent(name)
            memScoped {
                val isDir = alloc<kotlinx.cinterop.BooleanVar>()
                if (fm.fileExistsAtPath(full, isDirectory = isDir.ptr) && !isDir.value) {
                    out.add(full)
                }
            }
        }
        return out
    }

    override fun moveOrReplace(sourcePath: String, destinationPath: String): Boolean {
        if (!fm.fileExistsAtPath(sourcePath)) return false
        val parent = (destinationPath as NSString).stringByDeletingLastPathComponent
        if (parent.isNotEmpty() && !fm.fileExistsAtPath(parent)) {
            fm.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        }
        if (fm.fileExistsAtPath(destinationPath)) {
            fm.removeItemAtPath(destinationPath, error = null)
        }
        if (fm.moveItemAtPath(sourcePath, toPath = destinationPath, error = null)) {
            return true
        }
        if (fm.copyItemAtPath(sourcePath, toPath = destinationPath, error = null)) {
            fm.removeItemAtPath(sourcePath, error = null)
            return true
        }
        return false
    }

    override fun computeSha256Hex(path: String): String = NativeSha256.computeFileSha256Hex(path)

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
        val parent = (tempFilePath as NSString).stringByDeletingLastPathComponent
        if (parent.isNotEmpty() && !fm.fileExistsAtPath(parent)) {
            fm.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        }

        var lastError: Throwable? = null
        for (url in urls) {
            if (shouldStop()) {
                return StreamTransferResult(
                    succeeded = false,
                    stopped = true,
                    usedUrl = url,
                    bytesDownloaded = getFileSize(tempFilePath).coerceAtLeast(0L)
                )
            }
            val existing = getFileSize(tempFilePath).coerceAtLeast(0L)
            val nsUrl = NSURL.URLWithString(url) ?: continue
            val req = NSMutableURLRequest.requestWithURL(nsUrl).apply {
                setTimeoutInterval(readTimeoutSeconds.toDouble())
                setValue(userAgent, forHTTPHeaderField = "User-Agent")
                if (existing > 0L) {
                    setValue("bytes=$existing-", forHTTPHeaderField = "Range")
                }
                headers.forEach { (k, v) -> setValue(v, forHTTPHeaderField = k) }
            }

            var dataResult: NSData? = null
            var httpResp: NSHTTPURLResponse? = null
            var nsErr: NSError? = null
            val sem = dispatch_semaphore_create(0)
            val task = NSURLSession.sharedSession.dataTaskWithRequest(req) { data, response, error ->
                dataResult = data
                httpResp = response as? NSHTTPURLResponse
                nsErr = error
                dispatch_semaphore_signal(sem)
            }
            task.resume()
            dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)

            if (shouldStop()) {
                return StreamTransferResult(
                    succeeded = false,
                    stopped = true,
                    usedUrl = url,
                    bytesDownloaded = getFileSize(tempFilePath).coerceAtLeast(0L)
                )
            }

            val err = nsErr
            val resp = httpResp
            val data = dataResult
            if (err != null || resp == null || data == null) {
                lastError = RuntimeException(err?.localizedDescription ?: "Apple URLSession request failed for $url")
                continue
            }

            val status = resp.statusCode.toInt()
            if (status !in 200..299) {
                lastError = RuntimeException("HTTP $status downloading $url")
                continue
            }

            val isResuming = status == 206 && existing > 0L
            if (isResuming) {
                val handle = NSFileHandle.fileHandleForWritingAtPath(tempFilePath)
                if (handle != null) {
                    handle.seekToEndOfFile()
                    handle.writeData(data)
                    handle.closeFile()
                } else {
                    data.writeToFile(tempFilePath, atomically = true)
                }
            } else {
                data.writeToFile(tempFilePath, atomically = true)
            }

            val finalSize = getFileSize(tempFilePath).coerceAtLeast(0L)
            onProgress(finalSize, finalSize, url)
            return StreamTransferResult(
                succeeded = true,
                usedUrl = url,
                bytesDownloaded = finalSize,
                totalBytes = finalSize
            )
        }

        return StreamTransferResult(
            succeeded = false,
            usedUrl = urls.firstOrNull().orEmpty(),
            bytesDownloaded = getFileSize(tempFilePath).coerceAtLeast(0L),
            error = lastError ?: RuntimeException("Download failed on Apple target.")
        )
    }
}

actual fun createPlatformDownloadTransport(): PlatformDownloadTransport = ApplePlatformDownloadTransport()
