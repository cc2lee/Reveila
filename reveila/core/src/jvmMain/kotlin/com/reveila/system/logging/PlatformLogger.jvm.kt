package com.reveila.system.logging

import java.util.logging.Level
import java.util.logging.Logger

class JvmPlatformLogger(name: String) : PlatformLogger {
    private val logger = Logger.getLogger(name)

    override fun info(message: () -> String) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(message())
        }
    }

    override fun warning(message: () -> String, throwable: Throwable?) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, message(), throwable)
        }
    }

    override fun severe(message: () -> String, throwable: Throwable?) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, message(), throwable)
        }
    }

    override fun debug(message: () -> String) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(message())
        }
    }
}

actual fun createPlatformLogger(name: String): PlatformLogger = JvmPlatformLogger(name)
