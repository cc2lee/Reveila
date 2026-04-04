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

public class ReveilaModule extends ReactContextBaseJavaModule {

    private MobileKillSwitch killSwitch;
    private Subject currentSubject;
    private long lastActivityTimestamp;
    private long sessionTimeoutMs = 30 * 60 * 1000; // Default 30 mins
    
    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    // It's a best practice to run network operations on a dedicated background thread pool.
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    ReveilaModule(ReactApplicationContext context) {
        super(context);
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
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("E_SET_IDENTITY_FAILED", e.getMessage(), e);
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
     * Unlocks the system using the master password.
     * Supports full password (16-32 chars) or convenience password (first 4 chars).
     * Full password is required every 30 days.
     */
    @ReactMethod
    public void unlockWithMasterPassword(String password, Promise promise) {
        executorService.execute(() -> {
            try {
                Reveila reveilaInstance = ReveilaService.getReveilaInstance();
                if (reveilaInstance == null) {
                    promise.reject("E_NOT_READY", "Engine not initialized");
                    return;
                }

                AndroidPlatformAdapter adapter = (AndroidPlatformAdapter) reveilaInstance.getSystemContext().getPlatformAdapter();
                AndroidCryptographer crypto = (AndroidCryptographer) adapter.getCryptographer();
                ModelSettings settings = new ModelSettings(getReactApplicationContext());
                
                boolean isFullPassword = password.length() >= 16;
                boolean isConvenience = password.length() == 4;
                
                if (!isFullPassword && !isConvenience) {
                    promise.reject("E_INVALID_LENGTH", "Password must be 4 or 16-32 characters.");
                    return;
                }

                long now = System.currentTimeMillis();
                long lastFullLogin = settings.getLastFullLoginTimestamp();
                boolean needsFullPassword = (now - lastFullLogin) >= THIRTY_DAYS_MS;

                if (isConvenience && needsFullPassword) {
                    promise.reject("E_FULL_PWD_REQUIRED", "Full password required (30-day security cycle).");
                    return;
                }

                // Verify the hash against one-way stored verifier
                String saltHex = isFullPassword ? settings.getMasterSalt() : settings.getConvSalt();
                String wrappedDekBase64 = isFullPassword ? settings.getWrappedDekFull() : settings.getWrappedDekConv();
                
                if (saltHex == null || wrappedDekBase64 == null) {
                    promise.reject("E_UNLOCK_FAILED", "System not initialized. No keys found.");
                    return;
                }

                // Unwrap DEK (This will fail if password/salt is wrong, proving authentication)
                byte[] dek = com.reveila.crypto.DefaultCryptographer.unwrapKeyFromBase64(
                        wrappedDekBase64,
                        isFullPassword ? password : password.substring(0, 4),
                        saltHex
                );

                if (isFullPassword) {
                    settings.updateLastFullLogin();
                }

                // Unlock the cryptographer with the raw DEK
                crypto.unlock(dek);
                
                // Initialize default subject on success
                setUserIdentity("local-user", "sovereign-local", "user", null);
                promise.resolve(true);
                
            } catch (javax.crypto.AEADBadTagException e) {
                // Specific exception when GCM auth tag verification fails (wrong password)
                promise.reject("E_UNLOCK_FAILED", "Invalid password.");
            } catch (Exception e) {
                promise.reject("E_UNLOCK_FAILED", "System error: " + e.getMessage());
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
        executorService.execute(() -> {
            try {
                // First, check if the service is fully initialized and running.
                Reveila reveilaInstance = ReveilaService.getReveilaInstance();
                if (!ReveilaService.isRunning() || reveilaInstance == null) {
                    promise.reject("E_NOT_READY", "The Reveila service is not yet available.");
                    return;
                }

                if (currentSubject == null) {
                    promise.reject("E_NO_IDENTITY", "User identity not set. Please sign in first.");
                    return;
                }

                Object[] javaParams = ReactNativeJsonConverter.toArray(params);
                Object result = reveilaInstance.invoke(componentName, methodName, javaParams, "127.0.0.1", currentSubject);

                // Convert the Java result to a React Native Writable type (Map, Array, etc.)
                Object writableResult = ReactNativeJsonConverter.convertObjectToWritable(result);
                promise.resolve(writableResult);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                promise.reject("E_INVOKE_FAILED", message, e);
            }
        });
    }
}