package com.reveila.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Ensures that high-risk Sovereign Memory writes or system actions
 * are gatekept behind a hardware-backed Biometric prompt.
 */
class SovereignMemoryManager(private val context: Context) {

    private val TAG = "SovereignMemoryManager"

    // [ ] 3. The Watchdog: Wrap SovereignMemoryManager writes/actions in BiometricPrompt challenge
    fun secureWriteOperation(activity: FragmentActivity, actionName: String, action: () -> Unit) {
        val executor: Executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e(TAG, "Biometric authentication error for $actionName: $errString")
                    
                    // Show a Toast so the user actually sees why the prompt failed/aborted
                    Toast.makeText(activity, "Biometric Error: $errString\n(Did you set up a PIN/Fingerprint on this emulator?)", Toast.LENGTH_LONG).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i(TAG, "Biometric verified successfully. Executing $actionName...")
                    action() // Execute the secure action
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Biometric authentication failed. Action $actionName blocked.")
                    Toast.makeText(activity, "Authentication failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Reveila High-Risk Action Guard")
            .setSubtitle("Authenticate to authorize: $actionName")
            // Allow fallback to standard PIN/Pattern if fingerprint isn't enrolled (crucial for Emulators)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        // Trigger the challenge
        biometricPrompt.authenticate(promptInfo)
    }
}
