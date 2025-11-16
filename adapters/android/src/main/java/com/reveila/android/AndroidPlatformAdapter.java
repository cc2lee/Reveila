package com.reveila.android;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.*;

import reveila.Reveila;
import reveila.error.ConfigurationException;
import reveila.system.PlatformAdapter;
import reveila.system.Constants;
import reveila.system.EventWatcher;
import reveila.util.FileUtil;
import com.reveila.android.ReveilaSetup;

/**
 * An implementation of {@link PlatformAdapter} for the Android environment.
 * It interacts with the Android-specific file system, using the app's private
 * storage and assets folder.
 */
public class AndroidPlatformAdapter implements PlatformAdapter {

    private final Context context;
    private Properties properties;
    private Logger logger;

    public AndroidPlatformAdapter(Context context) throws IOException, ConfigurationException {
        this.context = context;
        // The ReveilaSetup class handles copying assets.
        // We assume it has been run before this adapter is instantiated.
        loadProperties(new Properties()); // Pass empty properties for now
        this.logger = configureLogging(this.properties);
    }

    @Override
    public void runTask(Runnable task, long delayMillis, long periodMillis, EventWatcher listener) {
        // TODO: IMPLEMENT ANDROID SPECIFIC TASK MANAGEMENT
    }

    @Override
    public void destruct() {
        // TODO: IMPLEMENT ANDROID SPECIFIC TASK MANAGEMENT
    }

    @Override
    public String getHostDescription() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    @Override
    public Properties getProperties() {
        return this.properties;
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public String[] listComponentConfigs() throws IOException {
        File componentsDir = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + "components");
        File[] files = FileUtil.listFilesWithExtension(componentsDir.getAbsolutePath(), "json");
        return Arrays.stream(files).map(File::getName).toArray(String[]::new);
    }

    @Override
    public String[] listTaskConfigs() throws IOException {
        File tasksDir = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + "tasks");
        File[] files = FileUtil.listFilesWithExtension(tasksDir.getAbsolutePath(), "json");
        return Arrays.stream(files).map(File::getName).toArray(String[]::new);
    }

    @Override
    public InputStream getInputStream(int storageType, String path) throws IOException {
        File file;
        switch (storageType) {
            case PlatformAdapter.CONF_STORAGE:
                file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + path);
                if (!file.exists() || !file.isFile()) { // Fallback for component configs
                    file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + path);
                }
                break;
            case PlatformAdapter.DATA_STORAGE:
                file = new File(getSystemHome(), "data" + File.separator + path);
                break;
            case PlatformAdapter.TEMP_STORAGE:
                file = new File(getSystemHome(), "temp" + File.separator + path);
                break;
            case PlatformAdapter.TASK_STORAGE:
                file = new File(getSystemHome(), "tasks" + File.separator + path);
                break;
            default:
                throw new IOException("Unsupported storage type: " + storageType);
        }
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        return new FileInputStream(file);
    }

    @Override
    public OutputStream getOutputStream(int storageType, String path) throws IOException {
        File file;
        switch (storageType) {
            case PlatformAdapter.CONF_STORAGE:
                file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + path);
                break;
            case PlatformAdapter.DATA_STORAGE:
                file = new File(getSystemHome(), "data" + File.separator + path);
                break;
            case PlatformAdapter.TEMP_STORAGE:
                file = new File(getSystemHome(), "temp" + File.separator + path);
                break;
            case PlatformAdapter.TASK_STORAGE:
                file = new File(getSystemHome(), "tasks" + File.separator + path);
                break;
            default:
                throw new IOException("Unsupported storage type: " + storageType);
        }
        // Ensure parent directories exist
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        return new FileOutputStream(file);
    }

    /**
     * Gets the absolute path to the Reveila system home directory.
     * This is a static utility method to allow other components to know the directory path.
     */
    public static String getSystemHome(Context context) {
        return new File(context.getFilesDir(), "reveila/system").getAbsolutePath();
    }

    private String getSystemHome() {
        String systemHome = this.properties.getProperty(Constants.SYSTEM_NAME);
        if (systemHome == null || systemHome.isBlank()) {
            // On Android, the home directory is the app's private files directory.
            systemHome = AndroidPlatformAdapter.getSystemHome(context);

            File dir = new File(systemHome);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new RuntimeException("Failed to create home directory: " + systemHome);
            }
            this.properties.setProperty(Constants.SYSTEM_NAME, systemHome);
        }
        return systemHome;
    }

    private InputStream openPropertiesStream() throws IOException {
        // On Android, properties are copied from assets to a fixed location.
        File propertiesFile = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + Constants.SYSTEM_PROPERTIES_FILE_NAME);
        if (!propertiesFile.exists()) {
            throw new IOException(Constants.SYSTEM_PROPERTIES_FILE_NAME + " not found at " + propertiesFile.getAbsolutePath() + ". Ensure ReveilaSetup has run.");
        }
        return new FileInputStream(propertiesFile);
    }

    private void loadProperties(Properties overwrites) throws IOException, ConfigurationException {
        if (overwrites == null) {
            overwrites = new Properties();
        }

        properties = new Properties();
        InputStream stream = null;
        try {
            stream = openPropertiesStream();
            properties.load(stream);
        } catch (IOException e) {
            throw e;
        } finally {
            if (stream != null) {
                stream.close();
            }
        }

        properties.putAll(overwrites);
        getSystemHome(); // This will resolve the system home directory, and set it in the properties.

        // Set default properties if not already set
        properties.setProperty(Constants.PLATFORM_OS, getHostDescription());
        // ... (copy other default property settings from WindowsPlatformAdapter if needed)
    }

    // This is a simplified logging configuration for Android.
    // For production apps, consider using a dedicated Android logging library like Timber.
    private Logger configureLogging(Properties props) throws IOException {
        String loggerName = props.getProperty(Constants.LOGGER_NAME, "reveila");
        Logger newLogger = Logger.getLogger(loggerName);
        newLogger.setUseParentHandlers(false); // Prevent propagation to root

        // On Android, it's often better to log to Logcat than to a file.
        // We'll use a custom handler for that.
        Handler logcatHandler = new Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record == null) {
                    return;
                }
                String tag = record.getLoggerName();
                String msg = getFormatter().format(record);
                int level = record.getLevel().intValue();

                if (level >= Level.SEVERE.intValue()) {
                    android.util.Log.e(tag, msg);
                } else if (level >= Level.WARNING.intValue()) {
                    android.util.Log.w(tag, msg);
                } else if (level >= Level.INFO.intValue()) {
                    android.util.Log.i(tag, msg);
                } else {
                    android.util.Log.d(tag, msg);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };

        logcatHandler.setFormatter(new SimpleFormatter());
        newLogger.addHandler(logcatHandler);

        setLoggingLevel(newLogger, props);
        newLogger.info("Logging configured to use Android Logcat.");
        return newLogger;
    }

    private void setLoggingLevel(Logger logger, Properties props) {
        String levelStr = props.getProperty(Constants.LOG_LEVEL, "INFO").trim().toUpperCase();
        try {
            logger.setLevel(Level.parse(levelStr));
        } catch (Exception e) {
            logger.setLevel(Level.INFO); // Default to INFO for Android
        }
    }
}