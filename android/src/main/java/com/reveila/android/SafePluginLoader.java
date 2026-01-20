// Located in: Reveila-Suite/Android-App/src/main/java/com/reveila/app/util/SafePluginLoader.java
package com.reveila.android;

import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import com.reveila.api.IReveilaPlugin;

public class SafePluginLoader {

    public static IReveilaPlugin loadPlugin(Context context, String pluginFileName, String className) {
        try {
            // 1. Target internal private storage (never SD card)
            File pluginDir = new File(context.getFilesDir(), "plugins");
            if (!pluginDir.exists()) pluginDir.mkdirs();
            
            File pluginFile = new File(pluginDir, pluginFileName);
            
            // 2. Copy from Assets to internal storage (simulating a download)
            copyAssetToFile(context, pluginFileName, pluginFile);

            // 3. ANDROID 14+ CRITICAL STEP:
            // Dynamically loaded code must be set to read-only before loading.
            pluginFile.setReadOnly();

            // 4. Initialize the ClassLoader
            // We use the App's ClassLoader as the parent
            DexClassLoader loader = new DexClassLoader(
                pluginFile.getAbsolutePath(),
                null, // optimizedDirectory is deprecated/null in modern Android
                null, 
                context.getClassLoader()
            );

            // 5. Instantiate and cast to the Shared Interface
            Class<?> clazz = loader.loadClass(className);
            return (IReveilaPlugin) clazz.getDeclaredConstructor().newInstance();

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