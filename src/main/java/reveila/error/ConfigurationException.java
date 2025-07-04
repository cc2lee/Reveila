package reveila.error;

/**
 * @author Charles Lee
 * 
 * Thrown by system codes to indicate a configuration error.
 */
 
public class ConfigurationException extends SystemException {
	
	public static final String ERROR_CODE = "100";

	public ConfigurationException(String message) {
        super(message);
    }

    @Override
	public String getErrorCode() {
		return ERROR_CODE;
	}

}
