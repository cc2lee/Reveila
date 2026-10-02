package com.reveila.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformCryptographerTest {

    private val testKey = ByteArray(32) { (it + 1).toByte() }
    private val testData = "Hello, Reveila Sovereign Native Engine!".encodeToByteArray()

    @Test
    fun testPlatformCryptographerEncryptDecrypt() {
        val crypto = PlatformCryptographer()
        val encrypted = crypto.encrypt(testData, testKey)
        assertTrue(encrypted.isNotEmpty(), "Encrypted payload should not be empty")

        val decrypted = crypto.decrypt(encrypted, testKey)
        assertEquals(
            testData.decodeToString(),
            decrypted.decodeToString(),
            "Decrypted data must match original input"
        )
    }

    @Test
    fun testPlatformCryptographerSha256() {
        val crypto = PlatformCryptographer()
        val hash = crypto.sha256(testData)
        assertEquals(32, hash.size, "SHA-256 hash must be 32 bytes")
    }

    @Test
    fun testPlatformCryptographerBase64() {
        val crypto = PlatformCryptographer()
        val encoded = crypto.base64Encode(testData)
        val decoded = crypto.base64Decode(encoded)
        assertEquals(
            testData.decodeToString(),
            decoded.decodeToString(),
            "Base64 roundtrip must match original"
        )
    }

    @Test
    fun testPlatformCryptographerAdapterAsCryptographer() {
        val cryptographer: Cryptographer = PlatformCryptographerAdapter(testKey)

        val encrypted = cryptographer.encrypt(testData)
        assertTrue(encrypted.isNotEmpty(), "Encrypted payload should not be empty")

        val decrypted = cryptographer.decrypt(encrypted)
        assertEquals(
            testData.decodeToString(),
            decrypted.decodeToString(),
            "Adapter decrypted data must match original input"
        )

        val hash = cryptographer.hash(testData)
        assertEquals(32, hash.size, "Hash must be 32 bytes")
    }
}
