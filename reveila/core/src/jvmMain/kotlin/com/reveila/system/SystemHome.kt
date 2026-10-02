package com.reveila.system

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

open class SystemHome(systemHome: String) {

    companion object {
        @JvmField
        val DIRS: Array<String> = arrayOf(
            "bin",
            "configs",
            "configs/components",
            "data",
            "libs",
            "logs",
            "plugins",
            "resources",
            "temp"
        )
    }

    val systemHome: Path

    init {
        this.systemHome = Path.of(systemHome).toAbsolutePath().normalize()
        try {
            if (Files.exists(this.systemHome)) {
                if (!Files.isDirectory(this.systemHome)) {
                    throw RuntimeException("System home path exists but is not a directory: " + this.systemHome)
                }
            } else {
                Files.createDirectories(this.systemHome)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to initialize system home: " + this.systemHome, e)
        }
    }

    open fun createDirectoryStructure(createNew: Boolean) {
        try {
            if (createNew) {
                // Delete old files and create a new set of folders
                if (Files.exists(systemHome)) {
                    Files.walk(systemHome).use { walk ->
                        walk.sorted(Comparator.reverseOrder())
                            .filter { path -> path != systemHome } // Don't delete systemHome itself
                            .forEach { path ->
                                try {
                                    Files.delete(path)
                                } catch (e: IOException) {
                                    throw RuntimeException("Failed to delete: $path", e)
                                }
                            }
                    }
                }
            }

            // Create folders from DIRS
            for (dir in DIRS) {
                val dirPath = systemHome.resolve(dir)
                if (!Files.exists(dirPath)) {
                    Files.createDirectories(dirPath)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to create directory structure in: $systemHome", e)
        }
    }

    open fun resolve(path: String): Path = systemHome.resolve(path)
}
