package com.reveila.android;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * Java implementation of the Biometric Gatekeeper.
 * Ensures that high-risk actions are protected by hardware-backed authentication.
 */
public class SovereignMemoryManager {

    private static final String TAG = "SovereignMemoryManager";
    private final Context context;

    public SovereignMemoryManager(Context context) {
        this.context = context;
    }

    /**
     * Wrap high-risk writes/actions in a BiometricPrompt challenge.
     * * @param activity   The host activity (The Expo Shell Activity)
     * @param actionName Label for the prompt
     * @param action     The task to execute upon success
     */
    public void secureWriteOperation(FragmentActivity activity, String actionName, Runnable action) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.e(TAG, "Biometric authentication error for " + actionName + ": " + errString);
                        Toast.makeText(activity, "Biometric Error: " + errString, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.i(TAG, "Biometric verified successfully. Executing " + actionName + "...");
                        if (action != null) {
                            action.run(); // Execute the secure action
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.w(TAG, "Biometric authentication failed. Action " + actionName + " blocked.");
                        Toast.makeText(activity, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Authorization")
                .setSubtitle("You must authenticate to continue: " + actionName)
                // Aligns with modern Android security standards (Strong Biometrics + PIN fallback)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                                          BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        // Trigger the challenge
        biometricPrompt.authenticate(promptInfo);
    }
}