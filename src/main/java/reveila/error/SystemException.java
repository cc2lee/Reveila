package reveila.error;

/**
 * @author Charles Lee
 *
 * The root class for all system exceptions. This class has
 * an initial error code value of "10000". Values less than 10000
 * are reserved for system errors.
 */
 
public class SystemException extends Exception implements ErrorCode {
	
	protected String errorCode = "10000";

	public SystemException(String message) {
        super(message);
    }

    @Override
	public String getErrorCode() {
		return errorCode;
	}

}
