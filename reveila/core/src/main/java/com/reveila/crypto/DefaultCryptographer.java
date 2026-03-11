package com.reveila.crypto;

import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author Charles Lee
 */
public class DefaultCryptographer implements Cryptographer {

	private Cipher cipher;
	private SecretKeySpec secretKey;

/*
**The key bytes length must match algorithm length, e.g., 16 bytes for AES-128**
- 128 bits is the default for "AES".
- Use 16 bytes for AES-128, 24 for AES-192, 32 for AES-256.
- Java supports 256-bit keys only if the JRE allows it (modern JDKs do by default).
*/
	public DefaultCryptographer(byte[] keyBytes) throws Exception {
		secretKey = new SecretKeySpec(keyBytes, "AES");
		cipher = Cipher.getInstance("AES");
	}

	public byte[] encrypt(byte[] data) throws Exception {
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		return cipher.doFinal(data);
	}

	public byte[] decrypt(byte[] encryptedData) throws Exception {
		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		return cipher.doFinal(encryptedData);
	}

	public byte[] hash(byte[] data) throws Exception {
		/*
		 * Replace "SHA-256" with "SHA-1" or "MD5" for other algorithms (SHA-256 is recommended).
		 * The returned byte array is the hash value.
		 */
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(data);
	}
}
