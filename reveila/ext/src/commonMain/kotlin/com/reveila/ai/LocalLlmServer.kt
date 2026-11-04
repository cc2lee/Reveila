package com.reveila.ai

import com.reveila.system.io.PlatformFileSystem
import com.reveila.system.logging.PlatformLogger
import kotlin.concurrent.Volatile

open class LocalLlmServer(
    private val executablePath: String,
    private val modelFilePath: String
) {
    @Volatile
    private var isRunning: Boolean = false
    private val logger = PlatformLogger("com.reveila.ai.LocalLlmServer")

    open fun start() {
        if (isRunning) {
            logger.info("LocalLlmServer is already running.")
            return
        }

        val fs = PlatformFileSystem()
        if (!fs.exists(executablePath)) {
            logger.severe("Binary not found: $executablePath")
            return
        }
        if (!fs.exists(modelFilePath)) {
            logger.severe("Model file not found: $modelFilePath")
            return
        }

        isRunning = true
        logger.info("LocalLlmServer started for model: $modelFilePath")
    }

    open fun stop() {
        isRunning = false
        logger.info("LocalLlmServer stopped.")
    }

    open fun isRunning(): Boolean = isRunning
}
