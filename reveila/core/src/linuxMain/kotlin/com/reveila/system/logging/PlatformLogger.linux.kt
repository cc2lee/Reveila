package com.reveila.system.logging

import platform.posix.fprintf
import platform.posix.stderr

class LinuxPlatformLogger(private val name: String) : PlatformLogger {

    override fun info(message: () -> String) {
        println("[INFO] [$name] ${message()}")
    }

    override fun warning(message: () -> String, throwable: Throwable?) {
        val err = throwable?.message?.let { " - $it" } ?: ""
        println("[WARN] [$name] ${message()}$err")
    }

    override fun severe(message: () -> String, throwable: Throwable?) {
        val err = throwable?.message?.let { " - $it" } ?: ""
        fprintf(stderr, "[ERROR] [%s] %s%s\n", name, message(), err)
    }

    override fun debug(message: () -> String) {
        println("[DEBUG] [$name] ${message()}")
    }
}

actual fun createPlatformLogger(name: String): PlatformLogger = LinuxPlatformLogger(name)
