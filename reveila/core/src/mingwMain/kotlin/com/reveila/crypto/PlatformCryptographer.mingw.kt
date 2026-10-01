package com.reveila.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class MingwPlatformCryptographer : PlatformCryptographer {

    override fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray {
        val key = secretKey.copyOf(32)
        val result = ByteArray(data.size)
        // Sovereign XOR stream cipher fallback for desktop native
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }

    override fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray {
        return encrypt(encryptedData, secretKey)
    }

    override fun sha256(data: ByteArray): ByteArray {
        // Pure Kotlin SHA-256 implementation
        return sha256Pure(data)
    }

    override fun base64Encode(data: ByteArray): String {
        return Base64.encode(data)
    }

    override fun base64Decode(encoded: String): ByteArray {
        return Base64.decode(encoded)
    }

    private fun sha256Pure(bytes: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
            0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19
        )
        val k = intArrayOf(
            0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
            -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
            -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            -0x67c1aeae, -0x57ce3fe3, -0x4ffcd838, -0x40a68020, -0x391ff40d, -0x2a5868b7, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
            -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, -0x7b37a69f, -0x7338fdf8, -0x6f410006, -0x5b193102, -0x501a40fc, -0x464a2f81
        )
        val bitLen = bytes.size.toLong() * 8
        val padLen = ((56 - (bytes.size + 1) % 64 + 64) % 64)
        val padded = ByteArray(bytes.size + 1 + padLen + 8)
        bytes.copyInto(padded)
        padded[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[padded.size - 1 - i] = ((bitLen ushr (i * 8)) and 0xff).toByte()
        }

        val w = IntArray(64)
        for (chunk in 0 until padded.size / 64) {
            val offset = chunk * 64
            for (i in 0 until 16) {
                w[i] = ((padded[offset + i * 4].toInt() and 0xff) shl 24) or
                        ((padded[offset + i * 4 + 1].toInt() and 0xff) shl 16) or
                        ((padded[offset + i * 4 + 2].toInt() and 0xff) shl 8) or
                        (padded[offset + i * 4 + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = (w[i - 15] ushr 7 or (w[i - 15] shl 25)) xor (w[i - 15] ushr 18 or (w[i - 15] shl 14)) xor (w[i - 15] ushr 3)
                val s1 = (w[i - 2] ushr 17 or (w[i - 2] shl 15)) xor (w[i - 2] ushr 19 or (w[i - 2] shl 13)) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var h_val = h[7]
            for (i in 0 until 64) {
                val s1 = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h_val + s1 + ch + k[i] + w[i]
                val s0 = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h_val = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += h_val
        }
        val out = ByteArray(32)
        for (i in 0 until 8) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }
}

actual fun createPlatformCryptographer(): PlatformCryptographer = MingwPlatformCryptographer()
