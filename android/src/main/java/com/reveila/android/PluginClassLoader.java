package com.reveila.android;

import dalvik.system.DexClassLoader;

/**
 * A custom ClassLoader for Android that implements a child-first loading strategy.
 * This allows plugins to provide their own versions of libraries while still
 * sharing core system and Reveila classes with the host application.
 */
public class PluginClassLoader extends DexClassLoader {
    private final ClassLoader parent;

    public PluginClassLoader(String dexPath, String optimizedDir, String libraryPath, ClassLoader parent) {
        super(dexPath, optimizedDir, libraryPath, parent);
        this.parent = parent;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Thread-safe class loading lock
        synchronized (getClassLoadingLock(name)) {
            // 1. Always check if already loaded
            Class<?> c = findLoadedClass(name);
            if (c != null) return c;

            // 2. CRITICAL: Identify classes that MUST come from the System/App
            // This includes Java core, Android framework, and shared Reveila interfaces.
            if (isSystemClass(name) || isSharedInterface(name)) {
                return parent.loadClass(name);
            }

            try {
                // 3. TRY CHILD FIRST: Look in the plugin's own DEX/JAR
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            } catch (ClassNotFoundException e) {
                // 4. FALLBACK TO PARENT: If not in plugin, check the main app
                return super.loadClass(name, resolve);
            }
        }
    }

    private boolean isSystemClass(String name) {
        return name.startsWith("java.") || 
               name.startsWith("javax.") || 
               name.startsWith("android.") || 
               name.startsWith("androidx.") ||
               name.startsWith("com.google.android."); // e.g., GMS/Firebase
    }

    private boolean isSharedInterface(String name) {
        // Core Reveila classes should be shared between host and plugins to allow
        // them to interact via common interfaces and types.
        return name.startsWith("com.reveila.");
    }
}
