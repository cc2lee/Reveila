package reveila.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Logger;

import reveila.util.event.EventWatcher;

/**
 * An interface to abstract the host platform, allowing Reveila to run
 * on different platforms without having to deal with the underlying implementation details.
 */
public interface PlatformAdapter {

    public static final int TEMP_STORAGE = 0;
    public static final int DATA_STORAGE = 1;
    public static final int CONF_STORAGE = 2;
    public static final int TASK_STORAGE = 3;

    public String getHostDescription();
    public Properties getProperties();
    public InputStream getInputStream(int storageType, String path) throws IOException;
    public OutputStream getOutputStream(int storageType, String path) throws IOException;
    public String[] listComponentConfigs() throws IOException;
    public String[] listTaskConfigs() throws IOException;
    public Logger getLogger();
    public void runTask(Runnable task, long delayMillis, long periodMillis, EventWatcher listener);
    public void destruct();

}