package com.reveila.android.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.io.File;
import java.io.IOException;

/**
 * Sovereign LLM Service
 * Manages the background lifecycle of the embedded llama-server binary.
 * Need to use the Android NDK to compile llama.cpp for the arm64-v8a
 * architecture.
 */
public class ReveilaLlmService extends Service {
    private static final String TAG = "ReveilaLlmService";
    private Process serverProcess;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startLlmServer();
        return START_STICKY;
    }

    private void downloadModel() {
        ReveilaModelDownloader.ensureModelExists(this, new ReveilaModelDownloader.DownloadCallback() {
            @Override
            public void onComplete(File modelFile) {
                // Now start the LlmService we wrote earlier
                Intent serviceIntent = new Intent(context, ReveilaLlmService.class);
                serviceIntent.putExtra("model_path", modelFile.getAbsolutePath());
                startService(serviceIntent);
            }

            @Override
            public void onProgress(int progress) {
                // Optional: Update your "Control Center" UI with a progress bar
            }

            @Override
            public void onError(Exception e) {
                // Log the failure to your Sovereign Audit Ledger
            }
        });
    }

    private void startLlmServer() {
        if (serverProcess != null)
            return;

        new Thread(() -> {
            try {
                // TODO: Check if the model file exists before trying to start the server
                File modelFile = new File(getFilesDir(), "models/gemma-2b.gguf");
                if (!modelFile.exists()) {
                    Log.error(TAG, "Model file not found.");
                    return;
                }

                // path to the executable binary in app's internal storage
                String binaryPath = getFilesDir().getAbsolutePath() + "/bin/llama-server";
                String modelPath = modelFile.getAbsolutePath();

                ProcessBuilder pb = new ProcessBuilder(
                        binaryPath,
                        "--model", modelPath,
                        "--port", "8080",
                        "--threads", "4",
                        "--ctx-size", "2048");

                // Ensure the binary is executable
                new File(binaryPath).setExecutable(true);

                Log.info(TAG, "Starting Sovereign Node on localhost:8080...");
                serverProcess = pb.start();

                // Handle process termination/logs here...
                serverProcess.waitFor();
            } catch (IOException | InterruptedException e) {
                Log.error(TAG, "Failed to start Sovereign Node: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        if (serverProcess != null) {
            serverProcess.destroy();
            Log.info(TAG, "Sovereign Node Stopped.");
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Local-only background service
    }
}