package com.reveila.error;

public class SystemException extends Exception {
	
	private String errorCode = "100";

	public SystemException(String message) {
        super(message);
    }

	public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

	public SystemException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
		return errorCode;
	}

}
