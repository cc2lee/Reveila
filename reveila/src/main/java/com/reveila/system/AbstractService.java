package com.reveila.system;

import java.util.EventObject;
import java.util.logging.Logger;
import java.time.Duration;
import java.time.Instant;

import com.reveila.event.EventConsumer;

/**
 * The foundational class for all plugins in the Reveila-Suite.
 * Handles lifecycle state, interruption checks, and context management.
 */
public abstract class AbstractService implements EventConsumer, Startable, Stoppable {

    protected SystemContext systemContext;
    protected Logger logger;
    private ServiceState state = ServiceState.INITIALIZED;
    private Instant startTime;
    private Duration startupLatency;

    public boolean isRunning() {
        return state == ServiceState.ACTIVE;
    }

    public void setSystemContext(SystemContext context) {
        if (context == null) {
            throw new IllegalArgumentException("SystemContext cannot be null.");
        }
        this.systemContext = context;
        this.logger = context.getLogger();
    }

    @Override
    public final synchronized void stop() throws Exception {
        this.state = ServiceState.STOPPING;
        try {
            onStop();
            this.state = ServiceState.STOPPED;
        } catch (Exception e) {
            this.state = ServiceState.FAILED;
            throw e;
        }
    }

    protected abstract void onStop() throws Exception;

    /**
     * Final implementation of start to handle state and safety.
     * Logic should be implemented in onStart().
     */
    @Override
    public final synchronized void start() throws Exception {
        if (state == ServiceState.ACTIVE || state == ServiceState.STARTING) return;
        
        this.startTime = Instant.now();
        this.state = ServiceState.STARTING;

        try {
            onStart(); // Hook for subclass implementation
            
            // Check: did we time out or get interrupted during onStart?
            if (Thread.currentThread().isInterrupted()) {
                stop();
                throw new InterruptedException("Service start interrupted.");
            }
            
            this.startupLatency = Duration.between(startTime, Instant.now());
            this.state = ServiceState.ACTIVE;
        } catch (Exception e) {
            this.state = ServiceState.FAILED;
            throw e;
        }
    }

    /**
     * @return The exact time in milliseconds it took for the service to boot.
     * Useful for Slide 8 (Operational Performance) data.
     */
    public long getStartupLatencyMs() {
        return startupLatency != null ? startupLatency.toMillis() : 0;
    }

    /**
     * @return The current operational state of the service.
     */
    public ServiceState getServiceState() {
        return state;
    }
    
    /**
     * Subclasses implement their specific startup logic here.
     * Must check isInterrupted() if performing long-running tasks.
     */
    protected abstract void onStart() throws Exception;

    /**
     * Utility for plugins to check if the system wants them to halt.
     */
    protected boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }
    
    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }

}
