package com.reveila.system;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

public abstract class AbstractComponent implements Startable, Stoppable {

    protected Logger logger = Logger.getLogger("reveila." + this.getClass().getName());
    protected boolean isManaged = false;

    public boolean isManaged() {
        return isManaged;
    }

    public void setManaged(boolean managed) {
        this.isManaged = managed;
    }

    protected boolean debug = false;
    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    private ComponentState state = ComponentState.STOPPED;
    private Instant startTime;
    private Duration startupLatency;

    public boolean isRunning() {
        return state == ComponentState.ACTIVE;
    }

    @Override
    public final synchronized void stop() throws Exception {
        this.state = ComponentState.STOPPING;
        try {
            onStop();
            this.state = ComponentState.STOPPED;
        } catch (Exception e) {
            this.state = ComponentState.FAILED;
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
        if (state == ComponentState.ACTIVE || state == ComponentState.STARTING) return;
        
        this.startTime = Instant.now();
        this.state = ComponentState.STARTING;

        try {
            onStart(); // Hook for subclass implementation
            
            // Check: did we time out or get interrupted during onStart?
            if (Thread.currentThread().isInterrupted()) {
                stop();
                throw new InterruptedException("Component start interrupted.");
            }
            
            this.startupLatency = Duration.between(startTime, Instant.now());
            this.state = ComponentState.ACTIVE;
        } catch (Exception e) {
            this.state = ComponentState.FAILED;
            throw e;
        }
    }

    /**
     * @return The exact time in milliseconds it took for the component to boot.
     * Useful for Slide 8 (Operational Performance) data.
     */
    public long getStartupLatencyMs() {
        return startupLatency != null ? startupLatency.toMillis() : 0;
    }

    /**
     * @return The current operational state of the Component.
     */
    public ComponentState getState() {
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
}
