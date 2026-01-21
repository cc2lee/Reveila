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
		return new ChildFirstURLClassLoader(urlArray, parentClassLoader);
	}
}
