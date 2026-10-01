package com.reveila.crypto;

/**
 * @author Charles Lee
 *
 * Defines a common system wide cryptography interface.
 */
public interface Cryptographer {

	/**
	 * Decrypt the data.
	 * 
	 * @param data The data to be decrypted, passed in as byte array.
	 * @return The byte array containing the decrypted data.
	 * @throws CryptoException If there is an error decrypting the data.
	 */
	public byte[] decrypt(byte[] data) throws CryptoException;

	/**
	 * Encrypt the data.
	 *
	 * @param data Data to be encrypted.
	 * @return The encrypted data as byte array.
	 * @throws CryptoException If there is an error encrypting the data.
	 */
	public byte[] encrypt(byte[] data) throws CryptoException;

	/**
	 * Apply a one-way "hash" to the input data, rendering it unreadable. The
	 * hashed data cannot be decrypted, but the same input data will hash to the
	 * same hashed value. This is appropriate for information such as passwords.
	 * 
	 * @param data The data to be hashed.
	 * @return Hashed data in byte array.
	 * @throws CryptoException If there is an error hashing the data.
	 */
	public byte[] hash(byte[] data) throws CryptoException;

}
