package com.reveila.ai;

import com.reveila.error.SystemException;

public class LlmException extends SystemException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }

    public LlmException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode);
    }
}
