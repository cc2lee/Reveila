package com.reveila.system;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors the plugin directory for changes and triggers a reload on the Proxy.
 * Implements a "Debounce" logic to wait for the compiler to finish writing.
 */
public class PluginWatcher implements Runnable {

    private final Path pluginPath;
    private final Proxy proxy;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PluginWatcher(Path pluginPath, Proxy proxy) {
        this.pluginPath = pluginPath;
        if (!Files.isDirectory(this.pluginPath)) {
            throw new IllegalArgumentException("Plugin path must be a valid directory: " + pluginPath);
        }
        if (proxy == null) {
            throw new IllegalArgumentException("Proxy cannot be null.");
        }
        this.proxy = proxy;
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
        pluginPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        // Check BOTH the flag and the thread's interrupted status
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
            
            // Re-check running state after polling
            if (key == null || !running.get()) continue;

                boolean shouldReload = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path fileName = (Path) event.context();
                    
                    // Only care about .jar or .class updates
                    if (fileName.toString().endsWith(".jar") || fileName.toString().endsWith(".class")) {
                        shouldReload = true;
                    }
                }

                if (shouldReload) {
                    // DEBOUNCE: Wait 500ms for the OS to release file locks (essential for Windows)
                    Thread.sleep(500);
                    System.out.println("♻️ Change detected. Reloading plugin via Proxy...");
                    try {
                        proxy.loadPlugin(pluginPath);
                        System.out.println("✅ Hot-Reload successful.");
                    } catch (Exception e) {
                        System.err.println("❌ Hot-Reload failed for component '" + proxy.toString() + "': " + e.getMessage());
                    }
                }

                if (!key.reset()) break;
            }
        } catch (IOException e) {
            System.err.println("Plugin Watcher IO Error: " + e.getMessage());
        } catch (InterruptedException e) {
            // This is expected during Proxy.stop()
            Thread.currentThread().interrupt();
        }
    }
}