package com.reveila.util.io

import com.reveila.system.io.PlatformFileSystem

/**
 * Pure Kotlin Multiplatform utility class for filesystem operations
 * powered by [PlatformFileSystem].
 */
class FileUtil private constructor() {

    companion object {

        private val fs: PlatformFileSystem get() = PlatformFileSystem()

        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun delete(path: String?, recursive: Boolean = true): Boolean {
            if (path.isNullOrBlank() || !fs.exists(path)) {
                return false
            }
            return fs.delete(path, recursive)
        }

        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun delete(file: Any?, recursive: Boolean = true): Boolean {
            if (file == null) return false
            val pathStr = file.toString()
            return delete(pathStr, recursive)
        }

        @kotlin.jvm.JvmStatic
        fun listRelativePaths(directory: String, fileExt: String?): List<String> {
            if (!fs.exists(directory) || !fs.isDirectory(directory)) {
                return emptyList()
            }
            val ext = if (fileExt.isNullOrEmpty() || fileExt == ".*") null else fileExt.removePrefix(".")
            return fs.listRelativePaths(directory, ext)
        }

        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun copyFile(sourcePath: String, targetPath: String, overwrite: Boolean = true): Long {
            if (!fs.exists(sourcePath)) {
                throw Exception("Source file does not exist: $sourcePath")
            }
            val bytes = fs.readBytes(sourcePath)
            fs.writeBytes(targetPath, bytes, append = false)
            return bytes.size.toLong()
        }

        @kotlin.jvm.JvmStatic
        fun exists(path: String): Boolean = fs.exists(path)

        @kotlin.jvm.JvmStatic
        fun isDirectory(path: String): Boolean = fs.isDirectory(path)

        @kotlin.jvm.JvmStatic
        fun createDirectories(path: String) = fs.createDirectories(path)

        @kotlin.jvm.JvmStatic
        fun readText(path: String): String = fs.readText(path)

        @kotlin.jvm.JvmStatic
        @kotlin.jvm.JvmOverloads
        fun writeText(path: String, text: String, append: Boolean = false) = fs.writeText(path, text, append)
    }
}
