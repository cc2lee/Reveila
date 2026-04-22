package com.reveila.android;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import com.reveila.system.SystemContext;
import com.reveila.system.SystemProxy;
import com.reveila.system.MetaObject;
import com.reveila.system.Manifest;

public class AndroidPlugins {

    public static final String RELATIVE_ROOT_DIR = "reveila/system/plugins/";

    public static ClassLoader createPluginClassLoader(String dir, ClassLoader parent) {
		try {
			// Reflection used here to avoid hard dependency on Android SDK in the core Java project
			Class<?> dexClass;
			try {
				// Try to use our Child-First implementation if available on the classpath
				dexClass = Class.forName("com.reveila.android.ChildFirstDexClassLoader");
			} catch (ClassNotFoundException e) {
				// Fallback to standard DexClassLoader (Parent-First)
				dexClass = Class.forName("dalvik.system.DexClassLoader");
			}

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

    public static SystemProxy createProxy(Context context, String pluginId, String className) {
        Map<String, Object> config = Map.of(
                "name", pluginId,
                "class", className,
                "plugin",
                Map.of("directory", new File(context.getFilesDir(), RELATIVE_ROOT_DIR + pluginId).getAbsolutePath()));
        
        Manifest manifest = new Manifest();
        manifest.setComponentType("plugin");
        manifest.setName(pluginId);
        manifest.setImplementationClass(className);

        return new SystemProxy(new MetaObject(config), manifest);
    }

    /**
     * Loads a plugin and returns its SystemProxy.
     */
    public static SystemProxy loadPlugin(Context context, SystemContext systemContext, String pluginFileName, String className) {
        try {
            String pluginId = pluginFileName.replace(".jar", "").replace(".dex", "");
            File pluginDir = new File(context.getFilesDir(), RELATIVE_ROOT_DIR + pluginId);
            if (!pluginDir.exists())
                pluginDir.mkdirs();

            File pluginFile = new File(pluginDir, pluginFileName);
            copyAssetToFile(context, pluginFileName, pluginFile);
            pluginFile.setReadOnly();

            SystemProxy proxy = createProxy(context, pluginId, className);
            proxy.setContext(systemContext);
            ClassLoader loader = createPluginClassLoader(pluginDir.getAbsolutePath(), AndroidPlugins.class.getClassLoader());
            
            Method setClassLoaderMethod = SystemProxy.class.getDeclaredMethod("setClassLoader", ClassLoader.class);
            setClassLoaderMethod.setAccessible(true);
            setClassLoaderMethod.invoke(proxy, loader);

            return proxy;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void copyAssetToFile(Context context, String assetName, File dest) throws Exception {
        try (InputStream in = context.getAssets().open(assetName);
                FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}