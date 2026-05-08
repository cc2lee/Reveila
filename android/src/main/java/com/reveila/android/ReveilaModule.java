package com.reveila.android;

import androidx.annotation.NonNull;
import com.facebook.react.bridge.*;
import android.util.Log;
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
import java.util.Map;
import java.util.List;

import com.reveila.system.Reveila;
import com.reveila.android.safety.MobileKillSwitch;
import com.reveila.android.db.ReveilaDatabase;
import com.reveila.android.data.VaultRepository;
import com.reveila.android.data.RoomRepository;

import javax.security.auth.Subject;
import com.reveila.system.RolePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.reveila.data.EntityMapper;

/**
 * ReveilaModule: The Sovereign Bridge.
 * Acts as a thin router between the React Native UI and the Java Engine.
 * Communication is strictly JSON-based via sendCommand.
 */
public class ReveilaModule extends ReactContextBaseJavaModule {

    private static final String NAME = "ReveilaModule";
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private MobileKillSwitch killSwitch;
    private final SovereignMemoryManager memoryManager;
    private final VaultRepository repository;
    private static final Subject subject = new Subject();
    static {
        RolePrincipal principal = new RolePrincipal("ui-client");
        subject.getPrincipals().add(principal);
    }

    ReveilaModule(ReactApplicationContext context) {
        super(context);
        this.memoryManager = new SovereignMemoryManager(context);
        // Initialize your repository here
        this.repository = new RoomRepository("CONCEPT", ReveilaDatabase.getDatabase(context).genericDao());
    }

    @ReactMethod
    public void saveSensitiveData(String data) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        memoryManager.secureWriteOperation(activity, "Save Vault Key", () -> {
            // This code ONLY runs if fingerprint/PIN succeeds
            repository.saveSecret(data);
        });
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

                // 1. Parse the JSON Envelope
                ObjectMapper mapper = EntityMapper.getObjectMapper();
                Map<String, Object> payload = mapper.readValue(jsonInput, new TypeReference<Map<String, Object>>() {
                });

                String component = (String) payload.get("component");
                String method = (String) payload.get("method");

                // Extract params as an Object array
                List<Object> paramList = (List<Object>) payload.get("params");
                Object[] params = paramList != null ? paramList.toArray() : new Object[0];

                // 2. Execute via the Engine's primary entry point
                // CallerIP and Subject are handled here for the "Sovereign" audit trail
                Object result = engine.invoke(
                        component,
                        method,
                        params,
                        "127.0.0.1", // Localhost for Personal Edition
                        subject // use the same local "ui-client" subject for now
                );

                // 3. Serialize the result back to JSON for Expo
                String jsonResponse = mapper.writeValueAsString(result);
                promise.resolve(jsonResponse);

            } catch (Exception e) {
                Log.e("ReveilaModule", "Invoke failed", e);
                promise.reject("E_INVOKE_FAILED", e.getMessage());
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
            if (systemHome != null)
                intent.putExtra("systemHome", systemHome);

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
            if (killSwitch == null)
                killSwitch = new MobileKillSwitch(activity, null);
            killSwitch.emergencyStopAll();
            promise.resolve(true);
        }
    }

    @Override
    public void invalidate() {
        executorService.shutdown();
    }
}