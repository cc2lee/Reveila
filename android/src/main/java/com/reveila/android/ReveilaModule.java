package com.reveila.android;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;

import android.content.Intent;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.reveila.system.Reveila;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.reveila.android.safety.BiometricSafetyGuard;
import com.reveila.android.safety.MobileKillSwitch;
import com.reveila.core.safety.AgentSafetyCommand;
import com.reveila.system.RolePrincipal;
import com.reveila.system.PluginPrincipal;
import com.reveila.system.Constants;
import javax.security.auth.Subject;

import androidx.fragment.app.FragmentActivity;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import androidx.biometric.BiometricManager;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

import com.reveila.android.service.VaultScannerWorker;
import androidx.work.PeriodicWorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import java.util.concurrent.TimeUnit;
import android.net.Uri;

public class ReveilaModule extends ReactContextBaseJavaModule {

    private static final String NAME = "ReveilaModule";
    private MobileKillSwitch killSwitch;
    private Subject currentSubject;
    private long lastActivityTimestamp;
    private long sessionTimeoutMs = 30 * 60 * 1000; // Default 30 mins
    
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    // It's a best practice to run network operations on a dedicated background thread pool.
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    ReveilaModule(ReactApplicationContext context) {
        super(context);
        schedulePeriodicVaultScan();
    }

    private void schedulePeriodicVaultScan() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Stay Sovereign/Offline
                .setRequiresBatteryNotLow(true)                // Don't kill the phone
                .setRequiresDeviceIdle(true)                   // Wait for "Quiet Time"
                .build();

        PeriodicWorkRequest scanRequest =
                new PeriodicWorkRequest.Builder(VaultScannerWorker.class, 1, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .setInitialDelay(15, TimeUnit.MINUTES)
                        .addTag("VAULT_SCAN")
                        .build();

        WorkManager.getInstance(getReactApplicationContext())
                .enqueueUniquePeriodicWork(
                        "PeriodicVaultScan",
                        ExistingPeriodicWorkPolicy.KEEP,
                        scanRequest
                );
    }

    @ReactMethod
    public void triggerVaultScan(Promise promise) {
        try {
            OneTimeWorkRequest scanRequest =
                    new OneTimeWorkRequest.Builder(VaultScannerWorker.class)
                            .addTag("MANUAL_VAULT_SCAN")
                            .build();

            WorkManager.getInstance(getReactApplicationContext())
                    .enqueueUniqueWork(
                            "ManualVaultScan",
                            ExistingWorkPolicy.REPLACE,
                            scanRequest
                    );
            
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_SCAN_FAILED", e.getMessage());
        }
    }

    @ReactMethod
    public void triggerEmergencyStop(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("E_NO_ACTIVITY", "Activity is not available");
            return;
        }

        if (killSwitch == null) {
            killSwitch = new MobileKillSwitch(activity, this::sendSafetyEvent);
        }

        killSwitch.emergencyStopAll();
        promise.resolve(true);
    }

    private void sendSafetyEvent(AgentSafetyCommand command) {
        WritableMap params = Arguments.createMap();
        params.putString("agentId", command.agentId());
        params.putString("action", command.action().name());
        params.putString("timestamp", String.valueOf(command.timestamp()));

        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onSafetyCommand", params);
    }

    @NonNull
    @Override
    public String getName() {
        // This is the name that will be used to access the module from JavaScript
        return "ReveilaModule";
    }

    /**
     * Starts the Reveila background service.
     * @param systemHome Optional path to the system home directory.
     * @param promise Promise to resolve when the service is starting.
     */
    @ReactMethod
    public void startService(String systemHome, Promise promise) {
        try {
            ReactApplicationContext context = getReactApplicationContext();
            Intent intent = new Intent(context, ReveilaService.class);
            if (systemHome != null && !systemHome.isEmpty()) {
                intent.putExtra("systemHome", systemHome);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_START_FAILED", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void startSovereignSetup(Promise promise) {
        try {
            ReactApplicationContext context = getReactApplicationContext();
            Intent intent = new Intent(context, SovereignSetupActivity.class);
            // Needed because we're starting an activity from outside an Activity context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_START_SETUP_FAILED", e.getMessage(), e);
        }
    }

    /**
     * This method is called when the React Native instance is destroyed.
     * It's the ideal place to clean up resources like the thread pool.
     */
    @Override
    public void invalidate() {
        executorService.shutdown();
    }

    @ReactMethod
    public void isRunning(Promise promise) {
        // This method allows the UI to check if the backend service is fully initialized.
        WritableMap status = Arguments.createMap();
        status.putBoolean("running", ReveilaService.isRunning());
        // Since we can't easily add isStarting to ReveilaService without changing it more,
        // let's just keep it simple for now or check if we should add it.
        promise.resolve(ReveilaService.isRunning());
    }

    /**
     * Checks if the user has completed the native Sovereign setup flow.
     */
    @ReactMethod
    public void isSetupComplete(Promise promise) {
        try {
            ModelSettings settings = new ModelSettings(getReactApplicationContext());
            promise.resolve(settings.isSetupComplete());
        } catch (Exception e) {
            promise.reject("E_CHECK_SETUP_FAILED", e.getMessage(), e);
        }
    }

    /**
     * Resets the entire application state.
     */
    @ReactMethod
    public void resetApplication(Promise promise) {
        try {
            ModelSettings settings = new ModelSettings(getReactApplicationContext());
            settings.resetApplication();
            
            // Also stop the service if it's running
            ReactApplicationContext context = getReactApplicationContext();
            Intent intent = new Intent(context, ReveilaService.class);
            context.stopService(intent);
            
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_RESET_FAILED", e.getMessage(), e);
        }
    }

    @ReactMethod
    public void authenticateBiometric(Promise promise) {
        FragmentActivity activity = (FragmentActivity) getCurrentActivity();
        if (activity == null) {
            promise.reject("ACTIVITY_NOT_FOUND", "Activity is not a FragmentActivity");
            return;
        }

        // Run on UI Thread
        new Handler(Looper.getMainLooper()).post(() -> {
            BiometricManager biometricManager = BiometricManager.from(activity);
            
            // Step 1: Check if biometrics are available and enrolled
            int canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG);
            if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                WritableMap result = Arguments.createMap();
                result.putBoolean("success", false);
                result.putString("error", "BIOMETRICS_UNAVAILABLE");
                promise.resolve(result);
                return;
            }

            // Step 2: Setup the Prompt and Callback
            Executor executor = ContextCompat.getMainExecutor(activity);
            BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                    super.onAuthenticationError(errorCode, errString);
                    WritableMap result = Arguments.createMap();
                    result.putBoolean("success", false);
                    result.putString("error", errString.toString());
                    promise.resolve(result);
                }

                @Override
                public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                    super.onAuthenticationSucceeded(result);
                    WritableMap response = Arguments.createMap();
                    response.putBoolean("success", true);
                    promise.resolve(response);
                }

                @Override
                public void onAuthenticationFailed() {
                    super.onAuthenticationFailed();
                    // This is triggered for rejected fingerprints; the system handles retries.
                }
            });

            // Step 3: Build the Prompt UI
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to access your Vault")
                .setNegativeButtonText("Use Password Instead")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build();

            // Step 4: Launch the Prompt
            biometricPrompt.authenticate(promptInfo);
        });
    }

    @ReactMethod
    public void setUserIdentity(String userId, String org, String role, Promise promise) {
        try {
            this.currentSubject = new Subject();
            // Local Identity Principal
            this.currentSubject.getPrincipals().add(PluginPrincipal.create(userId != null ? userId : "local-user", org != null ? org : "sovereign-local"));
            
            // Inject Mandatory Roles for UI-originated executions
            this.currentSubject.getPrincipals().add(new RolePrincipal("ui"));
            this.currentSubject.getPrincipals().add(new RolePrincipal("user"));
            
            // For now, we also promote to 'system' to allow full access during development
            this.currentSubject.getPrincipals().add(new RolePrincipal(Constants.SYSTEM));
            
            if (role != null && !role.isEmpty()) {
                this.currentSubject.getPrincipals().add(new RolePrincipal(role));
            }
            this.lastActivityTimestamp = System.currentTimeMillis();
            if (promise != null) {
                promise.resolve(true);
            }
        } catch (Exception e) {
            if (promise != null) {
                promise.reject("E_SET_IDENTITY_FAILED", e.getMessage(), e);
            } else {
                Log.e(NAME, "setUserIdentity failed", e);
            }
        }
    }

    /**
     * Updates the session timeout value from the UI.
     */
    @ReactMethod
    public void setSessionTimeout(int minutes, Promise promise) {
        this.sessionTimeoutMs = (long) minutes * 60 * 1000;
        promise.resolve(true);
    }

    /**
     * Sets up the master password for the first time.
     * This generates a new DEK and wraps it with the provided password.
     */
    @ReactMethod
    public void setupMasterPassword(String password, Promise promise) {
        executorService.execute(() -> {
            try {
                if (password.length() < 16 || password.length() > 32) {
                    promise.reject("E_INVALID_LENGTH", "Password must be between 16 and 32 characters.");
                    return;
                }

                ModelSettings settings = new ModelSettings(getReactApplicationContext());
                settings.setupMasterPassword(password);
                
                // Automatically unlock after setup
                unlockAfterSetup(password, promise);
                
            } catch (Exception e) {
                promise.reject("E_SETUP_FAILED", "Failed to setup master password: " + e.getMessage());
            }
        });
    }

    /**
     * Helper method to unlock immediately after password setup
     */
    private void unlockAfterSetup(String password, Promise promise) {
        try {
            Reveila reveilaInstance = ReveilaService.getReveilaInstance();
            if (reveilaInstance == null) {
                promise.reject("E_NOT_READY", "Engine not initialized");
                return;
            }

            AndroidPlatformAdapter adapter = (AndroidPlatformAdapter) reveilaInstance.getSystemContext().getPlatformAdapter();
            AndroidCryptographer crypto = (AndroidCryptographer) adapter.getCryptographer();
            ModelSettings settings = new ModelSettings(getReactApplicationContext());
            
            String saltHex = settings.getMasterSalt();
            String wrappedDekBase64 = settings.getWrappedDekFull();
            
            // Unwrap DEK
            byte[] dek = com.reveila.crypto.DefaultCryptographer.unwrapKeyFromBase64(
                    wrappedDekBase64,
                    password,
                    saltHex
            );

            // Unlock the cryptographer with the raw DEK
            crypto.unlock(dek);
            
            // Initialize default subject on success
            setUserIdentity("local-user", "sovereign-local", "user", null);
            promise.resolve(true);
            
        } catch (Exception e) {
            promise.reject("E_UNLOCK_FAILED", "Setup succeeded but unlock failed: " + e.getMessage());
        }
    }

    /**
     * Unlocks the system using the master password.
     * Supports full password (16-32 chars) or convenience password (first 4 chars).
     * Full password is required every 30 days.
     */
    @ReactMethod
    public void unlockWithMasterPassword(String password, Promise promise) {
        if (promise == null) {
            Log.e(NAME, "unlockWithMasterPassword called with null promise");
            return;
        }
        
        Log.d(NAME, "unlockWithMasterPassword: Starting unlock process");
        
        executorService.execute(() -> {
            try {
                Reveila reveilaInstance = ReveilaService.getReveilaInstance();
                if (reveilaInstance == null) {
                    Log.e(NAME, "unlockWithMasterPassword: Engine not initialized");
                    promise.reject("E_NOT_READY", "Engine not initialized");
                    return;
                }

                AndroidPlatformAdapter adapter = (AndroidPlatformAdapter) reveilaInstance.getSystemContext().getPlatformAdapter();
                AndroidCryptographer crypto = (AndroidCryptographer) adapter.getCryptographer();
                ModelSettings settings = new ModelSettings(getReactApplicationContext());
                
                boolean isFullPassword = password.length() >= 16;
                boolean isConvenience = password.length() == 4;
                
                if (!isFullPassword && !isConvenience) {
                    Log.w(NAME, "unlockWithMasterPassword: Invalid password length: " + password.length());
                    promise.reject("E_INVALID_LENGTH", "Password must be 4 or 16-32 characters.");
                    return;
                }

                long now = System.currentTimeMillis();
                long lastFullLogin = settings.getLastFullLoginTimestamp();
                boolean needsFullPassword = (now - lastFullLogin) >= THIRTY_DAYS_MS;

                if (isConvenience && needsFullPassword) {
                    Log.w(NAME, "unlockWithMasterPassword: Full password required after 30 days");
                    promise.reject("E_FULL_PWD_REQUIRED", "Full password required (30-day security cycle).");
                    return;
                }

                // Verify the hash against one-way stored verifier
                String saltHex = isFullPassword ? settings.getMasterSalt() : settings.getConvSalt();
                String wrappedDekBase64 = isFullPassword ? settings.getWrappedDekFull() : settings.getWrappedDekConv();
                
                if (saltHex == null || wrappedDekBase64 == null) {
                    Log.e(NAME, "unlockWithMasterPassword: No keys found - system not initialized");
                    promise.reject("E_UNLOCK_FAILED", "System not initialized. No keys found.");
                    return;
                }

                Log.d(NAME, "unlockWithMasterPassword: Attempting to unwrap DEK");
                
                // Unwrap DEK (This will fail if password/salt is wrong, proving authentication)
                // For convenience mode, password is already 4 characters
                byte[] dek = com.reveila.crypto.DefaultCryptographer.unwrapKeyFromBase64(
                        wrappedDekBase64,
                        password,
                        saltHex
                );

                if (isFullPassword) {
                    settings.updateLastFullLogin();
                }

                Log.d(NAME, "unlockWithMasterPassword: DEK unwrapped successfully, unlocking cryptographer");
                
                // Unlock the cryptographer with the raw DEK
                crypto.unlock(dek);
                
                Log.d(NAME, "unlockWithMasterPassword: Cryptographer unlocked, setting user identity");
                
                // Initialize default subject on success
                setUserIdentity("local-user", "sovereign-local", "user", null);
                
                Log.i(NAME, "unlockWithMasterPassword: Unlock successful");
                promise.resolve(true);
                
            } catch (javax.crypto.AEADBadTagException e) {
                // Specific exception when GCM auth tag verification fails (wrong password)
                Log.w(NAME, "unlockWithMasterPassword: Invalid password (GCM auth tag failed)");
                if (promise != null) {
                    promise.reject("E_UNLOCK_FAILED", "Invalid password.");
                }
            } catch (Exception e) {
                Log.e(NAME, "unlockWithMasterPassword: Unlock failed with exception", e);
                if (promise != null) {
                    promise.reject("E_UNLOCK_FAILED", "System error: " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Changes the master password by decrypting the DEK with the old password
     * and re-encrypting it with the new password.
     */
    @ReactMethod
    public void changeMasterPassword(String oldPassword, String newPassword, Promise promise) {
        executorService.execute(() -> {
            try {
                ModelSettings settings = new ModelSettings(getReactApplicationContext());
                
                // 1. Unwrap DEK using old password
                String oldSalt = settings.getMasterSalt();
                String oldWrappedDek = settings.getWrappedDekFull();
                
                if (oldSalt == null || oldWrappedDek == null) {
                    promise.reject("E_PWD_CHANGE_FAILED", "System not initialized.");
                    return;
                }

                byte[] dek = com.reveila.crypto.DefaultCryptographer.unwrapKeyFromBase64(oldWrappedDek, oldPassword, oldSalt);
                
                // 2. Generate new salts
                String newSaltFull = com.reveila.crypto.DefaultCryptographer.generateSaltHex();
                String newSaltConv = com.reveila.crypto.DefaultCryptographer.generateSaltHex();

                // 3. Wrap DEK with new passwords
                String newWrappedDekFull = com.reveila.crypto.DefaultCryptographer.wrapKeyToBase64(dek, newPassword, newSaltFull);
                String newWrappedDekConv = com.reveila.crypto.DefaultCryptographer.wrapKeyToBase64(dek, newPassword.substring(0, 4), newSaltConv);
                
                // 4. Update Settings
                settings.updateMasterPasswordHashes(newSaltFull, newSaltConv, newWrappedDekFull, newWrappedDekConv);

                promise.resolve(true);
            } catch (javax.crypto.AEADBadTagException e) {
                promise.reject("E_PWD_CHANGE_FAILED", "Old password incorrect.");
            } catch (Exception e) {
                promise.reject("E_PWD_CHANGE_FAILED", "System error: " + e.getMessage());
            }
        });
    }

    /**
     * Checks if the current session has timed out.
     */
    @ReactMethod
    public void isSessionValid(Promise promise) {
        if (currentSubject == null) {
            promise.resolve(false);
            return;
        }
        
        long now = System.currentTimeMillis();
        boolean valid = (now - lastActivityTimestamp) < sessionTimeoutMs;
        
        if (!valid) {
            currentSubject = null; // Wipe session on timeout
            Reveila reveilaInstance = ReveilaService.getReveilaInstance();
            if (reveilaInstance != null) {
                AndroidPlatformAdapter adapter = (AndroidPlatformAdapter) reveilaInstance.getSystemContext().getPlatformAdapter();
                AndroidCryptographer crypto = (AndroidCryptographer) adapter.getCryptographer();
                crypto.lock(); // Wipe encryption key
            }
        }
        promise.resolve(valid);
    }

    private String hashString(String input) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes());
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Invokes a method on a component running within the embedded Reveila instance.
     * @param componentName The name of the component to target.
     * @param methodName The name of the method to invoke on the component.
     * @param params An array of parameters to pass to the method.
     * @param promise The promise to resolve with the result or reject with an error.
     */
    @ReactMethod
    public void invoke(String componentName, String methodName, ReadableArray params, Promise promise) {
        Log.d(NAME, String.format("invoke: Calling %s.%s", componentName, methodName));
        
        executorService.execute(() -> {
            try {
                // First, check if the service is fully initialized and running.
                Reveila reveilaInstance = ReveilaService.getReveilaInstance();
                if (!ReveilaService.isRunning() || reveilaInstance == null) {
                    Log.e(NAME, "invoke: Reveila service not running");
                    promise.reject("E_NOT_READY", "The Reveila service is not yet available.");
                    return;
                }

                if (currentSubject == null) {
                    Log.w(NAME, "invoke: No user identity set");
                    promise.reject("E_NO_IDENTITY", "User identity not set. Please sign in first.");
                    return;
                }
                
                // Check if cryptographer is unlocked
                AndroidPlatformAdapter adapter = (AndroidPlatformAdapter) reveilaInstance.getSystemContext().getPlatformAdapter();
                AndroidCryptographer crypto = (AndroidCryptographer) adapter.getCryptographer();
                // Check if cryptographer is unlocked
                if (!crypto.isUnlocked()) {
                    Log.e(NAME, "invoke: Cryptographer is locked - system not unlocked");
                    promise.reject("E_LOCKED", "System is locked. Please unlock first.");
                    return;
                }
                
                Log.d(NAME, "invoke: All checks passed, proceeding with invocation");

                Object[] javaParams = ReactNativeJsonConverter.toArray(params);
                Object result = reveilaInstance.invoke(componentName, methodName, javaParams, "127.0.0.1", currentSubject);

                // Convert the Java result to a React Native Writable type (Map, Array, etc.)
                Object writableResult = ReactNativeJsonConverter.convertObjectToWritable(result);
                promise.resolve(writableResult);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                String message = cause != null ? cause.getMessage() : e.getMessage();
                Log.e(NAME, "invoke: Target exception", cause != null ? cause : e);
                promise.reject("E_INVOKE_FAILED", message, cause != null ? cause : e);
            } catch (Throwable t) {
                String message = t.getMessage() != null ? t.getMessage() : t.toString();
                Log.e(NAME, "invoke: Failed", t);
                promise.reject("E_INVOKE_FAILED", message, t);
            }
        });
    }
}