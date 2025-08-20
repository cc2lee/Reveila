package reveila.platform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import reveila.Reveila;
import reveila.error.ConfigurationException;
import reveila.system.Constants;
import reveila.util.io.FileUtil;

/**
 * An implementation of {@link PlatformAdapter} for the Windows platform.
 */
public class DefaultPlatformAdapter implements PlatformAdapter {

    private Properties properties;
    private Logger logger;

    public DefaultPlatformAdapter(Properties commandLineArgs) throws Exception {
        super();
        loadProperties(commandLineArgs);
        this.logger = configureLogging(this.properties);
        setURLClassLoader(this.properties);
    }

    @Override
    public int getHostType() {
        return PlatformAdapter.WINDOWS;
    }

    @Override
    public String getHostDescription() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArchitecture = System.getProperty("os.arch");
        return osName + " " + osVersion + " (" + osArchitecture + ")";
    }

    @Override
    public String[] listComponentConfigs() throws IOException {
        File componentsDir = new File(getSystemHome(), "configs" + File.separator + "components");
        File[] files = FileUtil.listFilesWithExtension(componentsDir.getAbsolutePath(), "json");
        return Arrays.stream(files).map(File::getName).toArray(String[]::new);
    }

    @Override
    public String[] listTaskConfigs() throws IOException {
        File tasksDir = new File(getSystemHome(), "configs" + File.separator + "tasks");
        File[] files = FileUtil.listFilesWithExtension(tasksDir.getAbsolutePath(), "json");
        return Arrays.stream(files).map(File::getName).toArray(String[]::new);
    }

    @Override
    public InputStream getInputStream(int storageType, String path) throws IOException {
        if (storageType == PlatformAdapter.CONF_STORAGE) {
            return openConfigInputStream(path);
        } else if (storageType == PlatformAdapter.DATA_STORAGE) {
            return openDataInputStream(path);
        } else if (storageType == PlatformAdapter.TEMP_STORAGE) {
            return openTempDataInputStream(path);
        } else {
            throw new IOException("Unsupported storage type: " + storageType);
        }
    }

    @Override
    public OutputStream getOutputStream(int storageType, String path) throws IOException {
        if (storageType == PlatformAdapter.CONF_STORAGE) {
            return openConfigOutputStream(path);
        } else if (storageType == PlatformAdapter.DATA_STORAGE) {
            return openDataOutputStream(path);
        } else if (storageType == PlatformAdapter.TEMP_STORAGE) {
            return openTempDataOutputStream(path);
        } else {
            throw new IOException("Unsupported storage type: " + storageType);
        }
    }

    private String getSystemHome() {
        String systemHome = this.properties.getProperty(Constants.S_SYSTEM_HOME);
        if (systemHome == null || systemHome.isBlank()) {
            systemHome = System.getenv("REVEILA_HOME");
            if (systemHome == null || systemHome.isBlank()) {
                systemHome = System.getProperty("user.dir") + File.separator + "reveila";
            }
            
            File dir = new File(systemHome);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new RuntimeException("Failed to create home directory: " + systemHome);
                }
            }
            this.properties.setProperty(Constants.S_SYSTEM_HOME, systemHome);
        }
        return systemHome;
    }

    public InputStream openPropertiesStream(Properties jvmArgs) throws IOException, ConfigurationException {
        
        URL url;
        String urlStr = jvmArgs.getProperty(Constants.S_SYSTEM_PROPERTIES_URL);
        try {
            url = new java.net.URI(urlStr).toURL();
        } catch (Exception e) {
            url = DefaultPlatformAdapter.class.getClassLoader().getResource(Constants.S_SYSTEM_PROPERTIES_FILE_NAME);
        }

        if (url == null) {
            throw new ConfigurationException("Could not find the system.properties file. Please ensure it is available in the classpath or specified URL.");
        } else {
            return url.openStream();
        }
    }

    private void loadProperties(Properties overwrites) throws IOException, ConfigurationException {
        if (overwrites == null) {
            overwrites = new Properties();
        }

        // Load properties from the system.properties file
        properties = new Properties();

		InputStream stream = null;
		try {
            stream = openPropertiesStream(overwrites);
			properties.load(stream);
		} catch (IOException e) {
			throw e;
		} finally {
            if (stream != null) {
                stream.close();
            }
        }

		properties.putAll(overwrites);

        // Set default properties if not already set
        String value = properties.getProperty(Constants.S_SYSTEM_HOME);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_HOME, getSystemHome());
        }
        value = properties.getProperty(Constants.S_SYSTEM_OS);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_OS, getHostDescription());
        }
        value = properties.getProperty(Constants.S_SYSTEM_CHARSET);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_CHARSET, StandardCharsets.UTF_8.name());
        }
        value = properties.getProperty(Constants.S_SYSTEM_VERSION);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_VERSION, "1.0.0");
        }
        value = properties.getProperty(Constants.S_SYSTEM_STRICT_MODE);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_STRICT_MODE, "true");
        }
        value = properties.getProperty(Constants.S_SYSTEM_LOGGER_NAME);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_LOGGER_NAME, "reveila");
        }
        value = properties.getProperty(Constants.S_SYSTEM_LIB_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_LIB_DIR, getSystemHome() + File.separator + "libs" /*System.getProperty("java.library.path")*/ );
        }
        value = properties.getProperty(Constants.S_SYSTEM_DATA_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_DATA_DIR, getSystemHome() + File.separator + "data" /*System.getProperty("user.home") + File.separator + "reveila" + File.separator + "data"*/ );
        }
        value = properties.getProperty(Constants.S_SYSTEM_TMP_DATA_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_TMP_DATA_DIR, getSystemHome() + File.separator + "temp" /*System.getProperty("java.io.tmpdir") + File.separator + "reveila" + File.separator + "temp"*/ );
        }
        value = properties.getProperty(Constants.S_SYSTEM_LOG_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_LOG_DIR, getSystemHome() + File.separator + "logs" /*System.getProperty("user.home") + File.separator + "reveila" + File.separator + "logs"*/ );
        }
        value = properties.getProperty(Constants.S_SYSTEM_CONFIGURATION_FILE_NAME);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.S_SYSTEM_CONFIGURATION_FILE_NAME, "system.properties");
        }
        value = properties.getProperty(Constants.S_SYSTEM_CONFIGURATION_FILE_URL);
        if (value == null || value.isBlank()) {
            File f = new File(getSystemHome(), "configs" + File.separator + properties.getProperty(Constants.S_SYSTEM_CONFIGURATION_FILE_NAME));
            properties.setProperty(Constants.S_SYSTEM_CONFIGURATION_FILE_URL, f.toURI().toURL().toString());
        }
    }

    private InputStream openConfigInputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "configs" + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            file = new File(getSystemHome(), "configs" + File.separator + "components" + File.separator + fileName);
        }

        if (!file.exists() || !file.isFile()) {
            throw new IOException("Configuration file not found: " + fileName);
        }
        
        return new FileInputStream(file);
    }

    private InputStream openDataInputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "data" + File.separator + fileName);
        return new FileInputStream(file);
    }

    private InputStream openTempDataInputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "temp" + File.separator + fileName);
        return new FileInputStream(file);
    }

    private OutputStream openConfigOutputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "configs" + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            file = new File(getSystemHome(), "configs" + File.separator + "components" + File.separator + fileName);
        }

        if (!file.exists() || !file.isFile()) {
            throw new IOException("Configuration file not found: " + fileName);
        }
        
        return new FileOutputStream(file);
    }

    private OutputStream openDataOutputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "data" + File.separator + fileName);
        return new FileOutputStream(file);
    }

    private OutputStream openTempDataOutputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), "temp" + File.separator + fileName);
        return new FileOutputStream(file);
    }

    private void setURLClassLoader(Properties props) throws ConfigurationException {
		String systemHome = props.getProperty(Constants.S_SYSTEM_HOME);
		if (systemHome == null || systemHome.isBlank()) {
			// This case should not be reachable as prepareRuntimeDirectories sets the property.
			// Throwing an exception here is a safe fallback.
			throw new ConfigurationException("System property '" + Constants.S_SYSTEM_HOME + "' was not set during directory preparation.");
		}

		File libDir = new File(systemHome, Constants.C_LIB_DIR_NAME);
		File[] jarFiles = libDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
		if (jarFiles == null) {
			throw new ConfigurationException("Could not list files in lib directory: " + libDir.getAbsolutePath());
		}

		URL[] urlArray = Arrays.stream(jarFiles)
				.map(this::toURL)
				.filter(Objects::nonNull)
				.toArray(URL[]::new);

		URLClassLoader classLoader = new URLClassLoader(urlArray, Reveila.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);
	}

    /**
	 * Converts a File object to a URL, handling potential exceptions.
	 * @param file The file to convert.
	 * @return The corresponding URL, or null if conversion fails.
	 */
	private URL toURL(File file) {
		try {
			return file.toURI().toURL();
		} catch (Exception e) {
			// This is unlikely to happen with a valid File object but is handled for safety.
			logger.log(Level.WARNING, "Could not convert file to URL, skipping: " + file.getAbsolutePath(), e);
			return null;
		}
	}

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    private Logger configureLogging(Properties props) throws IOException {
		String loggerName = props.getProperty(Constants.S_SYSTEM_LOGGER_NAME, "reveila");
		Logger newLogger = Logger.getLogger(loggerName);
		newLogger.setUseParentHandlers(false); // Prevent propagation to root

		// Remove default handlers from the root logger to prevent duplicate console output
		Logger rootLogger = Logger.getLogger("");
		for (Handler handler : rootLogger.getHandlers()) {
			rootLogger.removeHandler(handler);
		}

		Handler fileHandler = createLogFileHandler(props);
		newLogger.addHandler(fileHandler);

		setLoggingLevel(newLogger, props);

		// Final log message to confirm configuration
		String logFilePath = props.getProperty(Constants.S_SYSTEM_LOGGING_FILE);
		newLogger.info("Logging set to: " + new File(logFilePath).getAbsolutePath() + ", level=" + newLogger.getLevel());

		return newLogger;
	}

    private Handler createLogFileHandler(Properties props) throws IOException {
		String logFilePath = props.getProperty(Constants.S_SYSTEM_LOGGING_FILE);
		if (logFilePath == null || logFilePath.isBlank()) {
			logFilePath = props.getProperty(Constants.S_SYSTEM_HOME) + File.separator + "logs" + File.separator + "default.log";
			props.setProperty(Constants.S_SYSTEM_LOGGING_FILE, logFilePath);
		}

		File logFile = new File(logFilePath.trim());
		File logDir = logFile.getParentFile();
		if (logDir != null && !logDir.exists()) {
			logDir.mkdirs();
		}

		String limitStr = props.getProperty(Constants.S_SYSTEM_LOGGING_FILE_LIMIT, "0");
		String countStr = props.getProperty(Constants.S_SYSTEM_LOGGING_FILE_COUNT, "1");
		int limit = 0;
		int count = 1;
		try {
			limit = Integer.parseInt(limitStr);
		} catch (NumberFormatException e) {
			System.err.println("Warning: Invalid value for '" + Constants.S_SYSTEM_LOGGING_FILE_LIMIT + "'. Using 0 (disabling rotation).");
		}
		try {
			count = Integer.parseInt(countStr);
		} catch (NumberFormatException e) {
			System.err.println("Warning: Invalid value for '" + Constants.S_SYSTEM_LOGGING_FILE_COUNT + "'. Using default of 1.");
		}

		Handler handler = (limit > 0)
				? new FileHandler(logFilePath, limit, Math.max(1, count), true)
				: new FileHandler(logFilePath, true);

		handler.setFormatter(new SimpleFormatter());
		handler.setLevel(Level.ALL);
		return handler;
	}

    private void setLoggingLevel(Logger logger, Properties props) {
		String level = props.getProperty(Constants.S_SYSTEM_LOGGING_LEVEL, "ALL").trim().toUpperCase();
		try {
			logger.setLevel(Level.parse(level));
		} catch (IllegalArgumentException e) {
			logger.setLevel(Level.ALL); // Default to ALL if the level string is invalid
		}
	}

    @Override
    public Properties getProperties() {
        return this.properties;
    }
}