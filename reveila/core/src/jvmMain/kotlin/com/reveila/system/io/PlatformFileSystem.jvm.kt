package com.reveila.system.io

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.stream.Stream
import kotlin.io.path.isRegularFile

class JvmPlatformFileSystem : PlatformFileSystem {

    override fun readBytes(path: String): ByteArray {
        val p = Paths.get(path)
        return Files.readAllBytes(p)
    }

    override fun readText(path: String, charset: String): String {
        val p = Paths.get(path)
        return Files.readString(p, Charset.forName(charset))
    }

    override fun writeBytes(path: String, bytes: ByteArray, append: Boolean) {
        val p = Paths.get(path)
        val parent = p.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        val options = if (append) {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } else {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }
        Files.write(p, bytes, *options)
    }

    override fun writeText(path: String, text: String, append: Boolean) {
        writeBytes(path, text.toByteArray(Charsets.UTF_8), append)
    }

    override fun exists(path: String): Boolean {
        return Files.exists(Paths.get(path))
    }

    override fun isDirectory(path: String): Boolean {
        return Files.isDirectory(Paths.get(path))
    }

    override fun createDirectories(path: String) {
        Files.createDirectories(Paths.get(path))
    }

    override fun delete(path: String, recursive: Boolean): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        return if (recursive) file.deleteRecursively() else file.delete()
    }

    override fun listRelativePaths(directory: String, extension: String?): List<String> {
        val root = Paths.get(directory)
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return emptyList()
        }
        val ext = extension?.let { if (it.startsWith(".")) it else ".$it" }
        return Files.walk(root).use { stream ->
            stream.filter { it.isRegularFile() }
                .filter { p -> ext == null || p.fileName.toString().endsWith(ext) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toList()
        }
    }

    override fun resolve(base: String, relative: String): String {
        return Paths.get(base).resolve(relative).normalize().toString()
    }

    override fun normalize(path: String): String {
        return Paths.get(path).normalize().toString()
    }

    override fun toSafePath(base: String, userPath: String): String {
        val basePath = Paths.get(base).toAbsolutePath().normalize()
        val resolved = basePath.resolve(userPath).normalize()
        if (!resolved.startsWith(basePath)) {
            throw SecurityException("Path traversal attempt detected: $userPath is outside $base")
        }
        return resolved.toString()
    }

    override fun getDefaultSystemHome(): String {
        val env = System.getenv("REVEILA_HOME")
        if (!env.isNullOrBlank()) return env
        val sysProp = System.getProperty("reveila.system.home")
        if (!sysProp.isNullOrBlank()) return sysProp
        return Paths.get(System.getProperty("user.home"), ".reveila").toString()
    }
}

actual fun createPlatformFileSystem(): PlatformFileSystem = JvmPlatformFileSystem()
