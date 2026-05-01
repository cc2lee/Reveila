package com.reveila.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;
import com.reveila.android.lib.BuildConfig;
import com.reveila.ai.LocalLlmServer;
import com.reveila.util.FileUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReveilaService extends Service {
    private static final String TAG = "ReveilaService";
    private static final String LOCK_FILE_NAME = "running.lock";
    private static final int MAX_RETRIES = 3;

    private ServiceManager serviceManager;
    private ExecutorService mainExecutor;
    private File lockFile;
    private File systemHome;
    private LocalLlmServer localLlmServer;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isStarting = new AtomicBoolean(false);
    private static final Reveila reveila = new Reveila();

    public static Reveila getReveilaInstance() {
        return reveila;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Unified thread pool for all background service tasks
        mainExecutor = Executors.newFixedThreadPool(3);
        serviceManager = new ServiceManager(this, "reveila_core", 1001, "Reveila Core Engine");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Essential for Android 14+ foreground compliance
        serviceManager.startForeground(this, "Reveila service is starting...");

        // Atomic guard to prevent multiple initialization threads
        if (isRunning.get() || !isStarting.compareAndSet(false, true)) {
            Log.i(TAG, "Service already running or starting. Ignoring request.");
            return START_STICKY;
        }

        final String customSystemHome = intent != null ? intent.getStringExtra("systemHome") : null;

        mainExecutor.execute(() -> {
            try {
                Log.i(TAG, "Starting background initialization...");
                initializeEnvironment(customSystemHome);

                Properties props = new Properties();
                props.setProperty("platform", "android");
                if (systemHome != null) {
                    props.setProperty("reveila.system.home", systemHome.getAbsolutePath());
                }

                PlatformAdapter platformAdapter = new AndroidPlatformAdapter(this, props);
                reveila.start(platformAdapter);

                isRunning.set(true);
                serviceManager.updateNotification(this, "Reveila is active");
                Log.i(TAG, "Reveila engine started successfully.");

                // Automatically attempt to start LLM server if binary exists
                startLocalLlmServer();

            } catch (Throwable e) {
                Log.e(TAG, "CRITICAL: Failed to start Reveila engine", e);
                stopSelf();
            } finally {
                isStarting.set(false);
            }
        });

        return START_STICKY;
    }

    private void initializeEnvironment(String customPath) throws IOException {
        String homePath = (customPath != null && !customPath.isBlank())
                ? customPath
                : new File(getFilesDir(), "reveila/system").getAbsolutePath();

        systemHome = new File(homePath);
        if (!systemHome.exists() && !systemHome.mkdirs()) {
            throw new IOException("Failed to create system home: " + homePath);
        }

        lockFile = new File(systemHome, LOCK_FILE_NAME);
        boolean uncleanShutdown = !lockFile.createNewFile();
        boolean shouldOverwrite = uncleanShutdown || BuildConfig.DEBUG;

        if (BuildConfig.DEBUG && uncleanShutdown) {
            deleteRecursively(new File(systemHome, "configs"));
        }

        new ReveilaSetup(this, homePath, shouldOverwrite);
        fetchRemoteProperties();
    }

    public void startLocalLlmServer() {
        mainExecutor.execute(() -> {
            try {
                File exeFile = new File(systemHome, "bin/android/llama-server");
                if (!exeFile.exists())
                    return;

                File modelDir = getExternalFilesDir("llms");
                if (modelDir == null)
                    modelDir = new File(systemHome, "downloads/llms");
                if (!modelDir.exists() && !modelDir.mkdirs())
                    return;

                Properties p = reveila.getSystemContext().getProperties();
                String modelName = p.getProperty("ai.llm.model.name", "default.gguf");
                File modelFile = new File(modelDir, modelName);

                // Housekeeping: delete old .gguf files to save storage
                File[] existingModels = modelDir.listFiles((d, name) -> name.endsWith(".gguf"));
                if (existingModels != null) {
                    for (File f : existingModels) {
                        if (!f.getAbsolutePath().equals(modelFile.getAbsolutePath())) {
                            f.delete();
                        }
                    }
                }

                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model: " + modelName);
                    String baseUrl = p.getProperty("download.base.url");
                    downloadModel(new URI(baseUrl + "/llms/" + modelName).toURL(), modelFile);
                }

                synchronized (this) {
                    if (localLlmServer == null || !localLlmServer.isRunning()) {
                        localLlmServer = new LocalLlmServer(exeFile, modelFile);
                        localLlmServer.start();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Local LLM startup sequence failed", e);
            }
        });
    }

    private void downloadModel(URL url, File modelFile) {
        FileUtil.download(url, modelFile, true, new FileUtil.DownloadCallback() {
            @Override
            public void onProgress(int progress) {
                serviceManager.updateNotification(ReveilaService.this, "Downloading model: " + progress + "%", 100,
                        progress, false);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Download error", e);
            }

            @Override
            public void onComplete(File downloaded) {
                serviceManager.updateNotification(ReveilaService.this, "Local LLM Ready");
            }
        });
    }

    @Override
    public void onDestroy() {
        isRunning.set(false);
        // Offload cleanup to ensure process handles are closed before service dies
        mainExecutor.execute(() -> {
            if (localLlmServer != null)
                localLlmServer.stop();
            reveila.shutdown();
            if (lockFile != null && lockFile.exists())
                lockFile.delete();
        });

        terminateExecutor(mainExecutor);
        Log.i(TAG, "Reveila Service Destroyed.");
        super.onDestroy();
    }

    private void terminateExecutor(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS))
                pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void fetchRemoteProperties() {
        String urlString = BuildConfig.REVEILA_PROPERTIES_URL;
        if (urlString == null || urlString.isBlank())
            return;
        File configFile = new File(systemHome, "configs/reveila.properties");
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URI(urlString).toURL();
            for (int i = 0; i < MAX_RETRIES; i++) {
                try {
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setConnectTimeout(5000);
                    urlConnection.setReadTimeout(5000);
                    if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                                FileOutputStream out = new FileOutputStream(configFile)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                            Log.i(TAG, "Successfully updated properties from: " + urlString);
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Attempt " + (i + 1) + " failed: " + e.getMessage());
                    Thread.sleep(1000);
                } finally {
                    if (urlConnection != null)
                        urlConnection.disconnect();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Configuration fetch error; using local configuration.", e);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children)
                    deleteRecursively(child);
            }
        }
        file.delete();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}