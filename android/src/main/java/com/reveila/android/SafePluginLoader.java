// Located in: Reveila-Suite/Android-App/src/main/java/com/reveila/app/util/SafePluginLoader.java
package com.reveila.android;

import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import com.reveila.system.Proxy;
import com.reveila.system.MetaObject;
import java.util.Map;

/**
 * Realigned with Reveila Core Architecture (ADR 0006).
 * Uses Proxy-based invocation and standardized ClassLoading.
 */
public class SafePluginLoader {

    public static Proxy createProxy(Context context, String pluginId, String className) {
        Map<String, Object> config = Map.of(
                "name", pluginId,
                "class", className,
                "plugin",
                Map.of("directory", new File(context.getFilesDir(), "plugins/" + pluginId).getAbsolutePath()));
        return new Proxy(new MetaObject(config));
    }

    /**
     * Loads a plugin and returns its Proxy.
     * IReveilaPlugin interface has been deprecated and removed.
     */
    public static Proxy loadPlugin(Context context, String pluginFileName, String className) {
        try {
            String pluginId = pluginFileName.replace(".jar", "").replace(".dex", "");
            File pluginDir = new File(context.getFilesDir(), "plugins/" + pluginId);
            if (!pluginDir.exists())
                pluginDir.mkdirs();

            File pluginFile = new File(pluginDir, pluginFileName);
            copyAssetToFile(context, pluginFileName, pluginFile);
            pluginFile.setReadOnly();

            Proxy proxy = createProxy(context, pluginId, className);
            proxy.loadPlugin(pluginDir.toPath());

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