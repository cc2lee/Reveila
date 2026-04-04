package com.reveila.android;

import dalvik.system.DexClassLoader;

/**
 * Android-specific ClassLoader that implements the Child-First delegation model.
 * This allows plugins to override libraries provided by the host app.
 */
public class ChildFirstDexClassLoader extends DexClassLoader {

    public ChildFirstDexClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. Check if class is already loaded
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }

        // 2. CRITICAL: Always delegate "java.*", "javax.*", and "android.*" to the parent.
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
            return super.loadClass(name, resolve);
        }

        try {
            // 3. CHILD FIRST: Try to find the class in the DEX files of this loader
            c = findClass(name);
            return c;
        } catch (ClassNotFoundException e) {
            // 4. PARENT LAST: If not found in child, ask the parent.
            return super.loadClass(name, resolve);
        }
    }
}
