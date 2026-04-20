package com.reveila.system;

public abstract class PluginComponent extends AbstractComponent {

    protected Context context;
    
    public void setContext(Context context) {
        this.context = context;
    }
}
