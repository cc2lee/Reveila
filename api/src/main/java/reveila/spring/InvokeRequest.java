package reveila.spring;

/**
 * A Data Transfer Object for a generic component invocation request.
 */
public class InvokeRequest {
    private String methodName;
    private Object[] args;

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