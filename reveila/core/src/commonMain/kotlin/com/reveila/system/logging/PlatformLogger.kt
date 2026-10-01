package com.reveila.system.logging

/**
 * Multiplatform structured logging abstraction routing to Android Logcat,
 * Apple os_log / NSLog, and platform console/file handlers.
 */
interface PlatformLogger {
    fun info(message: () -> String)
    fun warning(message: () -> String, throwable: Throwable? = null)
    fun severe(message: () -> String, throwable: Throwable? = null)
    fun debug(message: () -> String)

    companion object {
        operator fun invoke(name: String): PlatformLogger = createPlatformLogger(name)
    }
}

expect fun createPlatformLogger(name: String): PlatformLogger
