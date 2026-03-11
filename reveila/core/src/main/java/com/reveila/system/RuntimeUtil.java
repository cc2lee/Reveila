/*
 * Created on May 20, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.reveila.system;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public final class RuntimeUtil {

	private RuntimeUtil() {}

	public static Properties getArgsAsProperties(String[] args) {
		Properties cmdArgs = new Properties();
		if (args != null) {
			for (String arg : args) {
				String[] parts = arg.split("=", 2);
				if (parts.length == 2 && !parts[0].isEmpty()) {
					cmdArgs.put(parts[0], parts[1]);
				} else {
					// It's good practice to warn about arguments that don't fit the expected format.
					// Since the logger isn't configured yet, System.err is the best option.
					System.err.println("Warning: Ignoring malformed command-line argument: " + arg);
				}
			}
		}
		return cmdArgs;
	}

	public static ClassLoader createPluginClassLoader(String dir, ClassLoader parentClassLoader) throws Exception {
		Path root = Paths.get(dir);
		List<URL> urls = Files.list(root)
				.filter(path -> {
					// 1. Check for JARs
					if (path.toString().endsWith(".jar")) {
						return true;
					}

					// 2. Check for the 'classes' directory robustly
					if (Files.isDirectory(path)) {
						Path fileName = path.getFileName();
						// getFileName() returns only the last part (e.g., "classes")
						// without separators, making this OS-agnostic.
						return fileName != null && fileName.toString().equalsIgnoreCase("classes");
					}

					return false;
				})
				.map(path -> {
					try {
						// CRITICAL: .toUri() detects if 'path' is a directory
						// and appends the trailing "/" automatically.
						return path.toUri().toURL();
					} catch (Exception e) {
						throw new RuntimeException("Malformed URL for path: " + path, e);
					}
				})
				.collect(Collectors.toList());

		URL[] urlArray = urls.toArray(new URL[0]);
		
		// ADR 0006: Check if we are on Android to use DexClassLoader
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("android") || System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik")) {
			// On Android, we need to handle DEX files.
			// This assumes the jars in the directory are actually DEX-optimized or contain classes.dex
			// For a true core-shared implementation, we'd use a PlatformAdapter-provided loader.
			return createAndroidClassLoader(dir, parentClassLoader);
		}

		return new ChildFirstURLClassLoader(urlArray, parentClassLoader);
	}

	private static ClassLoader createAndroidClassLoader(String dir, ClassLoader parent) {
		try {
			// Reflection used here to avoid hard dependency on Android SDK in the core Java project
			Class<?> dexClass = Class.forName("dalvik.system.DexClassLoader");
			java.io.File fileDir = new java.io.File(dir);
			java.io.File[] files = fileDir.listFiles((d, name) -> name.endsWith(".jar") || name.endsWith(".dex"));
			if (files == null || files.length == 0) return parent;

			StringBuilder pathBuilder = new StringBuilder();
			for (java.io.File f : files) {
				if (pathBuilder.length() > 0) pathBuilder.append(java.io.File.pathSeparator);
				pathBuilder.append(f.getAbsolutePath());
			}

			return (ClassLoader) dexClass.getConstructor(String.class, String.class, String.class, ClassLoader.class)
					.newInstance(pathBuilder.toString(), null, null, parent);
		} catch (Exception e) {
			return parent;
		}
	}
}
