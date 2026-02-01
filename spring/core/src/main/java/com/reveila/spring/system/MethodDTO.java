package com.reveila.spring.system;

import java.io.Serializable;

/**
 * Data Transfer Object representing a dynamic method call.
 * Used by ApiController to route requests to Reveila components.
 */
public class MethodDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String methodName;
    private Object[] args;

    public MethodDTO() {}

    public MethodDTO(String methodName, Object[] args) {
        this.methodName = methodName;
        this.args = args;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Object[] getArgs() {
        return args;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }
}