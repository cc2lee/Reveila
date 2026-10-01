package com.reveila.system;

import java.util.EventObject;

import com.reveila.event.EventConsumer;

public abstract class SystemComponent extends AbstractComponent implements EventConsumer {

    protected SystemContext context;
    
    public SystemContext getContext() {
        return context;
    }

    public void setContext(SystemContext context) {
        this.context = context;
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }
}
