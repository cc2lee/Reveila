package com.reveila.android;

import com.reveila.crypto.Cryptographer;
import com.reveila.crypto.DefaultCryptographer;
import java.util.Arrays;

/**
 * Android implementation of Cryptographer.
 * Wraps the universal DefaultCryptographer with Android-specific session handling.
 * 
 * @author CL
 */
public class AndroidCryptographer implements Cryptographer {

    private DefaultCryptographer delegate;

    public AndroidCryptographer() {
        // Initialized without a key. Must call unlock() before use.
    }

    /**
     * Unlocks the cryptographer using the unwrapped DEK.
     */
    public void unlock(byte[] dek) throws Exception {
        this.delegate = new DefaultCryptographer(dek);
    }

    /**
     * Locks the cryptographer by wiping the derived key.
     */
    public void lock() {
        this.delegate = null;
    }

    private void ensureUnlocked() {
        if (delegate == null) {
            throw new IllegalStateException("Cryptographer is locked. Provide master password.");
        }
    }

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        ensureUnlocked();
        return delegate.encrypt(data);
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        ensureUnlocked();
        return delegate.decrypt(encryptedData);
    }

    @Override
    public byte[] hash(byte[] data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }
}
