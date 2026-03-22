package com.reveila.system;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;

public final class PluginContext implements Context {

    private SystemContext systemContext;
    private Manifest manifest;
    private Properties properties = new Properties();

    public PluginContext(SystemContext context, Manifest manifest) {
        this.systemContext = context;
        this.manifest = manifest;

        // TODO: load plugin properties
    }

    public Optional<Proxy> getProxy(String name) {
        return this.systemContext.getProxy(name, manifest);
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
