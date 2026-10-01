package com.reveila.system.io

import java.io.File
import java.nio.charset.Charset

class AndroidPlatformFileSystem : PlatformFileSystem {

    override fun readBytes(path: String): ByteArray {
        return File(path).readBytes()
    }

    override fun readText(path: String, charset: String): String {
        return File(path).readText(Charset.forName(charset))
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val file = File(path)
        file.parentFile?.mkdirs()
        if (append) {
            file.appendBytes(bytes)
        } else {
            file.writeBytes(bytes)
        }
    }

    override fun writeText(path: String, text: String, append: Boolean) {
        writeBytes(path, text.toByteArray(Charsets.UTF_8), append)
    }

    override fun exists(path: String): Boolean {
        return File(path).exists()
    }

    override fun isDirectory(path: String): Boolean {
        return File(path).isDirectory
    }

    override fun createDirectories(path: String) {
        File(path).mkdirs()
    }

    override fun delete(path: String, recursive: Boolean): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        return if (recursive) file.deleteRecursively() else file.delete()
    }

    override fun listRelativePaths(directory: String, extension: String?): List<String> {
        val root = File(directory)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val ext = extension?.let { if (it.startsWith(".")) it else ".$it" }

        val results = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            if (ext == null || file.name.endsWith(ext)) {
                results.add(file.relativeTo(root).path.replace('\\', '/'))
            }
        }
        return results
    }

    override fun resolve(base: String, relative: String): String {
        return File(base, relative).canonicalPath
    }

    override fun normalize(path: String): String {
        return File(path).canonicalPath
    }

    override fun toSafePath(base: String, userPath: String): String {
        val baseFile = File(base).canonicalFile
        val resolved = File(baseFile, userPath).canonicalFile
        if (!resolved.path.startsWith(baseFile.path)) {
            throw SecurityException("Path traversal attempt detected: $userPath is outside $base")
        }
        return resolved.path
    }

    override fun getDefaultSystemHome(): String {
        val env = System.getenv("REVEILA_HOME")
        if (!env.isNullOrBlank()) return env
        val sysProp = System.getProperty("reveila.system.home")
        if (!sysProp.isNullOrBlank()) return sysProp
        return "/data/local/tmp/reveila"
    }
}

actual fun createPlatformFileSystem(): PlatformFileSystem = AndroidPlatformFileSystem()
