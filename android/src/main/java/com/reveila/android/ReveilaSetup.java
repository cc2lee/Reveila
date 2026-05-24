package com.reveila.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * This class handles extracting the read-only Reveila system asset footprints 
 * out of the compressed APK bundle layer and staging them into the application's 
 * active, writable local storage home workspace.
 */
public class ReveilaSetup {

    private static final String TAG = "ReveilaSetup";
    
    // The source path inside the read-only APK assets folder (from build.gradle.kts Sync)
    public static final String ASSET_FOLDER = "reveila";
    public static final String TARGET_HOME = "reveila";
    
    private final Context context;

    public ReveilaSetup(Context context, boolean overwrite) throws IOException {
        this(context, new File(context.getFilesDir(), TARGET_HOME).getAbsolutePath(), overwrite);
    }

    /**
     * Flexible path constructor that dynamically maps assets into the designated runtime home block.
     */
    public ReveilaSetup(Context context, String targetPath, boolean overwrite) throws IOException {
        this.context = context;
        Log.i(TAG, "Initializing sovereign asset synchronization pass -> Target Home: " + targetPath);
        copyAssetFolder(ASSET_FOLDER, new File(targetPath), overwrite);
    }

    private void copyAssetFolder(String assetFolderPath, File targetDir, boolean overwrite) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Failed to establish target directory path matrix: " + targetDir.getAbsolutePath());
        }

        AssetManager assetManager = context.getAssets();
        String[] assets = assetManager.list(assetFolderPath);

        // Fallback catch block: If list is empty, treat the current address token parameter as a raw asset file
        if (assets == null || assets.length == 0) {
            try (InputStream in = assetManager.open(assetFolderPath)) {
                // FIXED: Direct file loader requires target file pointer context, not the parent folder matrix
                copyFile(in, targetDir, overwrite);
            } catch (IOException e) {
                // Stifled block: Indicates an explicitly empty directory target asset leaf
            }
            return;
        }

        for (String asset : assets) {
            String assetPath = assetFolderPath + "/" + asset;
            File targetFile = new File(targetDir, asset);
            
            // Check if this asset path token represents a subdirectory layer
            String[] subAssets = assetManager.list(assetPath);
            if (subAssets != null && subAssets.length > 0) {
                // Recursive loop pass: Step down into nested resource structures
                copyAssetFolder(assetPath, targetFile, overwrite);
            } else {
                // The item is a standard file node branch OR an empty folder layout
                try (InputStream in = assetManager.open(assetPath)) {
                    
                    if (!overwrite && targetFile.exists()) {
                        continue;
                    }

                    // --- PRESERVATION COMPLIANCE: SYSTEM PROPERTIES ---
                    if (asset.equals("reveila.properties") && targetFile.exists() && targetFile.length() > 0) {
                        Log.i(TAG, "Preserving custom local modifications for 'reveila.properties'.");
                        continue;
                    }

                    // --- PRESERVATION COMPLIANCE: USER SETTINGS ---
                    if (assetFolderPath.contains("configs/settings") && targetFile.exists()) {
                        Log.i(TAG, "Skipping user configuration file overwrite pass for: " + assetPath);
                        continue;
                    }

                    copyFile(in, targetFile, overwrite);
                    Log.d(TAG, "Synchronized engine module asset -> " + targetFile.getName());
                    
                } catch (IOException e) {
                    // Resource handle is a folder leaf containing no internal sub-asset structures
                    if (!targetFile.exists() && !targetFile.mkdirs()) {
                        Log.w(TAG, "Failed to create directory leaf anchor: " + targetFile.getAbsolutePath());
                    } else {
                        Log.d(TAG, "Created structural directory node from layout target: " + assetPath);
                    }
                }
            }
        }
    }

    private void copyFile(InputStream in, File targetFile, boolean overwrite) throws IOException {
        if (!overwrite && targetFile.exists()) return;
        
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[4096]; // Expanded buffer sizing to 4KB to improve engine dex storage copies
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}