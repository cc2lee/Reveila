package com.reveila.system;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.security.auth.Subject;

public final class PluginContext implements Context {

    private SystemContext systemContext;
    private Manifest manifest;
    private Properties properties = new Properties();
    private Subject subject;

    public PluginContext(SystemContext context, Manifest manifest, Properties properties) {
        this.systemContext = context;
        this.manifest = manifest;
        this.subject = new Subject();
        this.subject.getPrincipals().add(PluginPrincipal.create(manifest.getName(), manifest.getOrg()));
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public Proxy getProxy(String name) throws com.reveila.error.SecurityException, IllegalArgumentException {
        Proxy proxy = systemContext.getProxy(name, subject);
        if (proxy instanceof SystemProxy) {
            proxy = new PluginProxy((SystemProxy)proxy, subject);
        }
        return proxy;
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger(manifest.getName());
    }

    @Override
    public Properties getProperties() {
        return new Properties() {
            @Override
            public String getProperty(String key) {
                String dynamicValue = systemContext.getProperty(key, subject);
                if (dynamicValue != null) {
                    return dynamicValue;
                }
                return properties.getProperty(key);
            }
        };
    }

    @Override
    public PlatformAdapter getPlatformAdapter() {
        List<String> roles = manifest.getRoles();
        if (roles != null && roles.contains(Constants.SYSTEM)) {
            return systemContext.getPlatformAdapter();
        }
        return null;
    }
}
