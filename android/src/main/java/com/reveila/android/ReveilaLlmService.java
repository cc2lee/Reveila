package com.reveila.android.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.reveila.android.ServiceManager;
import com.reveila.android.lib.FileUtil; // Assuming this utility exists based on your snippet

import java.io.File;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sovereign LLM Service
 * Manages the background lifecycle of the native llama-server binary.
 */
public class ReveilaLlmService extends Service {

    private static final String TAG = "ReveilaLlmService";
    private static final Object lock = new Object();
    
    private static volatile boolean isRunning = false;
    private static volatile boolean isStarting = false;
    
    private Process serverProcess;
    private ServiceManager serviceManager;
    private ExecutorService executor;

    // Static configuration passed from the main ReveilaService
    private static URL llmDownloadUrl = null;
    private static String modelName = "gemma-2b.gguf";
    private static volatile int downloadProgress = 0;

    public static boolean isRunning() { return isRunning; }
    public static int getDownloadProgress() { return downloadProgress; }
    public static void setModelName(String name) { modelName = name; }
    public static void setDownloadUrl(URL url) { llmDownloadUrl = url; }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        serviceManager = new ServiceManager(this);
        // Ensure this peer service is also promoted to Foreground to prevent LMK kills
        serviceManager.startForeground(this, "Reveila LLM Service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        serviceManager.startForeground(this, "Reveila LLM Service is active.");

        synchronized (lock) {
            if (isRunning || isStarting) {
                return START_STICKY;
            }
            isStarting = true;
        }

        executor.execute(this::initializeAndRunServer);
        return START_STICKY;
    }

    private void initializeAndRunServer() {
        try {
            File dir = new File(getFilesDir(), "reveila/system/downloads/llms");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new Exception("Could not create model directory.");
            }

            File modelFile = new File(dir, modelName);
            if (!modelFile.exists()) {
                Log.i(TAG, "Model not found. Initiating Sovereign download...");
                syncDownloadModel(modelFile);
            }

            String binaryPath = getFilesDir().getAbsolutePath() + "/reveila/system/bin/android/llama-server";
            File binFile = new File(binaryPath);
            
            if (!binFile.exists()) {
                throw new Exception("Native binary missing at: " + binaryPath);
            }
            binFile.setExecutable(true);

            // Configure the process for the Android environment
            ProcessBuilder pb = new ProcessBuilder(
                    binaryPath,
                    "--model", modelFile.getAbsolutePath(),
                    "--port", "8888",
                    "--threads", "4",
                    "--ctx-size", "2048",
                    "--host", "127.0.0.1" // Security: Bind to localhost only
            );

            // Redirect error stream to catch native crashes in Logcat
            pb.redirectErrorStream(true);

            Log.i(TAG, "Executing native llama-server...");
            serverProcess = pb.start();
            
            synchronized (lock) {
                isStarting = false;
                isRunning = true;
            }

            // This blocks the executor thread, acting as a watchdog for the process
            int exitCode = serverProcess.waitFor();
            Log.w(TAG, "Native llama-server exited with code: " + exitCode);

        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: LLM Service failure: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void syncDownloadModel(File destination) throws Exception {
        if (llmDownloadUrl == null) throw new Exception("Download URL not set.");
        
        // Using a synchronous wrapper around your FileUtil to keep the executor logic linear
        FileUtil.download(llmDownloadUrl, destination, true, new FileUtil.DownloadCallback() {
            @Override public void onProgress(int progress) { downloadProgress = progress; }
            @Override public void onError(Exception e) { Log.e(TAG, "Download failed", e); }
            @Override public void onComplete(File downloaded) { Log.i(TAG, "Model download verified."); }
        });

        // Simple poll for completion if FileUtil is internally async
        int timeout = 0;
        while (!destination.exists() && timeout < 600) { // 10 minute timeout
            Thread.sleep(1000);
            timeout++;
        }
    }

    private void cleanup() {
        synchronized (lock) {
            isRunning = false;
            isStarting = false;
        }
        if (serverProcess != null) {
            serverProcess.destroy();
            serverProcess = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Shutting down LLM Service...");
        
        if (executor != null) {
            executor.execute(this::cleanup);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}