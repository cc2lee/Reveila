package com.reveila.crypto

import kotlin.random.Random

/**
 * Universal multiplatform implementation of [Cryptographer] extending [PlatformCryptographerAdapter].
 * Works identically on any host OS: Windows, Mac, iOS, Android, Linux, and JVM.
 */
open class DefaultCryptographer(
    secretKey: ByteArray,
    platformCrypto: PlatformCryptographer = PlatformCryptographer()
) : PlatformCryptographerAdapter(secretKey, platformCrypto) {

    constructor(password: String, salt: ByteArray) : this(
        deriveKey(password, salt)
    )

    constructor() : this(generateRandomKey())

    companion object {
        private const val SALT_LENGTH = 16
        private const val KEY_LENGTH = 32 // 256 bits

        fun deriveKey(password: String, salt: ByteArray): ByteArray {
            val crypto = PlatformCryptographer()
            var hash = crypto.sha256(password.encodeToByteArray() + salt)
            // Perform 1000 rounds of SHA-256 for key strengthening
            for (i in 0 until 1000) {
                hash = crypto.sha256(hash + salt)
            }
            return hash
        }

        fun generateRandomKey(): ByteArray {
            return Random.nextBytes(KEY_LENGTH)
        }

        fun generateSaltHex(): String {
            val salt = Random.nextBytes(SALT_LENGTH)
            return bytesToHex(salt)
        }

        fun bytesToHex(bytes: ByteArray): String {
            val hexChars = "0123456789abcdef"
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append(hexChars[v ushr 4])
                sb.append(hexChars[v and 0x0F])
            }
            return sb.toString()
        }

        fun hexToBytes(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                val high = hexDigit(s[i])
                val low = hexDigit(s[i + 1])
                data[i / 2] = ((high shl 4) + low).toByte()
                i += 2
            }
            return data
        }

        private fun hexDigit(c: Char): Int {
            return when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> throw IllegalArgumentException("Invalid hex character: $c")
            }
        }
    }
}
