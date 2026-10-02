package com.reveila.crypto

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Universal JVM implementation of [Cryptographer] extending [PlatformCryptographerAdapter].
 * Uses PBKDF2 for password-based key derivation and delegates AES-GCM encryption/decryption
 * and SHA-256 hashing to [JvmPlatformCryptographer].
 * 
 * Works on any standard JVM (Spring Boot, Windows, Linux) and Android.
 * 
 * @author Charles Lee
 */
open class DefaultCryptographer : PlatformCryptographerAdapter {

    companion object {
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH = 128
        private const val KEY_LENGTH = 256
        private const val ITERATIONS = 65536
        private val secureRandom = SecureRandom()

        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        private fun deriveKey(password: String, salt: ByteArray): SecretKey {
            val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
            val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
            val tmp = factory.generateSecret(spec)
            return SecretKeySpec(tmp.encoded, "AES")
        }

        @JvmStatic
        fun generateRandomKey(): ByteArray {
            val key = ByteArray(KEY_LENGTH / 8)
            secureRandom.nextBytes(key)
            return key
        }

        @JvmStatic
        fun generateSaltHex(): String {
            val salt = ByteArray(SALT_LENGTH)
            secureRandom.nextBytes(salt)
            return bytesToHex(salt)
        }

        @JvmStatic
        @Throws(CryptoException::class)
        fun wrapKeyToBase64(rawKey: ByteArray, password: String, saltHex: String): String {
            try {
                val salt = hexToBytes(saltHex)
                val kek = deriveKey(password, salt)
                val cipher = Cipher.getInstance(AES_MODE)
                val iv = ByteArray(IV_LENGTH)
                secureRandom.nextBytes(iv)
                val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)

                cipher.init(Cipher.ENCRYPT_MODE, kek, parameterSpec)
                val ciphertext = cipher.doFinal(rawKey)

                val combined = ByteArray(iv.size + ciphertext.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
                return Base64.getEncoder().encodeToString(combined)
            } catch (e: Exception) {
                throw CryptoException("Encryption failed", e)
            }
        }

        @JvmStatic
        @Throws(CryptoException::class)
        fun unwrapKeyFromBase64(wrappedKeyBase64: String, password: String, saltHex: String): ByteArray {
            try {
                val combinedData = Base64.getDecoder().decode(wrappedKeyBase64)
                if (combinedData.size < IV_LENGTH) {
                    throw IllegalArgumentException("Invalid encrypted data size.")
                }

                val salt = hexToBytes(saltHex)
                val kek = deriveKey(password, salt)
                val iv = Arrays.copyOfRange(combinedData, 0, IV_LENGTH)
                val ciphertext = Arrays.copyOfRange(combinedData, IV_LENGTH, combinedData.size)

                val cipher = Cipher.getInstance(AES_MODE)
                val parameterSpec = GCMParameterSpec(TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, kek, parameterSpec)

                return cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                throw CryptoException("Decryption failed", e)
            }
        }

        @JvmStatic
        fun bytesToHex(bytes: ByteArray): String {
            val hexString = StringBuilder()
            for (b in bytes) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        }

        @JvmStatic
        fun hexToBytes(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }

    /**
     * Initializes the cryptographer with a raw AES key (Data Encryption Key).
     */
    constructor(keyBytes: ByteArray) : super(keyBytes, JvmPlatformCryptographer())

    /**
     * Initializes the cryptographer by deriving a key from a master password.
     */
    @Throws(CryptoException::class)
    constructor(password: String, salt: ByteArray) : this(
        try {
            deriveKey(password, salt).encoded
        } catch (e: Exception) {
            when (e) {
                is NoSuchAlgorithmException, is InvalidKeySpecException ->
                    throw CryptoException("Failed to derive key", e)
                else -> throw e
            }
        }
    )
}
