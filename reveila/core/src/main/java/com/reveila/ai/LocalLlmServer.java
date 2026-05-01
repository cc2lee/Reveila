package com.reveila.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalLlmServer {
    private Process process;
    private final File executable;
    private final File modelFile;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final Logger logger = Logger.getLogger(LocalLlmServer.class.getName());

    public LocalLlmServer(File executable, File modelFile) {
        this.executable = executable;
        this.modelFile = modelFile;
    }

    public synchronized void start() {
        if (isRunning.get()) {
            logger.info("LocalLlmServer is already running.");
            return;
        }

        new Thread(() -> {
            try {
                validateEnvironment();

                ProcessBuilder pb = new ProcessBuilder(
                        executable.getAbsolutePath(),
                        "--model", modelFile.getAbsolutePath(),
                        "--port", "8888",
                        "--threads", "4",
                        "--ctx-size", "2048",
                        "--host", "127.0.0.1");

                pb.redirectErrorStream(true);
                process = pb.start();
                isRunning.set(true);

                logger.info("Native LLM Server started (PID: " + process.pid() + ")");

                // Consuming the stream is critical to prevent the process from hanging
                // when the internal buffer fills up.
                consumeStream(process);

                int exitCode = process.waitFor();
                logger.warning("Native LLM Server terminated with code: " + exitCode);

            } catch (InterruptedException e) {
                logger.warning("LLM Server watchdog interrupted.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "CRITICAL: Native LLM Server failure", e);
            } finally {
                cleanup();
            }
        }, "Reveila-LLM-Watchdog").start();
    }

    private void validateEnvironment() throws IOException {
        if (!executable.exists())
            throw new IOException("Binary not found: " + executable.getAbsolutePath());
        if (!modelFile.exists())
            throw new IOException("Model file not found: " + modelFile.getAbsolutePath());

        if (!executable.canExecute() && !executable.setExecutable(true)) {
            throw new IOException("Failed to set execution permissions on: " + executable.getName());
        }
    }

    private void consumeStream(Process p) {
        // Run in a separate thread so we don't block the watchdog's waitFor()
        Thread reader = new Thread(() -> {
            try (BufferedReader readerIn = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = readerIn.readLine()) != null) {
                    // Optional: log specific LLM status updates here
                    // logger.finest(line);
                }
            } catch (IOException e) {
                // Stream closed
            }
        }, "Reveila-LLM-StreamConsumer");
        reader.setDaemon(true);
        reader.start();
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Stopping LocalLlmServer (PID: " + process.pid() + ")...");
            }
            
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        isRunning.set(false);
        process = null;
    }

    private void cleanup() {
        isRunning.set(false);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}