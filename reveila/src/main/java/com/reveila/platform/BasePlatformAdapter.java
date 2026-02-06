package com.reveila.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.Map;
import java.util.stream.Stream;

import com.reveila.error.ConfigurationException;
import com.reveila.error.SystemException;
import com.reveila.event.AutoCallEvent;
import com.reveila.event.EventConsumer;
import com.reveila.system.Constants;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.Reveila;
import com.reveila.util.FileUtil;

/**
 * An implementation of {@link PlatformAdapter} for the Windows platform.
 */
public abstract class BasePlatformAdapter implements PlatformAdapter {

    private Reveila reveila;
    private Properties properties;
    private Logger logger;
    private int jobThreadPoolSize = 1; // Use single thread for serial execution by default
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> autoCallTasks = new ConcurrentHashMap<>();
    private Path systemHome;
    private ClassLoader classLoader;

    public BasePlatformAdapter(Properties commandLineArgs) throws Exception {
        super();
        loadProperties(commandLineArgs);
        setupDirectories();
        configureLogging();
        setupClassLoader();
        createScheduler();
    }

    private void createScheduler() {
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

    private void setupDirectories() throws IOException {
        Files.createDirectories(systemHome);
        Files.createDirectories(systemHome.resolve("configs").resolve("components"));
        Files.createDirectories(systemHome.resolve("data"));
        Files.createDirectories(systemHome.resolve("libs"));
        Files.createDirectories(systemHome.resolve("logs"));
        Files.createDirectories(systemHome.resolve("plugins"));
        Files.createDirectories(systemHome.resolve("temp"));
    }

    @Override
    public String getPlatformDescription() {
        return properties.getProperty(Constants.PLATFORM_OS);
    }

    @Override
    public String[] getConfigFilePaths() throws IOException {
        // 1. Ensure we have a clean, absolute path to the components dir
        Path configDir = this.systemHome.resolve(Constants.CONFIGS_DIR_NAME)
                .resolve("components")
                .toAbsolutePath()
                .normalize();

        // 2. Find the files (assuming FileUtil returns relative paths from configDir)
        String[] files = FileUtil.findRelativePaths(configDir.toString(), ".json");

        // 3. Ensure systemHome is also absolute and normalized for a clean comparison
        Path normalizedHome = this.systemHome.toAbsolutePath().normalize();

        return Stream.of(files)
                .map(file -> {
                    // Resolve the file against the configDir, then normalize
                    Path absoluteFilePath = configDir.resolve(file).normalize();

                    // Relativize from home to the specific file
                    return normalizedHome.relativize(absoluteFilePath).toString();
                })
                .toArray(String[]::new);
    }

    @Override
    public InputStream getFileInputStream(String path) throws IOException {
        Path absolutePath = FileUtil.toSafePath(this.systemHome, path);
        if (!Files.exists(absolutePath)) {
            throw new IOException("File not found: " + absolutePath.toString());
        }

        return Files.newInputStream(absolutePath);
    }

    @Override
    public OutputStream getFileOutputStream(String path, boolean append) throws IOException {
        Path absolutePath = FileUtil.toSafePath(this.systemHome, path);
        if (append) {
            return Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private URL resolveConfigurationResource(String location) throws Exception {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Location string is null or empty");
        }

        // 1. Check if it's already a formal URL (e.g., http://, https://, file:/)
        if (location.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return new URI(location).toURL();
        }

        // 2. Treat as a local File Path
        Path path = Path.of(location).toAbsolutePath().normalize();

        // 3. Convert Path to URL (this automatically handles the 'file:/' prefix)
        return path.toUri().toURL();
    }

    private InputStream openPropertiesStream(Properties jvmArgs) throws IOException, ConfigurationException {

        URL url = null;

        // 1. try JVM argument first
        try {
            url = resolveConfigurationResource(jvmArgs.getProperty(Constants.SYSTEM_PROPERTIES_FILE_NAME));
        } catch (Exception e) {
            // 2. try looking in classpath
            url = BasePlatformAdapter.class.getClassLoader().getResource(Constants.SYSTEM_PROPERTIES_FILE_NAME);
        }

        if (url == null) {
            // 3. last place - home directory
            try {
                url = this.systemHome.resolve(Constants.CONFIGS_DIR_NAME)
                        .resolve(Constants.SYSTEM_PROPERTIES_FILE_NAME).toUri().toURL();
            } catch (Exception e) {
                String message = "SYSTEM STARTUP ERROR: " + Constants.SYSTEM_PROPERTIES_FILE_NAME
                        + "  not found. Please ensure it is passed in as a command line argument in URL format,"
                        + " or included in the classpath, or available in the " + Constants.CONFIGS_DIR_NAME
                        + " directory of the Reveila System Home.";
                System.err.println(message);
                if (logger != null) {
                    logger.severe(message);
                }
                throw new ConfigurationException(message, e, "100");
            }
        }

        return url.openStream();
    }

    @Override
    public void unregisterAutoCall(String componentName) {
        ScheduledFuture<?> task = autoCallTasks.remove(componentName);
        if (task != null) {
            task.cancel(false); // Do not interrupt if running, just don't run again
            logger.info("Unregistered auto-call for component: " + componentName);
        }
    }

    private void loadProperties(Properties jvmArgs) throws IOException, ConfigurationException {
        
        Properties args = (jvmArgs != null) ? jvmArgs : new Properties();
        
        // Resolve SYSTEM_HOME
        String homeValue = args.getProperty(Constants.SYSTEM_HOME);
        if (homeValue == null || homeValue.isBlank()) {
            homeValue = System.getenv("REVEILA_HOME");
            if (homeValue == null || homeValue.isBlank()) {
                homeValue = Paths.get(System.getProperty("user.dir"))
                        .resolve("reveila")
                        .toAbsolutePath()
                        .toString();
            }
            args.setProperty(Constants.SYSTEM_HOME, homeValue);
        }
        this.systemHome = Paths.get(homeValue).toAbsolutePath().normalize();
        
        // Load properties file using Try-with-Resources
        this.properties = new Properties();
        try (InputStream stream = openPropertiesStream(args)) {
            this.properties.load(stream);
        } catch (Exception e) {
            // If the file is missing, we might still continue if defaults are enough
            // but since you throw IOException, we'll keep that contract.
            throw new IOException("Failed to load " + Constants.SYSTEM_PROPERTIES_FILE_NAME, e);
        }

        // Apply command-line overwrites
        this.properties.putAll(args);

        // Set OS Metadata
        if (properties.getProperty(Constants.PLATFORM_OS) == null) {
            properties.setProperty(Constants.PLATFORM_OS, String.format("%s %s (%s)",
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch")));
        }

        // Set Defaults for missing properties
        resolveProperty(Constants.CHARACTER_ENCODING, StandardCharsets.UTF_8.name());
        resolveProperty(Constants.LAUNCH_STRICT_MODE, "true");
    }

    // Helper to keep the main logic readable
    private void resolveProperty(String key, String defaultValue) {
        if (properties.getProperty(key) == null || properties.getProperty(key).isBlank()) {
            properties.setProperty(key, defaultValue);
        }
    }

    private URL[] scanJars(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return new URL[0];
        }

        try (Stream<Path> stream = Files.find(dir, 1,
                (path, attr) -> path.toString().toLowerCase().endsWith(".jar") && attr.isRegularFile())) {

            return stream
                    .map(this::toURL) // Now uses the Path-based version
                    .filter(Objects::nonNull)
                    .toArray(URL[]::new);
        }
    }

    private void setupClassLoader() throws Exception {
        this.classLoader = createClassLoader();
        Thread.currentThread().setContextClassLoader(this.classLoader);
    }

    protected ClassLoader createClassLoader() throws Exception {
        // 1. Validation: Ensure systemHome was initialized in the constructor
        if (this.systemHome == null) {
            throw new SystemException("System Home Path was not initialized.");
        }

        // 2. Define the path for Shared (Common) libraries
        // This folder contains the interfaces/DTOs that all plugins share
        Path libsDir = this.systemHome.resolve(Constants.LIB_DIR_NAME);

        // 3. Ensure the common libs directory exists
        Files.createDirectories(libsDir);

        // 4. Load the Shared API ClassLoader
        // We use the System ClassLoader as the parent for this Common Loader
        URL[] urls = scanJars(libsDir);
        return new URLClassLoader(urls, this.getClass().getClassLoader());
    }

    private URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, "Invalid JAR path: " + path, e);
            return null;
        }
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    private void configureLogging() throws IOException {
        // 0. Set standard format: [Date Time] [Level] Message
        String format = "[%1$tF %1$tT] [%4$-7s] %5$s %n";
        System.setProperty("java.util.logging.SimpleFormatter.format", format);
        Logger rootLogger = Logger.getLogger("");

        // 1. Clean out existing handlers
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // 2. Add the File Handler (Standard for persistence)
        rootLogger.addHandler(createLogFileHandler(properties));

        // 3. Add Console Handler if enabled in properties
        String consoleEnabled = properties.getProperty(Constants.LOG_CONSOLE_ENABLED, "true");
        if (Boolean.parseBoolean(consoleEnabled)) {
            ConsoleHandler console = new ConsoleHandler();
            console.setFormatter(new SimpleFormatter());
            console.setLevel(Level.ALL);
            rootLogger.addHandler(console);
        }

        // 4. Set the global filter level
        setLoggingLevel(rootLogger, properties);
        this.logger = rootLogger;
    }

    private Handler createLogFileHandler(Properties props) throws IOException {
        // 1. Resolve Path using NIO.2
        Path logDir = Paths.get(props.getProperty(Constants.SYSTEM_HOME)).resolve("logs");
        Files.createDirectories(logDir); // Ensure directory exists
        Path logFile = logDir.resolve("reveila.log");

        // 2. Parse Rotation Settings safely
        int limit = parseLogSetting(props.getProperty(Constants.LOG_FILE_SIZE), 0, "size (rotation limit)");
        int count = parseLogSetting(props.getProperty(Constants.LOG_FILE_COUNT), 1, "file count");

        // 3. Initialize FileHandler
        // limit > 0 triggers the rotating file pattern
        Handler handler = (limit > 0)
                ? new FileHandler(logFile.toString(), limit, Math.max(1, count), true)
                : new FileHandler(logFile.toString(), true);

        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(Level.ALL); // Handler accepts all; Root Logger filters based on config
        return handler;
    }

    private int parseLogSetting(String value, int defaultValue, String label) {
        if (value == null || value.isBlank())
            return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Warning: Invalid " + label + " value. Using default: " + defaultValue);
            return defaultValue;
        }
    }

    private void setLoggingLevel(Logger logger, Properties props) {
        String level = props.getProperty(Constants.LOG_LEVEL, "INFO").trim().toUpperCase();
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
    public void registerAutoCall(String componentName, String methodName, long delaySeconds, long intervalSeconds,
            EventConsumer eventConsumer) {

        // Unregister existing task for this component if any
        unregisterAutoCall(componentName);

        // Use scheduleWithFixedDelay for system stability
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            if (reveila == null)
                return;

            try {
                reveila.invoke(componentName, methodName, null);
                AutoCallEvent event = new AutoCallEvent(this, componentName, methodName, AutoCallEvent.COMPLETED,
                        System.currentTimeMillis(), null);
                notifyAutoCallEventListener(eventConsumer, event);

            } catch (Throwable t) {
                logger.log(Level.SEVERE,
                        "Auto-call task failed! Component: " + componentName + ", Method: " + methodName, t);
                AutoCallEvent event = new AutoCallEvent(this, componentName, methodName, AutoCallEvent.FAILED,
                        System.currentTimeMillis(), t);
                notifyAutoCallEventListener(eventConsumer, event);
            }
        }, delaySeconds, intervalSeconds, TimeUnit.SECONDS);

        autoCallTasks.put(componentName, task);
    }

    // Helper method to remove duplication and nesting
    private void notifyAutoCallEventListener(EventConsumer listener, AutoCallEvent event) {
        if (listener == null)
            return;

        try {
            listener.notifyEvent(event);
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Failed to notify auto call event listener registered for component: " + event.getProxyName(), e);
        }
    }

    @Override
    public synchronized void shutdown() {
        logger.info("Platform Adapter shutting down...");
        // Clear all auto-call tasks
        autoCallTasks.values().forEach(task -> task.cancel(true));
        autoCallTasks.clear();

        if (this.scheduler != null) {
            this.scheduler.shutdown(); // Initiates an orderly shutdown in which previously submitted tasks are
                                       // executed, but no new tasks will be accepted.
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
        this.reveila = null;
        logger.info("Platform Adapter shutdown complete.");
    }

    @Override
    public synchronized void plug(Reveila reveila) {
        if (reveila == null) {
            throw new IllegalArgumentException("Reveila cannot be null");
        }

        this.reveila = reveila;
    }
}