package com.reveila.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/*
 * This class is used to copy the Reveila system files from the assets folder to the app's files directory.
 */
public class ReveilaSetup {

  private static final String TAG = "ReveilaSetup";
  private static final String REVEILA_ASSET_PATH = "reveila/system";
  
  private final Context context;

  public ReveilaSetup(Context context, boolean overwrite) throws IOException {
    this(context, new File(context.getFilesDir(), "reveila/system").getAbsolutePath(), overwrite);
  }

  public ReveilaSetup(Context context, String targetPath, boolean overwrite) throws IOException {
    this.context = context;
    File targetDir = new File(targetPath);
    // We copy the contents of the 'reveila/system' asset folder to the target directory.
    // The target directory itself is treated as 'system.home'.
    copyAssetFolder(REVEILA_ASSET_PATH, targetDir, overwrite);
  }

  private void copyAssetFolder(String assetFolderPath, File targetDir, boolean overwrite) throws IOException {
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
    }

    AssetManager assetManager = context.getAssets();
    String[] assets = assetManager.list(assetFolderPath);

    if (assets == null || assets.length == 0) {
      // It might be a file, or empty dir
      try (InputStream in = assetManager.open(assetFolderPath)) {
          copyFile(in, targetDir, overwrite);
      } catch (IOException e) {
          // Not a file, just an empty directory or invalid path
      }
      return;
    }

    for (String asset : assets) {
      String assetPath = assetFolderPath + "/" + asset;
      File targetFile = new File(targetDir, asset);
      
      // Check if this asset is a directory by trying to list its contents
      String[] subAssets = assetManager.list(assetPath);
      if (subAssets != null && subAssets.length > 0) {
        // It's a directory
        copyAssetFolder(assetPath, targetFile, overwrite);
      } else {
        // It's a file
        if (!overwrite && targetFile.exists()) {
          continue;
        }
        try (InputStream in = assetManager.open(assetPath);
             FileOutputStream out = new FileOutputStream(targetFile)) {
          byte[] buffer = new byte[1024];
          int read;
          while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
          }
          Log.d(TAG, "Copied asset file to: " + targetFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            // It was an empty directory, not a file
            if (!targetFile.exists()) targetFile.mkdirs();
        }
      }
    }
  }

  private void copyFile(InputStream in, File targetFile, boolean overwrite) throws IOException {
      if (!overwrite && targetFile.exists()) return;
      try (FileOutputStream out = new FileOutputStream(targetFile)) {
          byte[] buffer = new byte[1024];
          int read;
          while ((read = in.read(buffer)) != -1) {
              out.write(buffer, 0, read);
          }
      }
  }
}
