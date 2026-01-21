package com.reveila.system;

import java.util.EventObject;
import java.util.logging.Logger;

import com.reveila.event.EventConsumer;

/**
 * The foundational class for all plugins in the Reveila-Suite.
 * Handles lifecycle state, interruption checks, and context management.
 */
public abstract class AbstractService implements EventConsumer, Startable, Stoppable {

    protected SystemContext systemContext;
    private volatile boolean running = false;
    protected Logger logger;

    public boolean isRunning() {
        return running;
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
        running = false;
        onStop();
    }

    protected abstract void onStop() throws Exception;

    /**
     * Final implementation of start to handle state and safety.
     * Logic should be implemented in onStart().
     */
    @Override
    public final synchronized void start() throws Exception {
        if (running) return;
        
        onStart();
        
        // Check: did we time out or get interrupted during onStart?
        if (Thread.currentThread().isInterrupted()) {
            stop();
            throw new InterruptedException("Service start interrupted.");
        }
        
        running = true;
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
