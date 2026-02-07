package com.reveila.system;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.event.EventConsumer;

/**
 * An interface to abstract the host platform, allowing Reveila to run
 * on different platforms without having to deal with the underlying implementation details.
 */
public interface PlatformAdapter {

    public String getPlatformDescription();
    public Properties getProperties();
    public InputStream getFileInputStream(String relativePath) throws IOException;
    public OutputStream getFileOutputStream(String relativePath, boolean append) throws IOException;
    public String[] getConfigFilePaths() throws IOException;
    public Logger getLogger();
    public void registerAutoCall(String componentName, String methodName, long delaySeconds, long intervalSeconds, EventConsumer eventConsumer) throws Exception;
    public void unregisterAutoCall(String componentName);
    public void plug(Reveila reveila);
    public void unplug();
    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType);

}