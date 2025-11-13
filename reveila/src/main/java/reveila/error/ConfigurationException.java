package reveila.error;

/**
 * @author Charles Lee
 * 
 * Thrown by system codes to indicate a configuration error.
 */
 
public class ConfigurationException extends Exception implements ErrorCode {
	
	public static final String ERROR_CODE = "500";

	public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getErrorCode() {
		return ERROR_CODE;
	}

}
