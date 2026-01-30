package com.reveila.error;

public class ConfigurationException extends SystemException {

    public ConfigurationException() {
        super();
    }

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }
}
