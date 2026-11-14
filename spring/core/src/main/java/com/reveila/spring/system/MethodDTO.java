package com.reveila.spring.system;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.reveila.system.Constants;

/**
 * Data Transfer Object representing a dynamic method call.
 * Used by ApiController to route requests to Reveila components and enforce API version compatibility.
 */
public class MethodDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonAlias({"apiVersion", "clientVersion"})
    private String version;

    @JsonAlias({"method"})
    private String methodName;

    @JsonAlias({"params", "arguments"})
    private Object[] args;

    public MethodDTO() {}

    public MethodDTO(String methodName, Object[] args) {
        this(Constants.DEFAULT_SYSTEM_VERSION, methodName, args);
    }

    public MethodDTO(String version, String methodName, Object[] args) {
        this.version = version;
        this.methodName = methodName;
        this.args = args;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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
