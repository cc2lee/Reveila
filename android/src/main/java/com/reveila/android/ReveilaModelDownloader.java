package com.reveila.android.util;

import android.content.Context;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles the "First Boot" model acquisition from Sovereign Storage.
 */
public class ReveilaModelDownloader {
    private static final String TAG = "ReveilaDownloader";
    // This should point to your S3-compatible Sovereign storage or central node
    private static final String MODEL_URL = "https://storage.reveila.io/models/gemma-2b-v2.gguf";
    private static final String MODEL_FILENAME = "gemma-2b.gguf";

    public interface DownloadCallback {
        void onComplete(File modelFile);
        void onError(Exception e);
        void onProgress(int progress);
    }

    public static void ensureModelExists(Context context, DownloadCallback callback) {
        File modelDir = new File(context.getFilesDir(), "models");
        if (!modelDir.exists()) modelDir.mkdirs();

        File modelFile = new File(modelDir, MODEL_FILENAME);

        // Check if we already have the model (First Boot check)
        if (modelFile.exists()) {
            Log.info(TAG, "Model found in internal memory. Skipping download.");
            callback.onComplete(modelFile);
            return;
        }

        // Start Sovereign Pulse download
        new Thread(() -> downloadModel(modelFile, callback)).start();
    }

    private static void downloadModel(File targetFile, DownloadCallback callback) {
        HttpURLConnection connection = null;
        try {
            Log.info(TAG, "Initiating Sovereign Pulse: Downloading " + MODEL_FILENAME);
            URL url = new URL(MODEL_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            int fileLength = connection.getContentLength();

            try (InputStream input = new BufferedInputStream(url.openStream());
                 FileOutputStream output = new FileOutputStream(targetFile)) {

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    // Report progress back to the UI/Fabric
                    if (fileLength > 0) {
                        callback.onProgress((int) (total * 100 / fileLength));
                    }
                    output.write(data, 0, count);
                }
                
                Log.info(TAG, "Sovereign Node Provisioned: Model saved.");
                callback.onComplete(targetFile);

            }
        } catch (Exception e) {
            Log.error(TAG, "Sovereign Pulse Interrupted: " + e.getMessage());
            callback.onError(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}