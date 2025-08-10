package reveila.platform;

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

    public static final int WINDOWS = 0;
    public static final int MAC = 1;
    public static final int LINUX = 2;
    public static final int UNIX = 3;
    public static final int ANDROID = 4;
    public static final int IOS = 5;
    public static final int IPAD = 6;

    public static final int TEMP_STORAGE = 0;
    public static final int DATA_STORAGE = 1;
    public static final int CONF_STORAGE = 2;
    public static final int TASK_STORAGE = 3;

    public int getHostType();
    public String getHostDescription();
    public Properties getProperties();
    public InputStream getInputStream(int storageType, String path) throws IOException;
    public OutputStream getOutputStream(int storageType, String path) throws IOException;
    public String[] listComponentConfigs() throws IOException;
    public String[] listTaskConfigs() throws IOException;
    public Logger getLogger();
}