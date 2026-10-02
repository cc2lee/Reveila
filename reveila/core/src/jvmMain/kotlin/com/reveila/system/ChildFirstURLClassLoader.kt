package com.reveila.system

import java.net.URL
import java.net.URLClassLoader

open class ChildFirstURLClassLoader(urls: Array<URL>, parent: ClassLoader?) : URLClassLoader(urls, parent) {

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // 1. Check if class is already loaded (Standard optimization)
            var c = findLoadedClass(name)
            if (c != null) {
                if (resolve) {
                    resolveClass(c)
                }
                return c
            }

            // 2. CRITICAL: Always delegate "java.*" and "javax.*" to the parent/system.
            if (name.startsWith("java.") || name.startsWith("javax.")) {
                return super.loadClass(name, resolve)
            }

            try {
                // 3. CHILD FIRST: Try to find the class in THIS loader's URLs (the plugin jars)
                c = findClass(name)
                if (resolve) {
                    resolveClass(c)
                }
                return c
            } catch (e: ClassNotFoundException) {
                // 4. PARENT LAST: If not found in child, ask the parent.
                return super.loadClass(name, resolve)
            }
        }
    }

    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String): Class<*> {
        return loadClass(name, false)
    }
}
