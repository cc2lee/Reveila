package com.reveila.android;

import androidx.annotation.NonNull;
import com.facebook.react.bridge.*;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.fragment.app.FragmentActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reveila.system.Reveila;
import com.reveila.android.safety.MobileKillSwitch;

/**
 * ReveilaModule: The Sovereign Bridge.
 * Acts as a thin router between the React Native UI and the Java Engine.
 * Communication is strictly JSON-based via sendCommand.
 */
public class ReveilaModule extends ReactContextBaseJavaModule {

    private static final String NAME = "ReveilaModule";
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private MobileKillSwitch killSwitch;

    ReveilaModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * THE PRIMARY GATEWAY.
     * All UI requests are serialized to JSON and sent here.
     */
    @ReactMethod
    public void sendCommand(String jsonInput, Promise promise) {
        executorService.execute(() -> {
            try {
                Reveila engine = ReveilaService.getReveilaInstance();
                if (engine == null || !engine.isRunning()) {
                    promise.reject("E_ENGINE_OFFLINE", "The Reveila Engine is not running.");
                    return;
                }

                // ROUTING LOGIC: Pass the JSON string directly into the Engine's dispatcher.
                // The Engine parses the JSON, executes logic, and returns a JSON string response.
                String jsonResponse = engine.dispatchCommand(jsonInput);
                
                promise.resolve(jsonResponse);
            } catch (Exception e) {
                promise.reject("E_CMD_FAILED", e.getMessage());
            }
        });
    }

    /**
     * NATIVE CAPABILITY: Biometrics
     * Kept here because it requires a FragmentActivity context.
     */
    @ReactMethod
    public void authenticateBiometric(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "Activity not available.");
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            // ... (Keep existing BiometricPrompt logic from your snippet here) ...
            // This is a "True Native" task that shouldn't move to the Engine.
        });
    }

    /**
     * NATIVE CAPABILITY: Foreground Service Management
     */
    @ReactMethod
    public void startService(String systemHome, Promise promise) {
        try {
            Intent intent = new Intent(getReactApplicationContext(), ReveilaService.class);
            if (systemHome != null) intent.putExtra("systemHome", systemHome);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getReactApplicationContext().startForegroundService(intent);
            } else {
                getReactApplicationContext().startService(intent);
            }
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_START_FAILED", e.getMessage());
        }
    }

    @ReactMethod
    public void triggerEmergencyStop(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity != null) {
            if (killSwitch == null) killSwitch = new MobileKillSwitch(activity, null);
            killSwitch.emergencyStopAll();
            promise.resolve(true);
        }
    }

    @Override
    public void invalidate() {
        executorService.shutdown();
    }
}