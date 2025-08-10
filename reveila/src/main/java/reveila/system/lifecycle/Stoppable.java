package reveila.system.lifecycle;

/**
 * Defines a contract for components that have a shutdown lifecycle method.
 */
public interface Stoppable {
    void stop() throws Exception;
}