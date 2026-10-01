package com.reveila.crypto

/**
 * Multiplatform cryptographic operations for sovereign execution across devices.
 */
interface PlatformCryptographer {
    fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray
    fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray
    fun sha256(data: ByteArray): ByteArray
    fun base64Encode(data: ByteArray): String
    fun base64Decode(encoded: String): ByteArray

    companion object {
        operator fun invoke(): PlatformCryptographer = createPlatformCryptographer()
    }
}

expect fun createPlatformCryptographer(): PlatformCryptographer
