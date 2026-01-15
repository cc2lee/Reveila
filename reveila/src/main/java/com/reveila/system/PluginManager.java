package com.reveila.system;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle and execution of third-party plugins.
 * Each plugin is loaded in its own isolated ClassLoader (Parent-Last)
 * to prevent dependency conflicts (Jar Hell).
 */
public class PluginManager {

    // Registry: Maps a unique Plugin ID -> Its Isolated ClassLoader
    // Using ConcurrentHashMap to support multi-threaded access/execution
    private final Map<String, ClassLoader> pluginRegistry = new ConcurrentHashMap<>();

    /**
     * Registers a new plugin by creating a dedicated ClassLoader for its JARs.
     * * @param pluginId   A unique name for this plugin (e.g., "stripe-integration")
     * @param pluginDir  The directory containing this plugin's .jar files
     * @throws Exception If directory is invalid or no JARs are found
     */
    public void registerPlugin(String pluginId, Path pluginDir) throws Exception {
        if (!Files.exists(pluginDir) || !Files.isDirectory(pluginDir)) {
            throw new IllegalArgumentException("Plugin directory does not exist: " + pluginDir);
        }

        // 1. Gather all JARs in the plugin's folder
        List<URL> urls = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginDir, "*.jar")) {
            for (Path entry : stream) {
                urls.add(entry.toUri().toURL());
            }
        }

        if (urls.isEmpty()) {
            throw new Exception("No .jar files found in plugin directory: " + pluginDir);
        }

        // 2. Create the Isolated "Parent-Last" ClassLoader
        // - URLs: The plugin's specific jars
        // - Parent: The AppClassLoader (so plugin can see core Java/App interfaces)
        ClassLoader isolatedLoader = new ParentLastURLClassLoader(
            urls.toArray(new URL[0]), 
            this.getClass().getClassLoader() 
        );

        // 3. Register it
        // If a plugin with this ID exists, we replace it (simple hot-swap logic)
        ClassLoader oldLoader = pluginRegistry.put(pluginId, isolatedLoader);
        if (oldLoader != null) {
            closeLoader(oldLoader); // Cleanup old version
        }
        
        System.out.println("Registered plugin: " + pluginId + " with " + urls.size() + " jars.");
    }

    /**
     * Executes a specific method on a class within a registered plugin.
     * Handles the Thread Context ClassLoader (TCCL) swap automatically.
     */
    public Object invokePlugin(String pluginId, String className, String methodName, Object... args) throws Exception {
        ClassLoader pluginLoader = pluginRegistry.get(pluginId);
        if (pluginLoader == null) {
            throw new IllegalArgumentException("Plugin not found: " + pluginId);
        }

        // 1. Load the Class using the Plugin's Isolated Loader
        Class<?> clazz = pluginLoader.loadClass(className);
        
        // 2. Create Instance (Assumes default no-arg constructor for simplicity)
        Object instance = clazz.getDeclaredConstructor().newInstance();

        // 3. Resolve arguments types for reflection (simplified)
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }
        Method method = clazz.getMethod(methodName, argTypes);

        // 4. The "Scope Wrapper" Pattern
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextLoader = currentThread.getContextClassLoader();

        try {
            // SWAP: Set context to the plugin's loader so it can find its resources/dependencies
            currentThread.setContextClassLoader(pluginLoader);

            // EXECUTE
            return method.invoke(instance, args);

        } finally {
            // RESTORE: Always put the original loader back
            currentThread.setContextClassLoader(originalContextLoader);
        }
    }

    /**
     * Unloads a specific plugin and releases file locks.
     */
    public void unloadPlugin(String pluginId) {
        ClassLoader loader = pluginRegistry.remove(pluginId);
        if (loader != null) {
            closeLoader(loader);
            System.out.println("Unloaded plugin: " + pluginId);
        }
    }

    /**
     * Cleanly shuts down all plugins.
     */
    public void shutdown() {
        for (String id : pluginRegistry.keySet()) {
            unloadPlugin(id);
        }
    }

    // Helper to close URLClassLoaders to release file handles (Windows specific issue mostly)
    private void closeLoader(ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            try {
                ((URLClassLoader) loader).close();
            } catch (IOException e) {
                System.err.println("Warning: Failed to close class loader: " + e.getMessage());
            }
        }
    }
}