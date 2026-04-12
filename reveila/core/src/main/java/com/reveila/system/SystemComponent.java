package com.reveila.system;

import java.util.EventObject;
import java.util.logging.Logger;

import com.reveila.event.EventConsumer;

public abstract class SystemComponent extends AbstractComponent implements EventConsumer {

    protected SystemContext context;
    protected Logger logger;

    public SystemContext getContext() {
        return context;
    }

    public void setContext(SystemContext context) {
        if (context == null) {
            this.context = null;
            this.logger = null;
        }
        else {
            this.context = context;
            this.logger = context.getLogger();
        }
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }
}
