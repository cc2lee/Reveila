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
    private static final Subject subject = new Subject();
    static {
        RolePrincipal principal = new RolePrincipal("ui-client");
        subject.getPrincipals().add(principal);
    }
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    
    private MobileKillSwitch killSwitch; 
    private final SovereignMemoryManager memoryManager;
    
    // Defer the concrete assignment to bypass the synchronous constructor gate
    private VaultRepository repository;
    
    private final ReactApplicationContext context;

    ReveilaModule(ReactApplicationContext context) {
        super(context);
        this.context = context;
        this.memoryManager = new SovereignMemoryManager(context);
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

                // 1. Parse the JSON Envelope via standard shared mapper specifications
                ObjectMapper mapper = EntityMapper.getObjectMapper();
                Map<String, Object> payload = mapper.readValue(jsonInput, new TypeReference<Map<String, Object>>() {});

                String component = (String) payload.get("component");
                String method = (String) payload.get("method");

                // Extract parameters safely as an Object array
                List<Object> paramList = (List<Object>) payload.get("params");
                Object[] params = paramList != null ? paramList.toArray() : new Object[0];

                // 2. Execute via the Engine's primary entry point
                Object result = engine.invoke(
                        component,
                        method,
                        params,
                        "127.0.0.1", // Localhost for Personal Edition
                        subject 
                );

                // 3. Serialize the result back to JSON for Expo layout hydration
                String jsonResponse = mapper.writeValueAsString(result);
                promise.resolve(jsonResponse);

            } catch (Exception e) {
                Log.e(NAME, "Invoke execution path failed", e);
                promise.reject("E_INVOKE_FAILED", e.getMessage());
            }
        });
    }

    /**
     * FIXED: Vault operations now split context handling properly.
     * Biometrics verify on the UI Thread, and DB commits execute on the background executor pool.
     */
    @ReactMethod
    public void saveSensitiveData(String data) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            Log.e(NAME, "Cannot write sensitive data: FragmentActivity context is offline.");
            return;
        }
        
        // Force biometric prompt interaction to display safely from the Main Thread Looper
        new Handler(Looper.getMainLooper()).post(() -> {
            memoryManager.secureWriteOperation(activity, "Save Vault Key", () -> {
                // If biometrics clear the gate, offload the SQLite disk write to the background pool
                executorService.execute(() -> {
                    getRepository().saveSecret(data);
                });
            });
        });
    }

    /**
     * NATIVE CAPABILITY: Biometrics
     * Executed directly on the Main Looper via the local Activity handle window context.
     */
    @ReactMethod
    public void authenticateBiometric(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "FragmentActivity contextual anchor not available.");
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                BiometricManager biometricManager = BiometricManager.from(activity);
                int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                
                if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    promise.reject("E_BIOMETRIC_UNAVAILABLE", "Biometric authentication systems are unavailable on this hardware configuration.");
                    return;
                }

                Executor executor = ContextCompat.getMainExecutor(activity);
                BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        promise.reject("E_AUTH_ERROR", errString.toString());
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        promise.resolve(true);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                    }
                });

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Sovereign Security Authorization")
                        .setSubtitle("Authenticate identity credentials to unlock engine data pools")
                        .setAllowedAuthenticators(authenticators)
                        .setConfirmationRequired(false)
                        .build();

                biometricPrompt.authenticate(promptInfo);

            } catch (Exception e) {
                promise.reject("E_BIOMETRIC_PROMPT_FAILED", e.getMessage());
            }
        });
    }

    /**
     * NATIVE CAPABILITY: Foreground Service Management
     */
    @ReactMethod
    public void startService(Promise promise) {
        try {
            String baseFilesDirectory = getReactApplicationContext().getFilesDir().getAbsolutePath();
            String calculatedSystemHome = baseFilesDirectory.endsWith("/") 
                ? baseFilesDirectory + ReveilaSetup.TARGET_HOME 
                : baseFilesDirectory + "/" + ReveilaSetup.TARGET_HOME;

            Intent intent = new Intent(getReactApplicationContext(), ReveilaService.class);
            
            // 1. Offload the OS-level daemon startup completely away from the bridge state managers
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getReactApplicationContext().startForegroundService(intent);
            } else {
                getReactApplicationContext().startService(intent);
            }
            
            // 2. Resolve the JS promise safely on your separate background worker thread pool 
            // to avoid clipping the Fabric InputDispatcher rendering track!
            executorService.execute(() -> {
                promise.resolve(calculatedSystemHome);
            });

        } catch (Exception e) {
            Log.e(NAME, "Failed to switch background service infrastructure to active state", e);
            promise.reject("E_START_FAILED", e.getMessage());
        }
    }

    @ReactMethod
    public void triggerEmergencyStop(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "Emergency sequence skipped: Activity container is null.");
            return;
        }
        
        if (killSwitch == null) {
            killSwitch = new MobileKillSwitch(activity, null);
        }
        killSwitch.emergencyStopAll();
        promise.resolve(true);
    }

    /**
     * LIVENESS PROBE: Direct Static Read.
     */
    @ReactMethod
    public void getEngineStatus(Promise promise) {
        try {
            Reveila engine = ReveilaService.getReveilaInstance();
            WritableMap map = Arguments.createMap();
            
            if (engine != null && engine.isRunning()) {
                map.putBoolean("initialized", true);
                map.putString("status", "ONLINE");
            } else {
                map.putBoolean("initialized", false);
                map.putString("status", "INITIALIZING_CORE");
            }
            
            promise.resolve(map);
        } catch (Exception e) {
            promise.reject("E_STATIC_PROBE_FAILED", e.getMessage());
        }
    }

    @Override
    public void invalidate() {
        executorService.shutdown();
        super.invalidate();
    }

    /**
     * Thread-safe lazy initializer for your local secure repository matrix.
     */
    private synchronized VaultRepository getRepository() {
        if (this.repository == null) {
            this.repository = new RoomRepository("CONCEPT", ReveilaDatabase.getDatabase(this.context).genericDao());
        }
        return this.repository;
    }
}