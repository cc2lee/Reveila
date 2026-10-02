package com.reveila.crypto

/**
 * Universal multiplatform implementation of [Cryptographer] backed by [PlatformCryptographer].
 * Encapsulates a secret key and delegates low-level cryptographic operations
 * (authenticated encryption/decryption, SHA-256 hashing) to the host platform.
 *
 * Enables offline, sovereign cryptography across Apple (CommonCrypto),
 * Windows (Win32), Linux (POSIX), Android, and JVM.
 */
open class PlatformCryptographerAdapter(
    protected val secretKey: ByteArray,
    protected val platformCrypto: PlatformCryptographer = PlatformCryptographer()
) : Cryptographer {

    @Throws(CryptoException::class)
    override fun encrypt(data: ByteArray): ByteArray {
        return try {
            platformCrypto.encrypt(data, secretKey)
        } catch (e: Exception) {
            throw CryptoException("Encryption failed: ${e.message}", e)
        }
    }

    @Throws(CryptoException::class)
    override fun decrypt(data: ByteArray): ByteArray {
        return try {
            platformCrypto.decrypt(data, secretKey)
        } catch (e: Exception) {
            throw CryptoException("Decryption failed: ${e.message}", e)
        }
    }

    @Throws(CryptoException::class)
    override fun hash(data: ByteArray): ByteArray {
        return try {
            platformCrypto.sha256(data)
        } catch (e: Exception) {
            throw CryptoException("Hashing failed: ${e.message}", e)
        }
    }
}
