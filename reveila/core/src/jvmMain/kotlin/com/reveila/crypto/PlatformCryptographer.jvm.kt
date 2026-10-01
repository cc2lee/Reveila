package com.reveila.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class JvmPlatformCryptographer : PlatformCryptographer {

    private val secureRandom = SecureRandom()

    override fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val keySpec = SecretKeySpec(secretKey.copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    override fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray {
        if (encryptedData.size < 12) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }
        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)
        val keySpec = SecretKeySpec(secretKey.copyOf(32), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    override fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    override fun base64Encode(data: ByteArray): String {
        return Base64.getEncoder().encodeToString(data)
    }

    override fun base64Decode(encoded: String): ByteArray {
        return Base64.getDecoder().decode(encoded)
    }
}

actual fun createPlatformCryptographer(): PlatformCryptographer = JvmPlatformCryptographer()
