package com.reveila.system;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.reveila.error.ConfigurationException;
import com.reveila.error.ExceptionList;
import com.reveila.service.task.TaskEvent;
import com.reveila.util.FileUtil;

/**
 * An implementation of {@link PlatformAdapter} for the Windows platform.
 */
public class DefaultPlatformAdapter implements PlatformAdapter {

    private Properties properties;
    private Logger logger;
    private int jobThreadPoolSize = 1; // Use single thread for serial execution by default
    private ScheduledExecutorService scheduler;
    
    public DefaultPlatformAdapter(Properties commandLineArgs) throws Exception {
        super();
        loadProperties(commandLineArgs);
        this.logger = configureLogging(this.properties);
        setURLClassLoader(this.properties);
        
        // ThreadFactory with named worker threads for easier debugging
        final ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Task Executor - " + count.incrementAndGet());
            }
        };
        this.scheduler = Executors.newScheduledThreadPool(jobThreadPoolSize, threadFactory);
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
        String systemHome = this.properties.getProperty(Constants.SYSTEM_HOME);
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
            this.properties.setProperty(Constants.SYSTEM_HOME, systemHome);
        }
        return systemHome;
    }

    public InputStream openPropertiesStream(Properties jvmArgs) throws IOException, ConfigurationException {
        
        URL url = null;

        // 1. try JVM argument first
        try {
            url = new java.net.URI(jvmArgs.getProperty(Constants.SYSTEM_PROPERTIES_FILE_NAME)).toURL();
        } catch (Exception e) {
            // 2. try looking in classpath
            url = DefaultPlatformAdapter.class.getClassLoader().getResource(Constants.SYSTEM_PROPERTIES_FILE_NAME);
        }

        if (url == null) {
            // 3. last place - home directory
            String path = getSystemHome() + File.separator + Constants.CONFIGS_DIR_NAME + File.separator + Constants.SYSTEM_PROPERTIES_FILE_NAME;
            File file = new File(path);
            try {
                url = file.toURI().toURL();
            } catch (Exception e) {
                String message = "ERROR: " + Constants.SYSTEM_PROPERTIES_FILE_NAME 
                    + " URL not specified as a command line argument, and not found in the classpath or in the system home directory: " + path;
                System.out.println(message);
                if (logger != null) {
                    logger.severe(message);
                }
                throw new ConfigurationException(Constants.SYSTEM_PROPERTIES_FILE_NAME + " not found. "
                    + "Please ensure it is passed in as a command line argument, or included in the classpath, or available in the configs directory of the Reveila System Home.");
            }
        }

        return url.openStream();
    }

    private void loadProperties(Properties overwrites) throws IOException, ConfigurationException {
        if (overwrites == null) {
            overwrites = new Properties();
        }

        // Load properties from the reveila.properties file
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
        String value = properties.getProperty(Constants.SYSTEM_HOME);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.SYSTEM_HOME, getSystemHome());
        }
        value = properties.getProperty(Constants.PLATFORM_OS);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.PLATFORM_OS, getHostDescription());
        }
        value = properties.getProperty(Constants.CHARACTER_ENCODING);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.CHARACTER_ENCODING, StandardCharsets.UTF_8.name());
        }
        value = properties.getProperty(Constants.LAUNCH_STRICT_MODE);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.LAUNCH_STRICT_MODE, "true");
        }
        value = properties.getProperty(Constants.SYSTEM_DATA_FILE_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.SYSTEM_DATA_FILE_DIR, getSystemHome() + File.separator + "data" /*System.getProperty("user.home") + File.separator + "reveila" + File.separator + "data"*/ );
        }
        value = properties.getProperty(Constants.SYSTEM_TEMP_FILE_DIR);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.SYSTEM_TEMP_FILE_DIR, getSystemHome() + File.separator + "temp" /*System.getProperty("java.io.tmpdir") + File.separator + "reveila" + File.separator + "temp"*/ );
        }
        value = properties.getProperty(Constants.SYSTEM_PROPERTIES_FILE_NAME);
        if (value == null || value.isBlank()) {
            properties.setProperty(Constants.SYSTEM_PROPERTIES_FILE_NAME, Constants.SYSTEM_PROPERTIES_FILE_NAME);
        }
    }

    private InputStream openConfigInputStream(String fileName) throws IOException {
        File file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + fileName);
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
        File file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + fileName);
        if (!file.exists() || !file.isFile()) {
            file = new File(getSystemHome(), Constants.CONFIGS_DIR_NAME + File.separator + "components" + File.separator + fileName);
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
		String systemHome = props.getProperty(Constants.SYSTEM_HOME);
		if (systemHome == null || systemHome.isBlank()) {
			// This case should not be reachable as prepareRuntimeDirectories sets the property.
			// Throwing an exception here is a safe fallback.
			throw new ConfigurationException("System property '" + Constants.SYSTEM_HOME + "' was not set during directory preparation.");
		}

		File libDir = new File(systemHome, Constants.LIB_DIR_NAME);
		File[] jarFiles = libDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
		if (jarFiles == null) {
			throw new ConfigurationException("Could not list files in lib directory: " + libDir.getAbsolutePath());
		}

		URL[] urlArray = Arrays.stream(jarFiles)
				.map(this::toURL)
				.filter(Objects::nonNull)
				.toArray(URL[]::new);

		URLClassLoader classLoader = new URLClassLoader(urlArray, this.getClass().getClassLoader());
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
		Logger logger = Logger.getLogger("");
		for (Handler handler : logger.getHandlers()) {
			logger.removeHandler(handler);
		}

		Handler fileHandler = createLogFileHandler(props);
		logger.addHandler(fileHandler);
		setLoggingLevel(logger, props);
		return logger;
	}

    private Handler createLogFileHandler(Properties props) throws IOException {
        String logFilePath = props.getProperty(Constants.SYSTEM_HOME) + File.separator + "logs" + File.separator
                + "reveila.log";
        File logFile = new File(logFilePath);
        File logDir = logFile.getParentFile();
        if (logDir != null && !logDir.exists()) {
            logDir.mkdirs();
        }

        String limitStr = props.getProperty(Constants.LOG_FILE_SIZE, "0");
        String countStr = props.getProperty(Constants.LOG_FILE_COUNT, "1");
        int limit = 0;
        int count = 1;
        try {
            limit = Integer.parseInt(limitStr);
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid value for '" + Constants.LOG_FILE_SIZE
                    + "'. Using 0 (disabling rotation).");
        }
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            System.err.println(
                    "Warning: Invalid value for '" + Constants.LOG_FILE_COUNT + "'. Using default of 1.");
        }

        Handler handler = (limit > 0)
                ? new FileHandler(logFilePath, limit, Math.max(1, count), true)
                : new FileHandler(logFilePath, true);

        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.ALL);
        return handler;
    }

    private void setLoggingLevel(Logger logger, Properties props) {
		String level = props.getProperty(Constants.LOG_LEVEL, "ALL").trim().toUpperCase();
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

    @Override
    public void runTask(Runnable task, long delaySeconds, long periodSeconds, EventWatcher listener) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                task.run();
                if (listener != null) {
                    try {
                        listener.onEvent(new TaskEvent(task, TaskEvent.COMPLETED, System.currentTimeMillis(), new ExceptionList()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
                if (listener != null) {
                    try {
                        listener.onEvent(new TaskEvent(task, TaskEvent.FAILED, System.currentTimeMillis(), new ExceptionList(t)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, delaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void destruct() {
        if (this.scheduler != null) {
            this.scheduler.shutdown(); // Initiates an orderly shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.
            try {
                if (!this.scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.scheduler.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve interrupt status
                System.out.println("Thread interrupted while waiting for Task Executor termination.");
            }
        }
        this.scheduler = null;
    }
}