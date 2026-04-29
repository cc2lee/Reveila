package com.reveila.error;

public class SystemException extends Exception implements ErrorCode {

    private final String errorCode;

    public SystemException(String message) {
        super(message);
        this.errorCode = null;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public SystemException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
