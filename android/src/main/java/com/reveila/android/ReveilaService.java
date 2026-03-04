/**
 * ReveilaService is an Android {@link Service} responsible for managing the lifecycle of the Reveila engine.
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Initializes and starts the Reveila engine in a background thread to avoid blocking the main thread.</li>
 *   <li>Manages a lock file to detect unclean shutdowns and control asset overwriting behavior.</li>
 *   <li>Runs as a foreground service to ensure reliability and prevent unexpected termination.</li>
 *   <li>Provides static methods to check running status and access the Reveila instance.</li>
 *   <li>Handles clean shutdown by deleting the lock file and stopping the engine.</li>
 * </ul>
 * <p>
 * Usage:
 * <ul>
 *   <li>Start the service via {@code Context.startService(Intent)}.</li>
 *   <li>Service restarts automatically if killed by the system (START_STICKY).</li>
 * </ul>
 * <p>
 * Code example for invoking a method on the Reveila instance:
 * <pre>
 *      Reveila reveilaInstance = ReveilaService.getReveilaInstance();
 *      if (!ReveilaService.isRunning() || reveilaInstance == null) {
 *          return; // The Reveila service is not yet available.
 *      }
 *      Object[] methodArguments = null;
 *      Object result = reveilaInstance.invoke("componentName", "methodName", methodArguments);
 * </pre>
 * <p>
 * Note: Binding is not supported; {@link #onBind(Intent)} returns null.
 * </p>
 */
package com.reveila.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.io.File;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.annotation.Nullable;

import com.reveila.system.Reveila;
import com.reveila.system.PlatformAdapter;

import com.reveila.android.AndroidPlatformAdapter;
import com.reveila.android.ServiceManager;

public class ReveilaService extends Service {

    private static Reveila reveila;
    private ServiceManager serviceManager;
    private static volatile boolean isReveilaRunning = false;
    private static final String LOCK_FILE_NAME = "running.lock";
    // Use a single-threaded executor to serialize all service startup and shutdown
    // operations.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private File lockFile;

    public static Reveila getReveilaInstance() {
        return reveila;
    }

    public static boolean isRunning() {
        return isReveilaRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceManager = new ServiceManager(this);
        // Start foreground as early as possible to avoid "did not call startForeground" crash.
        serviceManager.startForeground(this, "Reveila service is initializing...");
        reveila = new Reveila();
        isReveilaRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ALWAYS call startForeground immediately on every onStartCommand call
        // to satisfy Android's foreground service requirements and prevent crashes.
        serviceManager.startForeground(this, "Reveila is running.");

        // Prevent starting the engine logic multiple times
        if (!ReveilaService.isReveilaRunning) {
            final String customSystemHome = intent != null ? intent.getStringExtra("systemHome") : null;
            
            // Offload all initialization, including file I/O, to a background thread
            // to prevent blocking the main thread and causing ANRs.
            executor.execute(() -> {
                try {
                    Log.i("ReveilaService", "Starting engine background initialization...");
                    
                    // Resolve SYSTEM_HOME
                    String homePath;
                    if (customSystemHome != null) {
                        homePath = customSystemHome;
                        Log.i("ReveilaService", "Using custom system home: " + homePath);
                    } else {
                        homePath = AndroidPlatformAdapter.getSystemHome(this);
                        Log.i("ReveilaService", "Using default system home: " + homePath);
                    }

                    File systemHome = new File(homePath);
                    if (!systemHome.exists() && !systemHome.mkdirs()) {
                         Log.e("ReveilaService", "Failed to create system home directory: " + homePath);
                    }
                    lockFile = new File(systemHome, LOCK_FILE_NAME);

                    // Check for the lock file to determine if the last shutdown was clean.
                    final boolean wasUncleanShutdown = lockFile.exists();
                    if (wasUncleanShutdown) {
                        Log.w("ReveilaService", "Unclean shutdown detected. Assets will be overwritten.");
                    } else {
                        Log.i("ReveilaService", "Clean startup detected. Assets will not be overwritten.");
                    }

                    // Create the lock file to indicate the service is now running.
                    lockFile.createNewFile();

                    // Copy assets, overwriting only if the last shutdown was unclean.
                    // Note: If using customSystemHome, we might still want to seed it from assets
                    // if it's empty, but usually a custom path implies external management.
                    new ReveilaSetup(this, wasUncleanShutdown);

                    Properties props = new Properties();
                    if (customSystemHome != null) {
                        props.setProperty(com.reveila.system.Constants.SYSTEM_HOME, customSystemHome);
                    }

                    PlatformAdapter platformAdapter = new AndroidPlatformAdapter(this, props);
                    reveila.start(platformAdapter);

                    ReveilaService.isReveilaRunning = true;
                    Log.i("ReveilaService", "Reveila service started successfully.");
                } catch (Throwable e) {
                    Log.e("ReveilaService", "CRITICAL: Failed to start Reveila engine", e);
                }
            });
        }

        // If the system kills the service, it will be automatically restarted.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.execute(() -> {
            if (reveila != null) {
                reveila.shutdown();
            }
            ReveilaService.isReveilaRunning = false;

            // On clean shutdown, delete the lock file.
            if (lockFile != null && lockFile.exists()) {
                if (lockFile.delete()) {
                    Log.i("ReveilaService", "Clean shutdown. Lock file deleted.");
                } else {
                    Log.w("ReveilaService", "Failed to delete lock file on shutdown.");
                }
            }
            Log.i("ReveilaService", "Reveila service stopped.");
        });
        executor.shutdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }
}