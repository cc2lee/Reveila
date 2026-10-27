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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import com.reveila.system.Properties;
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
    private Process llmProcess;
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

                // Automatically start Vault document directory scanner
                startVaultMonitor();

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
            try {
                if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                    Log.i(TAG, "Local LLM server is already responding on port 8080.");
                    serviceManager.updateNotification(ReveilaService.this, "Local LLM Online (Port 8080)");
                    return;
                }

                File exeFile = findLlamaServerExecutable();
                if (exeFile == null) {
                    serviceManager.updateNotification(ReveilaService.this, "LLM server binary not found");
                    Log.w(TAG, "LLM server binary not found in systemHome, nativeLibraryDir, or /data/local/tmp");
                    return;
                }

                File modelDir = prepareLocalLlmEnvironment();
                File modelFile = findModelFile(modelDir);

                if (modelFile == null || !modelFile.exists()) {
                    Properties p = reveila.getSystemContext() != null ? reveila.getSystemContext().getProperties() : null;
                    String baseUrl = p != null ? p.getProperty("download.base.url") : null;
                    String targetName = p != null ? p.getProperty("ai.llm.model.name", "qwen2.5-0.5b-instruct-q4_k_m.gguf") : "qwen2.5-0.5b-instruct-q4_k_m.gguf";
                    File downloadTarget = new File(modelDir, targetName);
                    if (baseUrl != null && !baseUrl.isBlank()) {
                        Log.i(TAG, "Attempting to download model: " + targetName);
                        downloadModel(new URI(baseUrl + "/llms/" + targetName).toURL(), downloadTarget);
                        if (downloadTarget.exists() && downloadTarget.length() > 0) {
                            modelFile = downloadTarget;
                        }
                    }
                }

                if (modelFile == null || !modelFile.exists()) {
                    serviceManager.updateNotification(ReveilaService.this, "Model weights missing (.gguf)");
                    Log.w(TAG, "No valid LLM model (.gguf) found on device.");
                    return;
                }

                synchronized (this) {
                    if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                        return;
                    }

                    if (llmProcess != null && llmProcess.isAlive()) {
                        llmProcess.destroy();
                    }

                    List<String> cmd = new ArrayList<>();
                    cmd.add(exeFile.getAbsolutePath());
                    cmd.add("-m");
                    cmd.add(modelFile.getAbsolutePath());
                    cmd.add("--host");
                    cmd.add("127.0.0.1");
                    cmd.add("--port");
                    cmd.add("8080");
                    cmd.add("-c");
                    cmd.add("2048");
                    cmd.add("--parallel");
                    cmd.add("1");
                    cmd.add("-n");
                    cmd.add("256");
                    cmd.add("-t");
                    cmd.add("4");

                    Log.i(TAG, "Launching native llama-server: " + String.join(" ", cmd));
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    llmProcess = pb.start();

                    Executors.newSingleThreadExecutor().execute(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(llmProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                Log.d("LLAMA-SERVER", line);
                            }
                        } catch (Exception ignored) {}
                    });

                    // Poll for server readiness up to 10 seconds
                    boolean ready = false;
                    for (int i = 0; i < 20; i++) {
                        if (isLlmServerHealthy("http://127.0.0.1:8080/health")) {
                            ready = true;
                            break;
                        }
                        Thread.sleep(500);
                    }

                    if (ready) {
                        serviceManager.updateNotification(ReveilaService.this, "Local LLM Online (" + modelFile.getName() + ")");
                        Log.i(TAG, "Native llama-server verified alive and healthy on 127.0.0.1:8080!");
                    } else {
                        serviceManager.updateNotification(ReveilaService.this, "Local LLM starting...");
                        Log.w(TAG, "Native llama-server process started, waiting for first completion request.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Local LLM startup sequence failed", e);
            }
        });
    }

    private File findLlamaServerExecutable() {
        // 1. Extracted native library directory (SELinux allows execute_no_trans on apk_data_file)
        File exe1 = new File(getApplicationInfo().nativeLibraryDir, "libllama-server.so");
        if (exe1.exists()) {
            exe1.setReadable(true, false);
            exe1.setExecutable(true, false);
            if (exe1.canExecute()) {
                Log.i(TAG, "Located llama-server at nativeLibraryDir: " + exe1.getAbsolutePath());
                return exe1;
            }
        }

        // 2. /data/local/tmp development path
        File exe2 = new File("/data/local/tmp/llama-server");
        if (exe2.exists()) {
            exe2.setReadable(true, false);
            exe2.setExecutable(true, false);
            if (exe2.canExecute()) {
                Log.i(TAG, "Located llama-server at /data/local/tmp: " + exe2.getAbsolutePath());
                return exe2;
            }
        }

        // 3. Packaged asset path staged to systemHome
        File exe3 = new File(systemHome, "bin/android/llama-server");
        if (exe3.exists()) {
            exe3.setReadable(true, false);
            exe3.setExecutable(true, false);
            if (exe3.canExecute()) {
                Log.i(TAG, "Located llama-server at systemHome: " + exe3.getAbsolutePath());
                return exe3;
            }
        }

        return null;
    }

    private File findModelFile(File modelDir) {
        Properties p = reveila.getSystemContext() != null ? reveila.getSystemContext().getProperties() : null;
        String configuredModelName = p != null ? p.getProperty("ai.llm.model.name", "qwen2.5-0.5b-instruct-q4_k_m.gguf") : "qwen2.5-0.5b-instruct-q4_k_m.gguf";

        // 1. Check configured name in modelDir
        File f1 = new File(modelDir, configuredModelName);
        if (f1.exists() && f1.length() > 0) return f1;

        // 2. Check any .gguf in modelDir
        File[] dirModels = modelDir.listFiles((d, name) -> name.endsWith(".gguf"));
        if (dirModels != null && dirModels.length > 0) return dirModels[0];

        // 3. Check /data/local/tmp for configured model
        File f2 = new File("/data/local/tmp", configuredModelName);
        if (f2.exists() && f2.length() > 0) return f2;

        // 4. Check /data/local/tmp for qwen default
        File f3 = new File("/data/local/tmp/qwen2.5-0.5b-instruct-q4_k_m.gguf");
        if (f3.exists() && f3.length() > 0) return f3;

        // 5. Check any .gguf in /data/local/tmp
        File[] tmpModels = new File("/data/local/tmp").listFiles((d, name) -> name.endsWith(".gguf"));
        if (tmpModels != null && tmpModels.length > 0) return tmpModels[0];

        return null;
    }

    private boolean isLlmServerHealthy(String healthUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URI(healthUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void startVaultMonitor() {
        mainExecutor.execute(() -> {
            try {
                ModelSettings settings = new ModelSettings(this);
                String vaultUriStr = settings.getVaultUri();
                if (vaultUriStr == null || vaultUriStr.isBlank()) {
                    File defaultVault = new File(getExternalFilesDir(null), "ReveilaVault");
                    if (!defaultVault.exists()) {
                        defaultVault.mkdirs();
                    }
                    vaultUriStr = defaultVault.getAbsolutePath();
                    settings.setVaultUri(vaultUriStr);
                }

                VaultScanner scanner = new VaultScanner(this);
                com.reveila.android.data.VaultRepository repo = new com.reveila.android.data.RoomRepository(
                        "CONCEPT", 
                        com.reveila.android.db.ReveilaDatabase.getDatabase(this).genericDao()
                );
                android.net.Uri uri = android.net.Uri.parse(vaultUriStr);
                ScanResult result = scanner.performScan(uri, repo, settings.getFocusKeywords(), progress -> {
                    Log.d(TAG, "Vault scanning progress: " + (int)(progress * 100) + "%");
                });
                Log.i(TAG, "Initial Vault scan complete. Scanned: " + result.getFilesScanned() + ", Entities: " + result.getEntitiesFound());
            } catch (Exception e) {
                Log.w(TAG, "Vault monitor scan failed: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDestroy() {
        isRunning.set(false);
        mainExecutor.execute(() -> {
            if (llmProcess != null) {
                try {
                    Log.i(TAG, "Terminating llama-server process...");
                    llmProcess.destroy();
                    if (!llmProcess.waitFor(2, TimeUnit.SECONDS)) {
                        llmProcess.destroyForcibly();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to terminate LLM process cleanly", e);
                }
                llmProcess = null;
            }
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
        } catch (Exception e) {
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
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            int contentLength = conn.getContentLength();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[8192];
                int len;
                long total = 0;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    total += len;
                    if (contentLength > 0) {
                        int progress = (int) ((total * 100) / contentLength);
                        serviceManager.updateNotification(ReveilaService.this, "Downloading model: " + progress + "%", 100,
                                progress, false);
                    }
                }
                serviceManager.updateNotification(ReveilaService.this, "Local LLM Ready");
            }
        } catch (Exception e) {
            serviceManager.updateNotification(ReveilaService.this, "Model download failed");
            Log.e(TAG, "Model download error", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
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