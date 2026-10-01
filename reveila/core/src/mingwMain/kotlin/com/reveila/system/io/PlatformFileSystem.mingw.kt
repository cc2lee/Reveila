package com.reveila.system.io

import com.reveila.system.platform.PlatformSystem
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class MingwPlatformFileSystem : PlatformFileSystem {

    override fun readBytes(path: String): ByteArray {
        val file = fopen(path, "rb") ?: throw RuntimeException("Failed to open file: $path")
        try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file).toInt()
            fseek(file, 0, SEEK_SET)

            val bytes = ByteArray(size)
            if (size > 0) {
                bytes.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1u, size.toULong(), file)
                }
            }
            return bytes
        } finally {
            fclose(file)
        }
    }

    override fun readText(path: String, charset: String): String {
        return readBytes(path).decodeToString()
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val mode = if (append) "ab" else "wb"
        val parent = path.substringBeforeLast('\\', "").ifEmpty { path.substringBeforeLast('/', "") }
        if (parent.isNotEmpty() && !exists(parent)) {
            createDirectories(parent)
        }

        val file = fopen(path, mode) ?: throw RuntimeException("Failed to open file for write: $path")
        try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                }
            }
        } finally {
            fclose(file)
        }
    }

    override fun writeText(path: String, text: String, append: Boolean) {
        writeBytes(path, text.encodeToByteArray(), append)
    }

    override fun exists(path: String): Boolean {
        memScoped {
            val s = alloc<stat>()
            return stat(path, s.ptr) == 0
        }
    }

    override fun isDirectory(path: String): Boolean {
        memScoped {
            val s = alloc<stat>()
            if (stat(path, s.ptr) == 0) {
                return (s.st_mode.toInt() and S_IFDIR) != 0
            }
            return false
        }
    }

    override fun createDirectories(path: String) {
        val normalized = normalize(path).replace('/', '\\')
        val parts = normalized.split('\\')
        var current = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            current = if (current.isEmpty()) part else "$current\\$part"
            if (!exists(current) && !current.endsWith(':')) {
                mkdir(current)
            }
        }
    }

    override fun delete(path: String, recursive: Boolean): Boolean {
        return remove(path) == 0
    }

    override fun listRelativePaths(directory: String, extension: String?): List<String> {
        return emptyList()
    }

    override fun resolve(base: String, relative: String): String {
        val b = base.trimEnd('/', '\\')
        val r = relative.trimStart('/', '\\')
        return "$b\\$r"
    }

    override fun normalize(path: String): String {
        return path.replace('/', '\\')
    }

    override fun toSafePath(base: String, userPath: String): String {
        val resolved = resolve(base, userPath)
        if (!resolved.startsWith(normalize(base))) {
            throw RuntimeException("Path traversal attempt detected: $userPath")
        }
        return resolved
    }

    override fun getDefaultSystemHome(): String {
        val env = PlatformSystem.getEnv("REVEILA_HOME")
        if (!env.isNullOrBlank()) return env
        val userProfile = PlatformSystem.getEnv("USERPROFILE") ?: "C:\\Reveila"
        return "$userProfile\\.reveila"
    }
}

actual fun createPlatformFileSystem(): PlatformFileSystem = MingwPlatformFileSystem()
