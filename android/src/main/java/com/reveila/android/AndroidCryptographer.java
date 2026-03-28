package com.reveila.android;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.reveila.crypto.Cryptographer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Arrays;

/**
 * Android implementation of Cryptographer using the hardware-backed Keystore System.
 * 
 * @author CL
 */
public class AndroidCryptographer implements Cryptographer {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "ReveilaMasterKey";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // Standard for GCM
    private static final int TAG_LENGTH = 128;

    public AndroidCryptographer() throws Exception {
        ensureKeyExists();
    }

    private void ensureKeyExists() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            keyGenerator.generateKey();
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data);

        // Concatenate IV and ciphertext for storage
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return combined;
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        // Extract IV from the beginning
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, IV_LENGTH, encryptedData.length);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }

    @Override
    public byte[] hash(byte[] data) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }
}
