package com.reveila.system;

import java.net.URL;
import java.net.URLClassLoader;

public class ParentLastURLClassLoader extends URLClassLoader {

    public ParentLastURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Thread-safe class loading lock
        synchronized (getClassLoadingLock(name)) {
            
            // 1. Check if class is already loaded (Standard optimization)
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }

            // 2. CRITICAL: Always delegate "java.*" and "javax.*" to the parent/system.
            // If you try to load "java.lang.String" from your jar, the JVM will throw a SecurityException.
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                try {
                    return getParent().loadClass(name);
                } catch (ClassNotFoundException e) {
                    // If the parent can't find it (rare for java.*), give up.
                    throw e;
                }
            }

            // 3. CHILD FIRST: Try to find the class in THIS loader's URLs (the plugin jars)
            try {
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            } catch (ClassNotFoundException e) {
                // Ignore! This means the class isn't in the plugin jar.
                // We will fall back to the parent below.
            }

            // 4. PARENT LAST: If not found in child, ask the parent.
            // This allows the plugin to use common libs (like logging) provided by the main app
            // only if the plugin doesn't provide them itself.
            return super.loadClass(name, resolve);
        }
    }
}