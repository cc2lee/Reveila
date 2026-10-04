package com.reveila.system

import com.reveila.system.io.PlatformFileSystem

open class SystemHome(val systemHome: String) {

    companion object {
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

    private val fs = PlatformFileSystem()

    init {
        try {
            if (fs.exists(systemHome)) {
                if (!fs.isDirectory(systemHome)) {
                    throw RuntimeException("System home path exists but is not a directory: $systemHome")
                }
            } else {
                fs.createDirectories(systemHome)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize system home: $systemHome", e)
        }
    }

    open fun createDirectoryStructure(createNew: Boolean) {
        if (createNew && fs.exists(systemHome)) {
            fs.delete(systemHome, recursive = true)
            fs.createDirectories(systemHome)
        }
        for (dir in DIRS) {
            val p = fs.resolve(systemHome, dir)
            if (!fs.exists(p)) {
                fs.createDirectories(p)
            }
        }
    }

    open fun getConfigsDirectory(): String = fs.resolve(systemHome, "configs")
    open fun getPluginsDirectory(): String = fs.resolve(systemHome, "plugins")
    open fun getDataDirectory(): String = fs.resolve(systemHome, "data")
    open fun getBinDirectory(): String = fs.resolve(systemHome, "bin")
    open fun getLogsDirectory(): String = fs.resolve(systemHome, "logs")
}
