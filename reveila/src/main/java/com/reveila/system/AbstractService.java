package com.reveila.system;

import java.util.EventObject;

import com.reveila.event.EventConsumer;

public abstract class AbstractService implements EventConsumer, Startable, Stoppable {

    protected SystemContext systemContext;
    protected boolean available = true;

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
    
    public void setSystemContext(SystemContext systemContext) {
        this.systemContext = systemContext;
    }

    @Override
    public void stop() throws Exception {
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }

}
