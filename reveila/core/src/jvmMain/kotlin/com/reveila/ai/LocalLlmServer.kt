package com.reveila.ai

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

open class LocalLlmServer(
    private val executable: File,
    private val modelFile: File
) {
    private var process: Process? = null
    private val isRunning = AtomicBoolean(false)
    private val logger: Logger = Logger.getLogger(LocalLlmServer::class.java.name)

    @Synchronized
    open fun start() {
        if (isRunning.get()) {
            logger.info("LocalLlmServer is already running.")
            return
        }

        thread(name = "Reveila-LLM-Watchdog") {
            try {
                validateEnvironment()

                val pb = ProcessBuilder(
                    executable.absolutePath,
                    "--model", modelFile.absolutePath,
                    "--port", "8888",
                    "--threads", "4",
                    "--ctx-size", "2048",
                    "--host", "127.0.0.1"
                )

                pb.redirectErrorStream(true)
                val proc = pb.start()
                process = proc
                isRunning.set(true)

                logger.info("Native LLM Server started (PID: ${proc.pid()})")

                consumeStream(proc)

                val exitCode = proc.waitFor()
                logger.warning("Native LLM Server terminated with code: $exitCode")
            } catch (e: InterruptedException) {
                logger.warning("LLM Server watchdog interrupted.")
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                logger.log(Level.SEVERE, "CRITICAL: Native LLM Server failure", e)
            } finally {
                cleanup()
            }
        }
    }

    @Throws(IOException::class)
    private fun validateEnvironment() {
        if (!executable.exists()) {
            throw IOException("Binary not found: ${executable.absolutePath}")
        }
        if (!modelFile.exists()) {
            throw IOException("Model file not found: ${modelFile.absolutePath}")
        }

        if (!executable.canExecute() && !executable.setExecutable(true)) {
            throw IOException("Failed to set execution permissions on: ${executable.name}")
        }
    }

    private fun consumeStream(p: Process) {
        thread(name = "Reveila-LLM-StreamConsumer", isDaemon = true) {
            try {
                BufferedReader(InputStreamReader(p.inputStream)).use { readerIn ->
                    var line: String?
                    while (readerIn.readLine().also { line = it } != null) {
                        logger.finest(line)
                    }
                }
            } catch (e: IOException) {
                // Stream closed
            }
        }
    }

    @Synchronized
    open fun stop() {
        val proc = process
        if (proc != null && proc.isAlive) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Stopping LocalLlmServer (PID: ${proc.pid()})...")
            }

            proc.destroy()
            try {
                if (!proc.waitFor(3, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (e: InterruptedException) {
                proc.destroyForcibly()
                Thread.currentThread().interrupt()
            }
        }
        isRunning.set(false)
        process = null
    }

    private fun cleanup() {
        isRunning.set(false)
        val proc = process
        if (proc != null && proc.isAlive) {
            proc.destroyForcibly()
        }
    }

    open fun isRunning(): Boolean = isRunning.get()
}
