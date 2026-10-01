package com.reveila.system.logging

import platform.Foundation.NSLog

class ApplePlatformLogger(private val name: String) : PlatformLogger {

    override fun info(message: () -> String) {
        NSLog("[INFO] [%s] %s", name, message())
    }

    override fun warning(message: () -> String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[WARN] [%s] %s - %s", name, message(), throwable.message ?: "")
        } else {
            NSLog("[WARN] [%s] %s", name, message())
        }
    }

    override fun severe(message: () -> String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[ERROR] [%s] %s - %s", name, message(), throwable.message ?: "")
        } else {
            NSLog("[ERROR] [%s] %s", name, message())
        }
    }

    override fun debug(message: () -> String) {
        NSLog("[DEBUG] [%s] %s", name, message())
    }
}

actual fun createPlatformLogger(name: String): PlatformLogger = ApplePlatformLogger(name)
