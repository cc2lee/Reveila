package com.reveila.android;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the downloading, verification, and persistence of AI models 
 * (GGUF / LiteRT) for the Sovereign Memory system.
 */
public class ModelManager {

    private static final String TAG = "ModelManager";
    private static final String MODELS_DIR = "sovereign_models";
    
    private final Context context;
    private final ExecutorService executor;

    public interface DownloadCallback {
        void onProgress(int percentage);
        void onSuccess(File modelFile);
        void onError(Exception e);
    }

    public ModelManager(Context context) {
        this.context = context.getApplicationContext();
        // Single thread executor to process one model download at a time
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Downloads a model in the background, verifies its integrity, and persists it 
     * in the app's internal scoped storage to protect it from user deletion.
     * 
     * @param modelUrl URL to download the model file from
     * @param expectedSha256 The expected SHA-256 hash to verify against
     * @param fileName The output filename (e.g., "gemma-3-4b.gguf")
     * @param callback Callback for UI updates
     */
    public void downloadModel(String modelUrl, String expectedSha256, String fileName, DownloadCallback callback) {
        executor.execute(() -> {
            // Store deep inside internal storage so the user's File Manager can't accidentally wipe the "Brain"
            File modelDir = new File(context.getFilesDir(), MODELS_DIR);
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                callback.onError(new IOException("Failed to create secure internal model directory."));
                return;
            }

            File outputFile = new File(modelDir, fileName);
            
            // Temporary file to prevent corrupted partial downloads from being recognized
            File tempFile = new File(modelDir, fileName + ".tmp");

            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;

            try {
                URL url = new URL(modelUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
                }

                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                output = new FileOutputStream(tempFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                int lastProgress = -1;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        // Throttle callback updates
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            callback.onProgress(progress);
                        }
                    }
                }
                
                output.flush();
                output.close();
                input.close();

                Log.i(TAG, "Download complete. Verifying SHA-256...");
                
                // Integrity check
                if (verifySha256(tempFile, expectedSha256)) {
                    if (outputFile.exists()) {
                        outputFile.delete();
                    }
                    if (tempFile.renameTo(outputFile)) {
                        Log.i(TAG, "Model fully verified and secured at: " + outputFile.getAbsolutePath());
                        callback.onSuccess(outputFile);
                    } else {
                        throw new IOException("Failed to finalize verified model file.");
                    }
                } else {
                    tempFile.delete();
                    throw new SecurityException("Model integrity verification failed! SHA-256 mismatch.");
                }

            } catch (Exception e) {
                Log.e(TAG, "Model download failed.", e);
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                callback.onError(e);
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {}
                
                if (connection != null) connection.disconnect();
            }
        });
    }

    /**
     * Checks if the downloaded file matches the expected cryptographic hash.
     */
    private boolean verifySha256(File file, String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) {
            Log.w(TAG, "Skipping verification: no expected hash provided.");
            return true;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[1024 * 1024]; // 1MB chunk size
            int bytesCount; 

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }

            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            
            String actualHash = sb.toString();
            Log.d(TAG, "File SHA-256: " + actualHash);
            
            return actualHash.equalsIgnoreCase(expectedHash);
            
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.e(TAG, "Error calculating hash", e);
            return false;
        }
    }
}
