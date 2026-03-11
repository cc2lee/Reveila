package com.reveila.system;

import java.net.URL;
import java.net.URLClassLoader;

public class ChildFirstURLClassLoader extends URLClassLoader {

    // Local Jar: new URL("file:/C:/libs/mysql-connector.jar")
    // Local Jar: new URL("file:/usr/lib/java/auth.jar")
    // Directory: new URL("file:/Users/project/target/classes/")
    // Web Server: new URL("https://example.com/lib/plugin.jar")
    // Web Server: new URL("https://example.com/classes/")
    // Jar-in-Jar: new URL("jar:file:/apps/main.jar!/lib/extension.jar")
    public ChildFirstURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        
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
                return super.loadClass(name);
            }

            try {
                // 3. CHILD FIRST: Try to find the class in THIS loader's URLs (the plugin jars)
                c = findClass(name);
                resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {
                // 4. PARENT LAST: If not found in child, ask the parent.
                // This allows the plugin to use common libs (like logging) provided by the main app
                // only if the plugin doesn't provide them itself.
                return super.loadClass(name);
            }
        }
    }
}