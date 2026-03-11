package com.reveila.android.safety;

import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles system-level biometric challenges and signs safety tokens using Android Keystore.
 */
public class BiometricSafetyGuard {

    private static final String KEY_NAME = "ReveilaSafetyKey";
    private final FragmentActivity activity;
    private final Executor executor;

    public BiometricSafetyGuard(FragmentActivity activity) {
        this.activity = activity;
        this.executor = Executors.newSingleThreadExecutor();
        ensureKeyExists();
    }

    public interface BiometricCallback {
        void onAuthenticationSucceeded(byte[] signature);
        void onAuthenticationFailed(String error);
    }

    private void ensureKeyExists() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_NAME)) {
                KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                kpg.initialize(new KeyGenParameterSpec.Builder(
                        KEY_NAME,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                        .setUserAuthenticationRequired(true)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build());
                kpg.generateKeyPair();
            }
        } catch (Exception e) {
            Log.e("BiometricSafetyGuard", "Failed to ensure key exists", e);
        }
    }

    public void authenticateAndSign(byte[] dataToSign, BiometricCallback callback) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                keyStore.load(null);
                Signature signature = Signature.getInstance("SHA256withECDSA");
                signature.initSign((java.security.PrivateKey) keyStore.getKey(KEY_NAME, null));

                BiometricPrompt.CryptoObject cryptoObject = new BiometricPrompt.CryptoObject(signature);

                BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Sovereign Kill Switch")
                        .setSubtitle("Authenticate to sign safety command")
                        .setNegativeButtonText("Cancel")
                        .build();

                BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        callback.onAuthenticationFailed(errString.toString());
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        try {
                            Signature sig = result.getCryptoObject().getSignature();
                            sig.update(dataToSign);
                            byte[] signatureBytes = sig.sign();
                            callback.onAuthenticationSucceeded(signatureBytes);
                        } catch (Exception e) {
                            callback.onAuthenticationFailed("Signing failed: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        callback.onAuthenticationFailed("Authentication failed.");
                    }
                });

                biometricPrompt.authenticate(promptInfo, cryptoObject);

            } catch (Exception e) {
                callback.onAuthenticationFailed("Initialization failed: " + e.getMessage());
            }
        });
    }
}
