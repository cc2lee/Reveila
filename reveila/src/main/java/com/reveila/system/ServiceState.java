package com.reveila.system;

/**
 * Defines the lifecycle states for any Reveila service.
 * Supports operational monitoring and the CISO "Kill Switch" functionality.
 * 
 * @author CL
 */
public enum ServiceState {
    /**
     * Service has been instantiated but not yet started.
     */
    INITIALIZED,

    /**
     * Service is currently executing its onStart() logic.
     */
    STARTING,

    /**
     * Service is fully operational and ready to receive calls.
     */
    ACTIVE,

    /**
     * Service has encountered a terminal failure during startup or execution.
     */
    FAILED,

    /**
     * Service is currently executing its onStop() logic.
     */
    STOPPING,

    /**
     * Service has been gracefully stopped.
     */
    STOPPED
}
