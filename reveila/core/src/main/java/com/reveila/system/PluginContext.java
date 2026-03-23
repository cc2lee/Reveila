package com.reveila.system;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

import javax.security.auth.Subject;

public final class PluginContext implements Context {

    private SystemContext systemContext;
    private Manifest manifest;
    private Properties properties = new Properties();
    private Subject subject;

    public PluginContext(SystemContext context, Manifest manifest) {
        this.systemContext = context;
        this.manifest = manifest;
        this.subject = new Subject();
        this.subject.getPrincipals().add(PluginPrincipal.create(manifest.getName(), manifest.getOrg()));
        // TODO: What properties should be added?
    }

    public Optional<Proxy> getProxy(String name) {
        SystemProxy systemProxy = (SystemProxy) systemContext.getProxy(name, subject).orElse(null);
        if (systemProxy == null) {
            return Optional.empty();
        }
        return Optional.of(new PluginProxy(systemProxy, subject));
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger(manifest.getName());
    }

    @Override
    public Properties getProperties() {
        return properties;
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
