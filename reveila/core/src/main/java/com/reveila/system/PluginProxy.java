package com.reveila.system;

import java.util.EventObject;
import java.util.List;

import javax.security.auth.Subject;

public class PluginProxy implements Proxy {

    private SystemProxy systemProxy;
    private Subject subject;

    public PluginProxy(SystemProxy systemProxy, Subject subject) {
        if (systemProxy == null) {
            throw new IllegalArgumentException("Argument 'systemProxy' cannot be null.");
        }
        if (subject == null) {
            throw new IllegalArgumentException("Argument 'subject' cannot be null.");
        }
        this.systemProxy = systemProxy;
        this.subject = subject;
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
        systemProxy.notifyEvent(evtObj);
    }

    @Override
    public String getName() {
        return systemProxy.getName();
    }

    @Override
    public List<String> getRequiredRoles() {
        return systemProxy.getRequiredRoles();
    }

    @Override
    public Object invoke(String methodName, Object[] args) throws Exception {
        return systemProxy.invoke(methodName, args, this.subject);
    }
}
