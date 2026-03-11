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
	 * @throws ServiceException If there is an error decrypting the data.
	 * @return The byte array containing the decrypted data.
	 */
	public byte[] decrypt(byte[] data) throws Exception;

	/**
	 *  Encrypt the data.
	 *
	 * @param data Data to be encrypted.
	 * @throws ServiceException If there is an error encrypting the data.
	 * @return The encrypted data as byte array.
	 */
	public byte[] encrypt(byte[] data) throws Exception;

   /**
	* Apply a one-way "hash" to the input data, rendering it unreadable. The
	* hashed data cannot be decrypted, but the same input data will hash to the
	* same hashed value. This is appropriate for information such as passwords.
	* 
	* @param data The data to be hashed.
	* @return Hashed data in byte array.
	* @throws ServiceException If there is an error hashing the data.
	*/
	public byte[] hash(byte[] data) throws Exception;

}
