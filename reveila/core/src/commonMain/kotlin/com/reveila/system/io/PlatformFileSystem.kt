package com.reveila.system.io

/**
 * Multiplatform abstraction for local filesystem I/O operations.
 * Allows the Reveila engine to execute offline and locally across Android, iOS, macOS, Windows, and Linux.
 */
interface PlatformFileSystem {
    fun readBytes(path: String): ByteArray
    fun readText(path: String, charset: String = "UTF-8"): String
    fun writeBytes(path: String, bytes: ByteArray, append: Boolean = false)
    fun writeText(path: String, text: String, append: Boolean = false)
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun createDirectories(path: String)
    fun delete(path: String, recursive: Boolean = false): Boolean
    fun listRelativePaths(directory: String, extension: String? = null): List<String>
    fun resolve(base: String, relative: String): String
    fun normalize(path: String): String
    fun toSafePath(base: String, userPath: String): String
    fun getDefaultSystemHome(): String

    companion object {
        operator fun invoke(): PlatformFileSystem = createPlatformFileSystem()
    }
}

expect fun createPlatformFileSystem(): PlatformFileSystem
