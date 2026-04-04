package com.reveila.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Universal implementation of Cryptographer using PBKDF2 for key derivation
 * and AES-GCM for authenticated encryption.
 * 
 * Works on any standard JVM (Spring Boot, Windows, Linux) and Android.
 * 
 * @author Charles Lee
 */
public class DefaultCryptographer implements Cryptographer {

    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATIONS = 65536;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Initializes the cryptographer with a raw AES key (Data Encryption Key).
     */
    public DefaultCryptographer(byte[] keyBytes) throws Exception {
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Initializes the cryptographer by deriving a key from a master password.
     */
    public DefaultCryptographer(String password, byte[] salt) throws Exception {
        this.secretKey = deriveKey(password, salt);
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public static byte[] generateRandomKey() {
        byte[] key = new byte[KEY_LENGTH / 8];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static String generateSaltHex() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return bytesToHex(salt);
    }

    public static String wrapKeyToBase64(byte[] rawKey, String password, String saltHex) throws Exception {
        byte[] salt = hexToBytes(saltHex);
        SecretKey kek = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance(AES_MODE);
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, kek, parameterSpec);
        byte[] ciphertext = cipher.doFinal(rawKey);

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public static byte[] unwrapKeyFromBase64(String wrappedKeyBase64, String password, String saltHex) throws Exception {
        byte[] combinedData = Base64.getDecoder().decode(wrappedKeyBase64);
        if (combinedData.length < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data size.");
        }

        byte[] salt = hexToBytes(saltHex);
        SecretKey kek = deriveKey(password, salt);
        byte[] iv = Arrays.copyOfRange(combinedData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(combinedData, IV_LENGTH, combinedData.length);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, kek, parameterSpec);
        
        return cipher.doFinal(ciphertext);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    public byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_MODE);
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        byte[] ciphertext = cipher.doFinal(data);

        // Result: [IV (12)] + [CIPHERTEXT (N)]
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return combined;
    }

    @Override
    public byte[] decrypt(byte[] combinedData) throws Exception {
        if (combinedData.length < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted data size.");
        }

        byte[] iv = Arrays.copyOfRange(combinedData, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(combinedData, IV_LENGTH, combinedData.length);

        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        return cipher.doFinal(ciphertext);
    }

    @Override
    public byte[] hash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }
}
