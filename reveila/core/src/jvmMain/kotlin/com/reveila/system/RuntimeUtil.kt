package com.reveila.system

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import java.util.stream.Collectors

object RuntimeUtil {

    @JvmStatic
    fun getArgsAsProperties(args: Array<String>?): Properties {
        val cmdArgs = Properties()
        if (args != null) {
            for (arg in args) {
                val parts = arg.split("=".toRegex(), limit = 2).toTypedArray()
                if (parts.size == 2 && parts[0].isNotEmpty()) {
                    cmdArgs[parts[0]] = parts[1]
                } else {
                    System.err.println("Warning: Ignoring malformed command-line argument: $arg")
                }
            }
        }
        return cmdArgs
    }

    @JvmStatic
    @Throws(Exception::class)
    fun createPluginClassLoader(dir: String, parent: ClassLoader?): ClassLoader {
        val root = Paths.get(dir)
        val urls: List<URL> = Files.list(root).use { stream ->
            stream.filter { path: Path ->
                // 1. Check for JARs
                if (path.toString().endsWith(".jar")) {
                    return@filter true
                }

                // 2. Check for the 'classes' directory robustly
                if (Files.isDirectory(path)) {
                    val fileName = path.fileName
                    return@filter fileName != null && fileName.toString().equals("classes", ignoreCase = true)
                }

                false
            }
            .map { path: Path ->
                try {
                    path.toUri().toURL()
                } catch (e: Exception) {
                    throw RuntimeException("Malformed URL for path: $path", e)
                }
            }
            .collect(Collectors.toList())
        }

        val urlArray = urls.toTypedArray()

        // ADR 0006: Check if we are on Android to use DexClassLoader
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("android") || System.getProperty("java.vm.name").equals("Dalvik", ignoreCase = true)) {
            return createAndroidPluginClassLoader(dir, parent)
        }

        return ChildFirstURLClassLoader(urlArray, parent)
    }

    @JvmStatic
    private fun createAndroidPluginClassLoader(dir: String, parent: ClassLoader?): ClassLoader {
        return try {
            val dexClass: Class<*> = try {
                Class.forName("com.reveila.android.ChildFirstDexClassLoader")
            } catch (e: ClassNotFoundException) {
                Class.forName("dalvik.system.DexClassLoader")
            }

            val fileDir = File(dir)
            val files = fileDir.listFiles { _, name -> name.endsWith(".jar") || name.endsWith(".dex") }
            if (files == null || files.isEmpty()) return parent ?: ClassLoader.getSystemClassLoader()

            val pathBuilder = StringBuilder()
            for (f in files) {
                if (pathBuilder.isNotEmpty()) pathBuilder.append(File.pathSeparator)
                pathBuilder.append(f.absolutePath)
            }

            dexClass.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                ClassLoader::class.java
            ).newInstance(pathBuilder.toString(), null, null, parent) as ClassLoader
        } catch (e: Exception) {
            parent ?: ClassLoader.getSystemClassLoader()
        }
    }
}
