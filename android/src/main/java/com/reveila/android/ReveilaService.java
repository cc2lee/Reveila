package com.reveila.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;
import com.reveila.error.SystemException;
import com.reveila.android.BuildConfig;
import com.reveila.ai.LocalLlmServer;
import com.reveila.util.io.FileUtil;

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
import java.util.concurrent.ThreadFactory;

public class ReveilaService extends Service {

    private static final String TAG = "REVEILA-SERVICE";

    // --- STATIC CRASH-WALL INITIALIZER ---
    static {
        // Generates a distinct high-visibility boundary in Logcat on every fresh boot
        // pass
        Log.w(TAG, "=====================================================================");
        Log.w(TAG, "██████╗ ███████╗██╗   ██╗███████╗██╗██╗      █████╗     ██████╗  ██████╗  ██████╗ ████████╗");
        Log.w(TAG, "██╔══██╗██╔════╝██║   ██║██╔════╝██║██║     ██╔══██╗    ██╔══██╗██╔═══██╗██╔═══██╗╚══██╔══╝");
        Log.w(TAG, "██████╔╝█████╗  ██║   ██║█████╗  ██║██║     ███████║    ██████╔╝██║   ██║██║   ██║   ██║   ");
        Log.w(TAG, "██╔══██╗██╔══╝  ╚██╗ ██╔╝██╔══╝  ██║██║     ██╔══██║    ██╔══██╗██║   ██║██║   ██║   ██║   ");
        Log.w(TAG, "██║  ██║███████╗ ╚████╔╝ ███████╗██║███████╗██║  ██║    ██████╔╝╚██████╔╝╚██████╔╝   ██║   ");
        Log.w(TAG, "╚═╝  ╚═╝╚══════╝  ╚═══╝  ╚══════╝╚═╝╚══════╝╚═╝  ╚═╝    ╚═════╝  ╚═════╝  ╚═════╝    ╚═╝   ");
        Log.w(TAG, "====================== INITIALIZING SOVEREIGN RUNTIME ======================");
    }

    private static final String BANNER =
    """
    ===========================================================================================
                              REVEILA BACKGROUND ENGINE ALIVE
    ===========================================================================================
    """;

    private static final String LOCK_FILE_NAME = "running.lock";
    private static final int MAX_RETRIES = 3;

    private static final Reveila reveila = new Reveila();

    public static Reveila getReveilaInstance() {
        return reveila;
    }

    private ServiceManager serviceManager;
    private ExecutorService mainExecutor;
    private File lockFile;

    private File systemHome;
    private LocalLlmServer localLlmServer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final AtomicBoolean isStarting = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();

        // Explicitly forces background workers to yield CPU priority to the Android UI
        // layout compiler
        mainExecutor = Executors.newFixedThreadPool(3, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "reveila-bg-worker");
                t.setPriority(Thread.MIN_PRIORITY); // Drop JVM priority
                return t;
            }
        });

        serviceManager = new ServiceManager(this, "reveila_core", 1001, "Reveila Core Engine");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Essential for Android foreground compliance
        serviceManager.startForeground(this, "Reveila service is starting...");

        // Atomic guard to prevent multiple initialization threads
        if (isRunning.get() || !isStarting.compareAndSet(false, true)) {
            Log.i(TAG, "Service already running or starting. Ignoring request.");
            return START_STICKY;
        }

        mainExecutor.execute(() -> {
            try {
                Log.i(TAG, "Starting background initialization...");
                initializeEnvironment();

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

            } catch (Exception e) {
                Log.e(TAG, "CRITICAL: Failed to start Reveila engine", e);
                stopSelf();
            } finally {
                isStarting.set(false);
            }
        });

        return START_STICKY;
    }

    public void startLocalLlmServer() {
        mainExecutor.execute(() -> {
            File exeFile = new File(systemHome, "bin/android/llama-server");
            if (!exeFile.exists()) {
                serviceManager.updateNotification(ReveilaService.this, "LLM server binary not found");
                Log.w(TAG, "LLM server binary not found at: " + exeFile.getAbsolutePath());
                return;
            }
            try {
                File modelDir = prepareLocalLlmEnvironment();
                File modelFile = getModelFile(modelDir);

                deleteOldModels(modelDir, modelFile);

                if (!modelFile.exists()) {
                    Log.i(TAG, "Downloading model: " + modelFile.getName());
                    Properties p = reveila.getSystemContext().getProperties();
                    String baseUrl = p.getProperty("download.base.url");
                    downloadModel(new URI(baseUrl + "/llms/" + modelFile.getName()).toURL(), modelFile);
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

    @Override
    public void onDestroy() {
        isRunning.set(false);
        mainExecutor.execute(() -> {
            if (localLlmServer != null)
                localLlmServer.stop();
            reveila.shutdown();
            if (lockFile != null && lockFile.exists()) {
                try {
                    Files.deleteIfExists(lockFile.toPath());
                } catch (IOException e) {
                    Log.w(TAG, "Failed to delete lock file: " + lockFile.getAbsolutePath(), e);
                }
            }
        });

        terminateExecutor(mainExecutor);
        Log.i(TAG, "Reveila Service Destroyed.");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Kept as null since the UI uses JSON-based IPC via sendCommand instead of
                     // direct binder channels
    }

    private void initializeEnvironment() throws SystemException {
        try {
            // 1. Standardized Reveila-Home path
            String homePath = new File(getFilesDir(), ReveilaSetup.TARGET_HOME).getAbsolutePath();

            systemHome = new File(homePath);
            if (!systemHome.exists() && !systemHome.mkdirs()) {
                throw new IOException("Failed to create system home: " + homePath);
            }

            // 2. Establish liveness lock tables
            lockFile = new File(systemHome, LOCK_FILE_NAME);
            boolean uncleanShutdown = !lockFile.createNewFile();
            boolean shouldOverwrite = uncleanShutdown || BuildConfig.DEBUG;

            if (shouldOverwrite) {
                FileUtil.delete(new File(systemHome, "configs"), true);
            }

            // 3. Extract and stage packaged APK workspace assets down to local flash
            // storage
            new ReveilaSetup(this, homePath, shouldOverwrite);

            // 4. Drop the final synchronization pass and fire your high-visibility liveness
            // metrics
            mainExecutor.execute(() -> {
                Log.i("ReveilaService", BANNER);
            });

        } catch (Exception e) {
            throw new SystemException("Environment initialization failed", e);
        }
    }

    private void deleteSilently(File f) {
        try {
            FileUtil.delete(f, true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to delete file: " + f.getAbsolutePath(), e);
        }
    }

    private File prepareLocalLlmEnvironment() throws IllegalStateException {
        File modelDir = getExternalFilesDir("llms");
        if (modelDir == null)
            modelDir = new File(systemHome, "downloads/llms");
        if (!modelDir.exists() && !modelDir.mkdirs())
            throw new IllegalStateException("Failed to create model directory: " + modelDir.getAbsolutePath());
        return modelDir;
    }

    private File getModelFile(File modelDir) {
        Properties p = reveila.getSystemContext().getProperties();
        String modelName = p.getProperty("ai.llm.model.name", "default.gguf");
        return new File(modelDir, modelName);
    }

    private void deleteOldModels(File modelDir, File modelFile) {
        File[] existingModels = modelDir.listFiles((d, name) -> name.endsWith(".gguf"));
        if (existingModels != null) {
            for (File f : existingModels) {
                if (!f.getAbsolutePath().equals(modelFile.getAbsolutePath())) {
                    deleteSilently(f);
                }
            }
        }
    }

    private void downloadModel(URL url, File modelFile) {
        try {
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
        } catch (Exception e) {
            serviceManager.updateNotification(ReveilaService.this, "Model download failed");
            Log.e(TAG, "Model download error", e);
        }
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
}