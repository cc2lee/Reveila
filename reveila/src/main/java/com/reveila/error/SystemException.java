package com.reveila.error;

/**
 * @author Charles Lee
 *
 * The root class for all system exceptions. This class has
 * an initial error code value of "10000". Values less than 10000
 * are reserved for system errors.
 */
 
public class SystemException extends Exception implements ErrorCode {
	
	protected String errorCode = "100";

	public SystemException() {
        super();
    }

	public SystemException(String message) {
        super(message);
    }

	public SystemException(Throwable cause) {
        super(cause);
    }

	public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

	public SystemException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
	public String getErrorCode() {
		return errorCode;
	}

}
