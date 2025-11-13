package reveila.system;

/**
 * Defines a contract for components that have a startup lifecycle method.
 */
public interface Startable {
    void start() throws Exception;
}