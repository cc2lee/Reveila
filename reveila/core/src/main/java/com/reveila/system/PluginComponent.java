package com.reveila.system;

import java.util.logging.Logger;

public abstract class PluginComponent extends AbstractComponent {

    protected Context context;
    protected Logger logger;

    public void setContext(Context context) {
        this.context = context;
        if (context == null) {
            if (this.logger != null) this.logger = null;
        } else {
            this.logger = Logger.getLogger("reveila.plugin." + this.getClass().getSimpleName());
        }
    }
}
