package com.reveila.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;

import javax.security.auth.Subject;

import com.reveila.data.Entity;
import com.reveila.data.Repository;
import com.reveila.error.ConfigurationException;
import com.reveila.error.SystemException;
import com.reveila.event.AutoCallEvent;
import com.reveila.event.EventConsumer;
import com.reveila.util.FileUtil;

/**
 * An implementation of {@link PlatformAdapter} for the Windows platform.
 */
public abstract class BasePlatformAdapter implements PlatformAdapter {

    public Map<String, Repository<Entity, Map<String, Map<String, Object>>>> getRepositories() {
        return repositories;
    }

    private String platformName;
    private Reveila reveila;
    private Properties properties = new Properties();
    protected Logger logger;
    private int jobThreadPoolSize = 4; // Allow concurrent execution to prevent startup deadlocks
    private ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> autoCallTasks = new ConcurrentHashMap<>();
    private SystemHome systemHome;
    private ClassLoader classLoader;
    private final Map<String, Repository<Entity, Map<String, Map<String, Object>>>> repositories = new ConcurrentHashMap<>();

    public BasePlatformAdapter(Properties commandLineArgs) throws Exception {
        super();
        setupSystemHome(commandLineArgs);
        loadProperties(commandLineArgs);
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

    @Override
    public String getPlatformDescription() {
        return properties.getProperty(Constants.PLATFORM_OS);
    }

    @Override
    public String[] listRelativePaths(String relativeDirectory, String ext) throws IOException {
        if (relativeDirectory == null || relativeDirectory.trim().isEmpty()) {
            throw new IOException("Manifests directory name not specified.");
        }
        // 1. Ensure we have a clean, absolute path to the components dir
        Path dir = this.systemHome.getSystemHome()
                .resolve(relativeDirectory)
                .toAbsolutePath()
                .normalize();

        if (!Files.exists(dir)) {
            logger.warning("Directory does not exist: " + dir + ". Returning empty file list.");
            return new String[0];
        }

        // 2. Find the files (assuming FileUtil returns relative paths from configDir)
        String[] files = FileUtil.listRelativePaths(dir.toString(), ext);

        // 3. Ensure systemHome is also absolute and normalized for a clean comparison
        Path home = this.systemHome.getSystemHome().toAbsolutePath().normalize();

        return Stream.of(files)
                .map(file -> {
                    // Resolve the file against the configDir, then normalize
                    Path absoluteFilePath = dir.resolve(file).normalize();

                    // Relativize from home to the specific file
                    return home.relativize(absoluteFilePath).toString();
                })
                .toArray(String[]::new);
    }

    @Override
    public InputStream getFileInputStream(String path) throws IOException {
        Path absolutePath = FileUtil.toSafePath(this.systemHome.getSystemHome(), path);
        if (!Files.exists(absolutePath)) {
            throw new IOException("File not found: " + absolutePath.toString());
        }

        return Files.newInputStream(absolutePath);
    }

    @Override
    public OutputStream getFileOutputStream(String path, boolean append) throws IOException {
        Path absolutePath = FileUtil.toSafePath(this.systemHome.getSystemHome(), path);
        if (append) {
            return Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            return Files.newOutputStream(absolutePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    @Override
    public void unregisterAutoCall(String componentName) {
        ScheduledFuture<?> task = autoCallTasks.remove(componentName);
        if (task != null) {
            task.cancel(false); // Do not interrupt if running, just don't run again
            logger.info("Unregistered auto-call for component: " + componentName);
        }
    }

    @Override
    public void loadProperties(Properties jvmArgs) throws IOException {

        String filename = "reveila.properties";
        Properties rawProps = new Properties();

        // 1. Load common properties
        String path = Constants.CONFIGS_DIR_NAME + File.separator + filename;
        try (InputStream in = getFileInputStream(path)) {
            rawProps.load(in);
        } catch (Exception e) {
            throw new IOException("Failed to load system properties from " + path, e);
        }

        // 2. Load platform-specific properties
        path = Constants.CONFIGS_DIR_NAME + File.separator + getPlatformName() + File.separator + filename;
        if (Files.exists(FileUtil.toSafePath(this.systemHome.getSystemHome(), path))) {
            try (InputStream in = getFileInputStream(path)) {
                rawProps.load(in);
            } catch (Exception e) {
                throw new IOException("Failed to load system properties from " + path, e);
            }
        }

        // Apply command-line overwrites to raw properties before resolution
        if (jvmArgs != null) rawProps.putAll(jvmArgs);

        // Resolve placeholders in all properties
        this.properties.putAll(resolveAllPlaceholders(rawProps));

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

    @Override
    public String getPlatformName() {
        return this.platformName;
    }

    public void setPlatformName(String platformName) {
        if (platformName == null || platformName.isBlank()) {
            throw new IllegalArgumentException("Platform name cannot be null or blank");
        }
        this.platformName = platformName;
    }

    private Properties resolveAllPlaceholders(Properties props) {
        Properties resolved = new Properties();
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            resolved.setProperty(key, resolveValue(value, props));
        }
        return resolved;
    }

    private String resolveValue(String value, Properties props) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int start = value.indexOf("${", cursor);
            if (start == -1) {
                result.append(value.substring(cursor));
                break;
            }
            result.append(value.substring(cursor, start));
            int end = value.indexOf("}", start);
            if (end == -1) {
                result.append(value.substring(start));
                break;
            }

            String placeholder = value.substring(start + 2, end);
            String defaultValue = null;
            int separator = placeholder.indexOf(":");

            // Unified Placeholder Support: Do not resolve 'secret:' placeholders during
            // initial property loading.
            if (placeholder.startsWith("secret:")) {
                result.append("${").append(placeholder).append("}");
                cursor = end + 1;
                continue;
            }

            if (separator != -1) {
                defaultValue = placeholder.substring(separator + 1);
                placeholder = placeholder.substring(0, separator);
            }

            // Lookup order: System Property -> Environment Variable -> Other Properties in
            // file -> Default
            String resolvedValue = System.getProperty(placeholder);
            if (resolvedValue == null) {
                resolvedValue = System.getenv(placeholder);
            }
            if (resolvedValue == null) {
                resolvedValue = props.getProperty(placeholder);
            }
            if (resolvedValue == null) {
                resolvedValue = defaultValue;
            }

            if (resolvedValue != null) {
                result.append(resolvedValue);
            } else {
                result.append("${").append(placeholder).append("}");
            }
            cursor = end + 1;
        }
        return result.toString();
    }

    private void setupSystemHome(Properties jvmArgs) throws ConfigurationException {
        // Resolve SYSTEM_HOME
        String path = jvmArgs.getProperty(Constants.SYSTEM_HOME);
        if (path == null || path.isBlank()) {
            path = System.getenv("REVEILA_HOME");
            if (path == null || path.isBlank()) {
                throw new ConfigurationException(
                        "SYSTEM STARTUP ERROR: System Home directory not specified. Please set the "
                                + Constants.SYSTEM_HOME
                                + " system property as a command line argument, or the REVEILA_HOME environment variable.",
                        null, "101");
            }
            jvmArgs.setProperty(Constants.SYSTEM_HOME, path);
        }

        // Initialize System Home
        boolean resetHome = "true".equalsIgnoreCase(jvmArgs.getProperty(Constants.RESET_HOME));
        this.systemHome = new SystemHome(path);
        this.systemHome.createDirectoryStructure(resetHome);
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
        logger.info("Initializing Shared ClassLoader with " + urls.length + " JARs from " + libsDir);
        for (URL url : urls) {
            logger.info("  -> " + url);
        }

        // 5. Create the Common ClassLoader
        if ("android".equalsIgnoreCase(this.properties.getProperty(Constants.PLATFORM))) {
            logger.info("Android platform detected. Creating DexClassLoader for shared libraries.");
            return RuntimeUtil.createPluginClassLoader(libsDir.toString(), this.getClass().getClassLoader());
        }
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

    protected Path getSystemHome() {
        if (this.systemHome == null) {
            return null;
        }
        return this.systemHome.getSystemHome();
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    private void configureLogging() throws IOException {
        // %1=Date, %2=Source, %3=Logger Name, %4=Level, %5=Message, %6=Throwable
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%1$tF %1$tT] [Reveila] [%3$s] %4$s: %5$s%n");

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
            // Use a custom StreamHandler pointing to System.out to prevent INFO logs going to System.err
            Handler console = new java.util.logging.StreamHandler(System.out, new SimpleFormatter()) {
                @Override
                public synchronized void publish(java.util.logging.LogRecord record) {
                    super.publish(record);
                    flush();
                }
                @Override
                public synchronized void close() throws SecurityException {
                    flush();
                }
            };
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
            if (logger != null) {
                logger.warning("Invalid " + label + " value '" + value + "'. Using default: " + defaultValue);
            } else {
                System.err.println("Warning: Invalid " + label + " value '" + value + "'. Using default: " + defaultValue);
            }
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
            EventConsumer eventConsumer, Subject subject) throws Exception {

        // Unregister existing task for this component if any
        unregisterAutoCall(componentName);

        // Use scheduleWithFixedDelay for system stability
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            if (reveila == null)
                return;

            try {
                // String componentName, String methodName, Object[] params, String callerIp,
                // Subject subject
                reveila.invoke(componentName, methodName, null, "localhost", subject);
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
    public synchronized void unplug() {
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
    public ExecutorService getExecutor() {
        return scheduler;
    }

    @Override
    public Repository<Entity, Map<String, Map<String, Object>>> getRepository(String entityType) {
        if (entityType == null) {
            return null;
        }
        return repositories.get(entityType.toLowerCase());
    }

    public void registerRepository(String entityType, Repository<Entity, Map<String, Map<String, Object>>> repository) {
        if (entityType != null && repository != null) {
            repositories.put(entityType.toLowerCase(), repository);
            logger.info("Registered repository for entity: " + entityType);
        }
    }

    @Override
    public synchronized void plug(Reveila reveila) {
        if (reveila == null) {
            throw new IllegalArgumentException("Reveila cannot be null");
        }

        this.reveila = reveila;
    }
}