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

import reveila.Reveila;
import reveila.error.ConfigurationException;
import reveila.system.Constants;
import reveila.util.ExceptionList;
import reveila.util.event.EventWatcher;
import reveila.util.io.FileUtil;
import reveila.util.task.TaskEvent;

/**
 * An implementation of {@link PlatformAdapter} for the Windows platform.
 */
public class DefaultPlatformAdapter implements PlatformAdapter {

    private Properties properties;
    private Logger logger;
    private int jobThreadPoolSize = 5; // if not set in system.properties, default to 5
    private ScheduledExecutorService scheduler;
    
    public DefaultPlatformAdapter(Properties commandLineArgs) throws Exception {
        super();
        loadProperties(commandLineArgs);
        this.logger = configureLogging(this.properties);
        setURLClassLoader(this.properties);
        String threadPoolSizeStr = this.properties.getProperty(Constants.TASK_THREAD_POOL_SIZE);
        if (threadPoolSizeStr != null && !threadPoolSizeStr.isBlank()) {
            try {
                this.jobThreadPoolSize = Integer.parseInt(threadPoolSizeStr);
            } catch (NumberFormatException e) {
                this.logger.warning("Invalid value for " + Constants.TASK_THREAD_POOL_SIZE + ": " + threadPoolSizeStr + 
                    ". Using default of " + this.jobThreadPoolSize + ".");
            }
        }
        this.logger.info("Task thread pool size set to: " + this.jobThreadPoolSize);
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
        
        URL url;
        String urlStr = jvmArgs.getProperty(Constants.SYSTEM_PROPERTIES_FILE_URL);
        try {
            url = new java.net.URI(urlStr).toURL();
        } catch (Exception e) {
            url = DefaultPlatformAdapter.class.getClassLoader().getResource(Constants.SYSTEM_PROPERTIES_FILE_NAME);
        }

        if (url == null) {
            throw new ConfigurationException(Constants.SYSTEM_PROPERTIES_FILE_NAME + 
                " not found. Please ensure it is available in the classpath or passed in as JVM argument.");
        } else {
            return url.openStream();
        }
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
		Logger newLogger = Logger.getLogger(Reveila.class.getSimpleName());
		newLogger.setUseParentHandlers(false); // Prevent propagation to root

		// Remove default handlers from the root logger to prevent duplicate console output
		Logger rootLogger = Logger.getLogger("");
		for (Handler handler : rootLogger.getHandlers()) {
			rootLogger.removeHandler(handler);
		}

		Handler fileHandler = createLogFileHandler(props);
		newLogger.addHandler(fileHandler);
		setLoggingLevel(newLogger, props);
		return newLogger;
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
        // Schedule the task to run after an initial delay of delaySeconds second,
        // and then repeatedly every periodSeconds seconds.
        scheduler.scheduleAtFixedRate(task, delaySeconds, periodSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                task.run();
                if (listener != null) {
                    try {
                        listener.onEvent(new TaskEvent(task, TaskEvent.COMPLETED, System.currentTimeMillis(),
                                new ExceptionList()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                if (listener != null) {
                    try {
                        listener.onEvent(new TaskEvent(task, TaskEvent.FAILED, System.currentTimeMillis(),
                                new ExceptionList(t)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }, delaySeconds, periodSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void destruct() {
        // Gracefully shut down the job executor pool.
        if (this.scheduler != null) {
            this.scheduler.shutdown();
            try {
                if (!this.scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                this.scheduler.shutdownNow();
            }
        }
        this.scheduler = null;
    }
}