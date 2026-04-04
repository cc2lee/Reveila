package com.reveila.system;

import java.util.EventObject;
import java.util.logging.Logger;

import com.reveila.event.EventConsumer;

public abstract class SystemComponent extends AbstractComponent implements EventConsumer {

    protected Context context;
    protected Logger logger;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null.");
        }
        this.context = context;
        this.logger = context.getLogger();
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }
}
