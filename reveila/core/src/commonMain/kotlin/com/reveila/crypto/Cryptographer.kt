package com.reveila.crypto

/**
 * Defines a common system wide cryptography interface.
 *
 * @author Charles Lee
 */
interface Cryptographer {

    /**
     * Decrypt the data.
     *
     * @param data The data to be decrypted, passed in as byte array.
     * @return The byte array containing the decrypted data.
     * @throws CryptoException If there is an error decrypting the data.
     */
    @Throws(CryptoException::class)
    fun decrypt(data: ByteArray): ByteArray

    /**
     * Encrypt the data.
     *
     * @param data Data to be encrypted.
     * @return The encrypted data as byte array.
     * @throws CryptoException If there is an error encrypting the data.
     */
    @Throws(CryptoException::class)
    fun encrypt(data: ByteArray): ByteArray

    /**
     * Apply a one-way "hash" to the input data, rendering it unreadable.
     *
     * @param data The data to be hashed.
     * @return Hashed data in byte array.
     * @throws CryptoException If there is an error hashing the data.
     */
    @Throws(CryptoException::class)
    fun hash(data: ByteArray): ByteArray
}
