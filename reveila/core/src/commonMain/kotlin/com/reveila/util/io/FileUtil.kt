package com.reveila.util.io

import com.reveila.system.io.PlatformFileSystem

/**
 * Pure Kotlin Multiplatform utility class for filesystem operations
 * powered by [PlatformFileSystem].
 */
class FileUtil private constructor() {

    companion object {

        private val fs: PlatformFileSystem get() = PlatformFileSystem()

        fun delete(path: String?, recursive: Boolean = true): Boolean {
            if (path.isNullOrBlank() || !fs.exists(path)) {
                return false
            }
            return fs.delete(path, recursive)
        }

        fun listRelativePaths(directory: String, fileExt: String?): List<String> {
            if (!fs.exists(directory) || !fs.isDirectory(directory)) {
                return emptyList()
            }
            val ext = if (fileExt.isNullOrEmpty() || fileExt == ".*") null else fileExt.removePrefix(".")
            return fs.listRelativePaths(directory, ext)
        }

        fun copyFile(sourcePath: String, targetPath: String, overwrite: Boolean = true): Long {
            if (!fs.exists(sourcePath)) {
                throw Exception("Source file does not exist: $sourcePath")
            }
            val bytes = fs.readBytes(sourcePath)
            fs.writeBytes(targetPath, bytes, append = false)
            return bytes.size.toLong()
        }

        fun exists(path: String): Boolean = fs.exists(path)

        fun isDirectory(path: String): Boolean = fs.isDirectory(path)

        fun createDirectories(path: String) = fs.createDirectories(path)

        fun readText(path: String): String = fs.readText(path)

        fun writeText(path: String, text: String, append: Boolean = false) = fs.writeText(path, text, append)
    }
}
