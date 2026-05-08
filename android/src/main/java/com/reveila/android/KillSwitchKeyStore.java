package com.reveila.android;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

/**
 * Manages the cryptographic integrity of the Reveila Kill Switch.
 * Protects tokens strictly in the hardware-backed Android Keystore, 
 * explicitly bypassing insecure Shared Preferences.
 */
public class KillSwitchKeyStore {

    private static final String KEY_ALIAS = "ReveilaKillSwitchKey";
    private static final String TAG = "KillSwitchKeyStore";

    public KillSwitchKeyStore() {
        generateKeyIfNotExists();
    }

    /**
     * The Watchdog: Ensure cryptographic signature for Kill Switch token 
     * is stored in Android Keystore.
     */
    private void generateKeyIfNotExists() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.i(TAG, "Generating hardware-backed Kill Switch key...");
                
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
                );
                
                KeyGenParameterSpec parameterSpec = new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    // Ensure the key strictly requires user authentication (biometrics/pin) to use
                    .setUserAuthenticationRequired(true)
                    .build();

                keyPairGenerator.initialize(parameterSpec);
                keyPairGenerator.generateKeyPair();
                Log.i(TAG, "Kill Switch key generated and secured in AndroidKeyStore.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to provision AndroidKeyStore for Kill Switch", e);
        }
    }

    /**
     * Signs the kill-switch payload using the hardware-backed private key.
     * Note: This requires the user to have just authenticated via BiometricPrompt, 
     * unlocking the key for usage.
     */
    public String signToken(byte[] payload) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            
            signature.initSign(privateKey);
            signature.update(payload);

            byte[] sigBytes = signature.sign();
            return Base64.encodeToString(sigBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sign Kill Switch token (Likely Biometric lock enforced)", e);
            return null;
        }
    }
}