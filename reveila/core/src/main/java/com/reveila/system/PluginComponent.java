package com.reveila.system;

import java.util.logging.Logger;

public abstract class PluginComponent extends AbstractComponent {

    protected Context context;
    protected Logger logger;

    public void setContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null.");
        }
        this.context = context;
        this.logger = context.getLogger();
    }
}
