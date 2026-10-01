package com.reveila.system.io

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformFileSystem actual constructor() {

    private val fileManager = NSFileManager.defaultManager

    actual fun readBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path)
            ?: throw RuntimeException("Failed to read file at path: $path")
        val size = data.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return bytes
    }

    actual fun readText(path: String, charset: String): String {
        val nsStr = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
            ?: throw RuntimeException("Failed to read text from: $path")
        return nsStr.toString()
    }

    actual fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val parent = (path as NSString).stringByDeletingLastPathComponent
        if (parent.isNotEmpty() && !fileManager.fileExistsAtPath(parent)) {
            createDirectories(parent)
        }

        if (append && fileManager.fileExistsAtPath(path)) {
            val handle = NSFileHandle.fileHandleForWritingAtPath(path)
            if (handle != null) {
                handle.seekToEndOfFile()
                bytes.usePinned { pinned ->
                    val data = NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
                    handle.writeData(data)
                }
                handle.closeFile()
                return
            }
        }

        bytes.usePinned { pinned ->
            val data = NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            data.writeToFile(path, atomically = true)
        }
    }

    actual fun writeText(path: String, text: String, append: Boolean) {
        writeBytes(path, text.encodeToByteArray(), append)
    }

    actual fun exists(path: String): Boolean {
        return fileManager.fileExistsAtPath(path)
    }

    actual fun isDirectory(path: String): Boolean {
        memScoped {
            val isDir = alloc<BooleanVar>()
            val exists = fileManager.fileExistsAtPath(path, isDirectory = isDir.ptr)
            return exists && isDir.value
        }
    }

    actual fun createDirectories(path: String) {
        fileManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    actual fun delete(path: String, recursive: Boolean): Boolean {
        if (!exists(path)) return true
        return fileManager.removeItemAtPath(path, null)
    }

    actual fun listRelativePaths(directory: String, extension: String?): List<String> {
        if (!exists(directory) || !isDirectory(directory)) return emptyList()
        val ext = extension?.let { if (it.startsWith(".")) it else ".$it" }

        val subpaths = fileManager.subpathsAtPath(directory) ?: return emptyList()
        val results = mutableListOf<String>()

        for (i in 0 until subpaths.count.toInt()) {
            val relative = subpaths.objectAtIndex(i.toULong()) as? String ?: continue
            val fullPath = (directory as NSString).stringByAppendingPathComponent(relative)
            if (!isDirectory(fullPath)) {
                if (ext == null || relative.endsWith(ext)) {
                    results.add(relative)
                }
            }
        }
        return results
    }

    actual fun resolve(base: String, relative: String): String {
        return (base as NSString).stringByAppendingPathComponent(relative).let { normalize(it) }
    }

    actual fun normalize(path: String): String {
        return (path as NSString).stringByStandardizingPath
    }

    actual fun toSafePath(base: String, userPath: String): String {
        val safeBase = normalize(base)
        val resolved = resolve(safeBase, userPath)
        if (!resolved.startsWith(safeBase)) {
            throw SecurityException("Path traversal attempt detected: $userPath is outside $base")
        }
        return resolved
    }

    actual fun getDefaultSystemHome(): String {
        val env = NSProcessInfo.processInfo.environment["REVEILA_HOME"] as? String
        if (!env.isNullOrBlank()) return env

        val home = NSHomeDirectory()
        return (home as NSString).stringByAppendingPathComponent(".reveila")
    }
}

