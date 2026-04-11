package com.reveila.system;

import java.util.EventObject;
import java.util.logging.Logger;

import com.reveila.event.EventConsumer;

public abstract class SystemComponent extends AbstractComponent implements EventConsumer {

    protected SystemContext context;
    protected Logger logger;

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        if (context == null) {
            this.context = null;
            return;
        }

        if (context instanceof SystemContext) {
            this.context = (SystemContext) context;
            this.logger = context.getLogger();
            return;
        }

        throw new IllegalArgumentException("Context must be a SystemContext.");
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
    }
}
