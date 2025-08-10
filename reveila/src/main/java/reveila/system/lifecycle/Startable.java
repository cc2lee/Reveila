package reveila.system.lifecycle;

/**
 * Defines a contract for components that have a startup lifecycle method.
 */
public interface Startable {
    void start() throws Exception;
}