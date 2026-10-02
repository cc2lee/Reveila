package com.reveila.util.io

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.LinkedList
import java.util.stream.Stream

/**
 * This utility class provides convenience methods for file system operations.
 */
class FileUtil private constructor() {

    interface DownloadCallback {
        fun onComplete(modelFile: File)
        fun onError(e: Exception)
        fun onProgress(progress: Int)
    }

    companion object {

        /**
         * Deletes a file or directory. If the file is a directory, it will be deleted recursively.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun delete(file: File?, throwException: Boolean) {
            if (file == null || !file.exists()) {
                return
            }

            val failedFiles = LinkedList<File>()
            deleteRecursively(file, failedFiles)

            if (throwException && failedFiles.isNotEmpty()) {
                throw IOException("Failed to delete: $failedFiles")
            }
        }

        private fun deleteRecursively(file: File, failedFiles: MutableList<File>) {
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteRecursively(child, failedFiles)
                    }
                }
            }

            try {
                Files.deleteIfExists(file.toPath())
            } catch (e: IOException) {
                failedFiles.add(file)
            }
        }

        /**
         * Lists all files in a given directory that end with a specific extension.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun listRelativePaths(directory: String, fileExt: String?): Array<String> {
            val root = Paths.get(directory)

            val extension = if (fileExt.isNullOrEmpty()) {
                ".*"
            } else {
                if (fileExt.startsWith(".")) fileExt else ".$fileExt"
            }

            Files.walk(root).use { stream ->
                return stream
                    .filter { Files.isRegularFile(it) }
                    .filter { path -> extension == ".*" || path.toString().lowercase().endsWith(extension) }
                    .map { path -> root.relativize(path).toString() }
                    .toArray { size -> arrayOfNulls<String>(size) }
                    .filterNotNull()
                    .toTypedArray()
            }
        }

        /**
         * Copies a source file to a target file or directory.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyFile(source: File?, target: File?, overwrite: Boolean): Long {
            if (source == null) {
                throw IOException("Source file specified is null")
            }
            if (!source.exists()) {
                throw IOException("Source file specified could not be found: ${source.absolutePath}")
            }
            if (!source.isFile) {
                throw IOException("Source file specified does not denote a normal file: ${source.absolutePath}")
            }
            if (target == null) {
                throw IOException("Destination file specified is null")
            }

            val sourcePath = source.toPath()
            val targetPath = target.toPath()

            val finalTargetPath = if (Files.isDirectory(targetPath)) targetPath.resolve(source.name) else targetPath

            val parentDir = finalTargetPath.parent
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir)
            }

            if (overwrite) {
                Files.copy(sourcePath, finalTargetPath, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.copy(sourcePath, finalTargetPath)
            }
            return Files.size(finalTargetPath)
        }

        /**
         * Copies a source directory and its contents to a target directory.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun copyDir(source: File?, target: File?, overwrite: Boolean): Long {
            if (source == null) {
                throw IOException("Source directory specified is null")
            }
            if (!source.exists()) {
                throw IOException("Source directory does not exist: ${source.absolutePath}")
            }
            if (!source.isDirectory) {
                throw IOException("Source is not a directory: ${source.absolutePath}")
            }
            if (target == null) {
                throw IOException("Target directory specified is null")
            }
            if (target.exists() && !target.isDirectory) {
                throw IOException("Target exists and is not a directory: ${target.absolutePath}")
            }

            if (!target.exists()) {
                if (!target.mkdirs()) {
                    throw IOException("Could not create target directory: ${target.absolutePath}")
                }
            }

            val srcFiles = source.listFiles() ?: throw IOException("Could not list files in source directory: ${source.absolutePath}")

            var bytes: Long = 0
            for (srcFile in srcFiles) {
                val destFile = File(target, srcFile.name)
                bytes += if (srcFile.isDirectory) {
                    copyDir(srcFile, destFile, overwrite)
                } else {
                    copyFile(srcFile, destFile, overwrite)
                }
            }
            return bytes
        }

        /**
         * Gets the name of a file from a full path, without the extension.
         */
        @JvmStatic
        fun getFilenameWithoutExtension(filename: String?): String? {
            if (filename.isNullOrEmpty()) {
                return filename
            }
            val name = File(filename).name
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex <= 0) name else name.substring(0, dotIndex)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun toSafePath(base: Path?, path: String?): Path {
            if (base == null || path == null) {
                throw IOException("Base path and target path must not be null")
            }
            val resolvedPath = base.resolve(path).normalize()
            if (!resolvedPath.startsWith(base)) {
                throw IOException("Path traversal attempt detected")
            }
            return resolvedPath
        }

        @JvmStatic
        fun toJavaFilePath(path: String): String {
            return path.replace('\\', '/')
        }

        private fun prepareDownloadFile(saveAsFile: File?, overwrite: Boolean, callback: DownloadCallback?) {
            if (saveAsFile == null) {
                val e = IllegalArgumentException("Destination file must be provided.")
                if (callback != null) callback.onError(e) else throw e
            } else if (saveAsFile.parentFile != null && !saveAsFile.parentFile.exists() && !saveAsFile.parentFile.mkdirs()) {
                val e = IOException("Could not create parent directories for: ${saveAsFile.absolutePath}")
                if (callback != null) callback.onError(e) else throw e
            } else if (saveAsFile.exists() && !overwrite) {
                val e = IOException("Destination file already exists and overwrite is false: ${saveAsFile.absolutePath}")
                if (callback != null) callback.onError(e) else throw e
            }
        }

        @Throws(IOException::class)
        private fun openUrlConnection(
            sourceUrl: URL?,
            saveAsFile: File?,
            overwrite: Boolean,
            callback: DownloadCallback?
        ): URLConnection? {
            prepareDownloadFile(saveAsFile, overwrite, callback)
            if (sourceUrl == null) {
                val e = IllegalArgumentException("Source URL must be provided.")
                if (callback != null) callback.onError(e) else throw e
                return null
            }

            val connection = sourceUrl.openConnection()
                ?: run {
                    val e = IOException("Could not open connection to URL: $sourceUrl")
                    if (callback != null) callback.onError(e) else throw e
                    return null
                }

            connection.connectTimeout = 15000 // 15 seconds
            connection.readTimeout = 60000 // 60 seconds
            connection.connect()
            return connection
        }

        @Throws(IOException::class)
        private fun writeToFile(
            connection: URLConnection,
            saveAsFile: File,
            fileLength: Int,
            callback: DownloadCallback?
        ) {
            BufferedInputStream(connection.getInputStream()).use { input ->
                BufferedOutputStream(FileOutputStream(saveAsFile)).use { output ->
                    val buffer = ByteArray(8192)
                    var total: Long = 0
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        total += bytesRead
                        callback?.onProgress((total * 100 / fileLength).toInt())
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                    callback?.onComplete(saveAsFile)
                }
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun download(
            sourceUrl: URL?,
            saveAsFile: File?,
            overwrite: Boolean,
            callback: DownloadCallback?
        ) {
            try {
                val connection = openUrlConnection(sourceUrl, saveAsFile, overwrite, callback)
                if (connection != null) {
                    val fileLength = connection.contentLength
                    if (fileLength <= 0) {
                        callback?.onError(IOException("Source file length is 0 bytes, or unknown."))
                        return
                    }
                    writeToFile(connection, saveAsFile!!, fileLength, callback)
                }
            } catch (e: IOException) {
                if (callback != null) {
                    callback.onError(e)
                } else {
                    throw e
                }
            }
        }
    }
}
