// Located in: Reveila-Suite/Android-App/src/main/java/com/reveila/app/util/SafePluginLoader.java
package com.reveila.android;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

import com.reveila.system.SystemProxy;
import com.reveila.system.MetaObject;
import com.reveila.system.Manifest;
import com.reveila.system.RuntimeUtil;

public class SafePluginLoader {

    public static SystemProxy createProxy(Context context, String pluginId, String className) {
        Map<String, Object> config = Map.of(
                "name", pluginId,
                "class", className,
                "plugin",
                Map.of("directory", new File(context.getFilesDir(), "plugins/" + pluginId).getAbsolutePath()));
        
        Manifest manifest = new Manifest();
        manifest.setComponentType("plugin");
        manifest.setName(pluginId);
        manifest.setImplementationClass(className);

        return new SystemProxy(new MetaObject(config), manifest);
    }

    /**
     * Loads a plugin and returns its SystemProxy.
     */
    public static SystemProxy loadPlugin(Context context, String pluginFileName, String className) {
        try {
            String pluginId = pluginFileName.replace(".jar", "").replace(".dex", "");
            File pluginDir = new File(context.getFilesDir(), "plugins/" + pluginId);
            if (!pluginDir.exists())
                pluginDir.mkdirs();

            File pluginFile = new File(pluginDir, pluginFileName);
            copyAssetToFile(context, pluginFileName, pluginFile);
            pluginFile.setReadOnly();

            SystemProxy proxy = createProxy(context, pluginId, className);
            ClassLoader loader = RuntimeUtil.createPluginClassLoader(pluginDir.getAbsolutePath(), SafePluginLoader.class.getClassLoader());
            
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