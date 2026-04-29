package com.reveila.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.os.Build;
import androidx.annotation.Nullable;

import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;
import com.reveila.android.lib.BuildConfig;
import com.reveila.android.AndroidPlatformAdapter;
import com.reveila.android.ServiceManager;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReveilaService extends Service {

    private ServiceManager serviceManager;
    private ExecutorService executor;
    private File lockFile;
    private File systemHome;
    private boolean isLocalProperties = true;
    private Object lock = new Object();

    private static volatile boolean isReveilaRunning = false;
    private static volatile boolean isReveilaStarting = false;
    
    private static final int MAX_RETRIES = 3;
    private static final String TAG = "ReveilaService";
    private static final String LOCK_FILE_NAME = "running.lock";
    private static final Reveila reveila = new Reveila();
    
    public boolean isLocalProperties() {
        return isLocalProperties;
    }

    public static Reveila getReveilaInstance() {
        return reveila;
    }

    public static boolean isRunning() {
        return isReveilaRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        serviceManager = new ServiceManager(this, "reveila_core", 1001, "Reveila Core Engine");
        serviceManager.startForeground(this, "Reveila service is initializing...");
    }

    public void setGlobalHome(String homePath) {
        this.systemHome = new File(homePath);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ALWAYS call startForeground immediately on every onStartCommand call
        // to satisfy Android's foreground service requirements and prevent crashes.
        serviceManager.startForeground(this, "Reveila is running.");

        // Prevent starting the engine logic multiple times
        synchronized (lock) {
            if (isReveilaRunning || isReveilaStarting) {
                return START_STICKY;
            } else {
                isReveilaStarting = true;
            }
        }

        final String customSystemHome = intent != null ? intent.getStringExtra("systemHome") : null;

        // Offload all initialization, including file I/O, to a background thread
        // to prevent blocking the main thread and causing ANRs.

        executor.execute(() -> {
            try {
                Log.i(TAG, "Starting background initialization...");

                // Resolve SYSTEM_HOME
                String homePath;
                if (customSystemHome != null && !customSystemHome.isBlank()) {
                    homePath = customSystemHome;
                    Log.i(TAG, "Using custom system home: " + homePath);
                } else {
                    homePath = new File(getFilesDir(), "reveila/system").getAbsolutePath();
                    Log.i(TAG, "Using default system home: " + homePath);
                }

                setGlobalHome(homePath);
                if (!systemHome.exists() && !systemHome.mkdirs()) {
                    Log.e(TAG, "Failed to create system home directory: " + homePath);
                    return; // cannot continue without a valid system home
                }

                // Continue if 'systemHome' is not null
                lockFile = new File(systemHome, LOCK_FILE_NAME);

                // Check for the lock file to determine if the last shutdown was clean.
                final boolean wasUncleanShutdown = !lockFile.createNewFile();
                if (wasUncleanShutdown) {
                    Log.w(TAG, "Unclean shutdown detected. Assets will be overwritten.");
                } else {
                    Log.i(TAG, "Clean startup detected. Assets will not be overwritten.");
                }

                // ADR 0003: In Debug mode, we always overwrite assets to ensure
                // IDE changes (like priority fixes) are applied immediately.
                boolean shouldOverwrite = wasUncleanShutdown || BuildConfig.DEBUG;
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "Debug mode detected. Forcing asset overwrite.");
                    // Delete the configs directory to prevent zombie config files from lingering
                    File configsDir = new File(systemHome, "configs");
                    if (configsDir.exists()) {
                        deleteRecursively(configsDir);
                    }
                }

                // Copy assets, overwriting only if the last shutdown was unclean or in debug
                // mode.
                new ReveilaSetup(this, homePath, shouldOverwrite);

                // Attempt to fetch remote properties if configured
                fetchRemoteProperties();

                Properties props = new Properties();
                props.setProperty("platform", "android");
                if (customSystemHome != null) {
                    props.setProperty(com.reveila.system.Constants.SYSTEM_HOME, customSystemHome);
                }

                PlatformAdapter platformAdapter = new AndroidPlatformAdapter(this, props);
                reveila.start(platformAdapter);
                isReveilaRunning = true;
                serviceManager.updateNotification(this, "Reveila is running");
                Log.i(TAG, "Reveila service started successfully.");

                startLlmService(platformAdapter.getProperties());

            } catch (Throwable e) {
                Log.e(TAG, "CRITICAL: Failed to start Reveila engine", e);
            } finally {
                isReveilaStarting = false;
            }
        });

        // If the system kills the service, it will be automatically restarted.
        return START_STICKY;
    }

    private void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private void fetchRemoteProperties() {
        String urlString = BuildConfig.REVEILA_PROPERTIES_URL;
        if (urlString == null || urlString.isEmpty())
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
                            isLocalProperties = false;
                            Log.i(TAG, "Successfully updated properties from: " + urlString);
                            return; // SUCCESS EXIT
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            // Peer shutdown remains on main thread for speed
            Intent stopLlmIntent = new Intent(this, ReveilaLlmService.class);
            stopService(stopLlmIntent);

            if (executor != null) {
                executor.execute(() -> {
                    try {
                        if (reveila != null)
                            reveila.shutdown();
                        if (lockFile != null && lockFile.exists())
                            lockFile.delete();
                    } catch (Exception e) {
                        Log.e(TAG, "Cleanup task failed", e);
                    }
                });
                executor.shutdown();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            if (executor != null)
                executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            // Resetting global state must happen last
            isReveilaRunning = false;
            isReveilaStarting = false;
            Log.i(TAG, "Reveila service stopped.");
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    private void startLlmService(Properties p) {
        if (ReveilaLlmService.isRunning()) {
            return;
        }

        try {
            String baseUrl = p.getProperty("download.base.url");
            String modelName = p.getProperty("ai.llm.model.name");
            URL url = new URI(baseUrl + "/llms/" + modelName).toURL();
            ReveilaLlmService.setModelName(modelName);
            ReveilaLlmService.setDownloadUrl(url);

            Intent intent = new Intent(this, ReveilaLlmService.class);
            // Ensure the LLM service is running in the background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Reveila LLM Service", e);
        }
    }
}