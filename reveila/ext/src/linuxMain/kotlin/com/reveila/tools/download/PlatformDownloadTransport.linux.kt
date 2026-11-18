package com.reveila.tools.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.DIR
import platform.posix.S_IFREG
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove
import platform.posix.rename
import platform.posix.stat
import platform.posix.statvfs

@OptIn(ExperimentalForeignApi::class)
class LinuxPlatformDownloadTransport : PlatformDownloadTransport {

    override fun getAvailableStorageBytes(path: String): Long {
        return try {
            memScoped {
                var cursor = path
                while (cursor.isNotEmpty()) {
                    val s = alloc<stat>()
                    if (stat(cursor, s.ptr) == 0) break
                    val idx = cursor.lastIndexOf('/')
                    if (idx <= 0) {
                        cursor = "/"
                        break
                    }
                    cursor = cursor.substring(0, idx)
                }
                val vfs = alloc<statvfs>()
                if (statvfs(cursor, vfs.ptr) == 0) {
                    vfs.f_bavail.toLong() * vfs.f_frsize.toLong()
                } else {
                    -1L
                }
            }
        } catch (_: Exception) {
            -1L
        }
    }

    override fun getFileSize(path: String): Long {
        memScoped {
            val s = alloc<stat>()
            return if (stat(path, s.ptr) == 0) s.st_size else -1L
        }
    }

    override fun getLastModifiedEpochMs(path: String): Long {
        memScoped {
            val s = alloc<stat>()
            return if (stat(path, s.ptr) == 0) s.st_mtim.tv_sec * 1000L else 0L
        }
    }

    override fun listFiles(directory: String): List<String> {
        val dir: kotlinx.cinterop.CPointer<DIR> = opendir(directory) ?: return emptyList()
        val out = mutableListOf<String>()
        try {
            while (true) {
                val entry = readdir(dir) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                val sep = if (directory.endsWith('/')) "" else "/"
                val full = "$directory$sep$name"
                memScoped {
                    val s = alloc<stat>()
                    if (stat(full, s.ptr) == 0 && (s.st_mode.toInt() and S_IFREG) != 0) {
                        out.add(full)
                    }
                }
            }
        } finally {
            closedir(dir)
        }
        return out
    }

    override fun moveOrReplace(sourcePath: String, destinationPath: String): Boolean {
        remove(destinationPath)
        if (rename(sourcePath, destinationPath) == 0) return true
        val src = fopen(sourcePath, "rb") ?: return false
        val dst = fopen(destinationPath, "wb") ?: run {
            fclose(src)
            return false
        }
        val buf = ByteArray(64 * 1024)
        return try {
            buf.usePinned { pinned ->
                while (true) {
                    val read = fread(pinned.addressOf(0), 1u, buf.size.toULong(), src).toInt()
                    if (read <= 0) break
                    fwrite(pinned.addressOf(0), 1u, read.toULong(), dst)
                }
            }
            true
        } finally {
            fclose(dst)
            fclose(src)
            remove(sourcePath)
        }
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
        return StreamTransferResult(
            succeeded = false,
            fatal = true,
            usedUrl = urls.firstOrNull().orEmpty(),
            error = UnsupportedOperationException("Streaming HTTP transport is not configured for linuxX64 native target.")
        )
    }
}

actual fun createPlatformDownloadTransport(): PlatformDownloadTransport = LinuxPlatformDownloadTransport()
