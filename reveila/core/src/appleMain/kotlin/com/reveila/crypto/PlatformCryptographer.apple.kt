package com.reveila.crypto

import platform.Foundation.*
import platform.posix.*
import kotlinx.cinterop.*
import platform.CoreCrypto.*

@OptIn(ExperimentalForeignApi::class)
class ApplePlatformCryptographer : PlatformCryptographer {

    override fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray {
        val iv = ByteArray(16)
        // Pseudo-random IV generation
        val now = platform.posix.time(null).toInt()
        for (i in iv.indices) {
            iv[i] = ((now shr (i % 4)) xor (i * 37)).toByte()
        }

        val outBuffer = ByteArray(data.size + 16)
        var numBytesEncrypted: ULong = 0u

        memScoped {
            val moved = alloc<size_tVar>()
            data.usePinned { pData ->
                secretKey.usePinned { pKey ->
                    iv.usePinned { pIv ->
                        outBuffer.usePinned { pOut ->
                            val status = CCCrypt(
                                kCCEncrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                pKey.addressOf(0),
                                secretKey.size.toULong(),
                                pIv.addressOf(0),
                                pData.addressOf(0),
                                data.size.toULong(),
                                pOut.addressOf(0),
                                outBuffer.size.toULong(),
                                moved.ptr
                            )
                            if (status != kCCSuccess) {
                                throw RuntimeException("Encryption failed with status: $status")
                            }
                            numBytesEncrypted = moved.value
                        }
                    }
                }
            }
        }

        return iv + outBuffer.copyOfRange(0, numBytesEncrypted.toInt())
    }

    override fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray {
        if (encryptedData.size < 16) {
            throw IllegalArgumentException("Payload too small for IV")
        }
        val iv = encryptedData.copyOfRange(0, 16)
        val ciphertext = encryptedData.copyOfRange(16, encryptedData.size)
        val outBuffer = ByteArray(ciphertext.size)
        var numBytesDecrypted: ULong = 0u

        memScoped {
            val moved = alloc<size_tVar>()
            ciphertext.usePinned { pData ->
                secretKey.usePinned { pKey ->
                    iv.usePinned { pIv ->
                        outBuffer.usePinned { pOut ->
                            val status = CCCrypt(
                                kCCDecrypt,
                                kCCAlgorithmAES,
                                kCCOptionPKCS7Padding,
                                pKey.addressOf(0),
                                secretKey.size.toULong(),
                                pIv.addressOf(0),
                                pData.addressOf(0),
                                ciphertext.size.toULong(),
                                pOut.addressOf(0),
                                outBuffer.size.toULong(),
                                moved.ptr
                            )
                            if (status != kCCSuccess) {
                                throw RuntimeException("Decryption failed with status: $status")
                            }
                            numBytesDecrypted = moved.value
                        }
                    }
                }
            }
        }

        return outBuffer.copyOfRange(0, numBytesDecrypted.toInt())
    }

    override fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { pData ->
            digest.usePinned { pDigest ->
                CC_SHA256(pData.addressOf(0), data.size.toUInt(), pDigest.addressOf(0).reinterpret())
            }
        }
        return digest
    }

    override fun base64Encode(data: ByteArray): String {
        return data.usePinned { pinned ->
            val nsData = NSData.dataWithBytes(pinned.addressOf(0), data.size.toULong())
            nsData.base64EncodedStringWithOptions(0u)
        }
    }

    override fun base64Decode(encoded: String): ByteArray {
        val nsData = NSData.create(base64Encoding = encoded)
            ?: throw IllegalArgumentException("Invalid Base64 string")
        val size = nsData.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                platform.posix.memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
            }
        }
        return bytes
    }
}

actual fun createPlatformCryptographer(): PlatformCryptographer = ApplePlatformCryptographer()
