package com.reveila.android;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.content.Context;

import android.content.res.AssetManager;
import android.util.Log;

public class ReveilaSetup {

  private static final String TAG = "Reveila";
  private static final String REVEILA_ASSET_PATH = "reveila/system";
  
  private Context context;

  public ReveilaSetup(Context context, boolean overwrite) throws IOException {
    this.context = context;
    copyAsset("system", "reveila", overwrite);
  }

  private void copyAsset(String assetName, String containingDirectory, boolean overwrite) throws IOException {

    File targetDir = new File(context.getFilesDir(), containingDirectory);
    if (!targetDir.exists() && !targetDir.mkdirs()) {
      throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
    }
    
    File targetFile = new File(targetDir, assetName);
    if (!overwrite && targetFile.exists() && targetFile.isFile()) {
      Log.i(TAG, "Asset already exists, overwrite is set to false, skipping: " + targetFile.getAbsolutePath());
      return;
    }

    String assetPath = containingDirectory + "/" + assetName;
    AssetManager assetManager = context.getAssets();
    
    // The AssetManager API makes it tricky to distinguish files from directories.
    // A common pattern is to try opening the asset as a stream. If it fails,
    // assume it's a directory and recurse.
    try {
      // If assetManager.open() succeeds, it's a file. We use try-with-resources
      // to ensure both streams are closed automatically.
      try (InputStream in = assetManager.open(assetPath);
           FileOutputStream out = new FileOutputStream(targetFile)) {
        
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        Log.i(TAG, "Copied asset file to: " + targetFile.getAbsolutePath());
      }
    } catch (FileNotFoundException e) {
      // This is the expected exception for a directory, so we recurse.
      String[] assets = assetManager.list(assetPath);
      if (assets == null) {
        Log.w(TAG, "Asset 'assets/" + assetPath + "' not found. Skipping.");
      } else {
        for (String asset : assets) {
          // Recursively copy each child asset.
          copyAsset(asset, containingDirectory + "/" + assetName, overwrite);
        }
      }
    }
  }
}