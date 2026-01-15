package com.reveila.system;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;

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
    public void registerTask(Runnable task, long delayMillis, long periodMillis, EventConsumer eventConsumer);
    public void unplug();

}