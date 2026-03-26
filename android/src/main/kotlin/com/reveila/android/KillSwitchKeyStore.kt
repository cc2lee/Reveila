package com.reveila.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.Signature
import java.security.KeyPairGenerator
import java.security.PrivateKey

/**
 * Manages the cryptographic integrity of the Reveila Kill Switch.
 * Protects tokens strictly in the hardware-backed Android Keystore, 
 * explicitly bypassing insecure Shared Preferences.
 */
class KillSwitchKeyStore {

    private val KEY_ALIAS = "ReveilaKillSwitchKey"
    private val TAG = "KillSwitchKeyStore"

    init {
        generateKeyIfNotExists()
    }

    // [ ] 3. The Watchdog: Ensure cryptographic signature for Kill Switch token is stored in Android Keystore
    private fun generateKeyIfNotExists() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                Log.i(TAG, "Generating hardware-backed Kill Switch key...")
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore"
                )
                
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    // Ensure the key strictly requires user authentication (biometrics/pin) to use
                    .setUserAuthenticationRequired(true)
                    .build()

                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
                Log.i(TAG, "Kill Switch key generated and secured in AndroidKeyStore.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision AndroidKeyStore for Kill Switch", e)
        }
    }

    /**
     * Signs the kill-switch payload using the hardware-backed private key.
     * Note: In a real flow, this requires the user to have just authenticated 
     * via BiometricPrompt, unlocking the key for usage.
     */
    fun signToken(payload: ByteArray): String? {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(payload)

            val sigBytes = signature.sign()
            return Base64.encodeToString(sigBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign Kill Switch token (Likely Biometric lock enforced)", e)
            return null
        }
    }
}
