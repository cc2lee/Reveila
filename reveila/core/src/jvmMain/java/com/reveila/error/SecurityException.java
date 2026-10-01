package com.reveila.error;

public class SecurityException extends RuntimeException implements ErrorCode {

    private final String errorCode;

    public SecurityException(String message) {
        super(message);
        this.errorCode = null;
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public SecurityException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String getErrorCode() {
        return this.errorCode;
    }
}
