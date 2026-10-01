package com.reveila.crypto;

import com.reveila.error.SystemException;

public class CryptoException extends SystemException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }

    public CryptoException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }

}
