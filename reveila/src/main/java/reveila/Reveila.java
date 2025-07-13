/**
 * @author Charles Lee
 */
package reveila;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import reveila.crypto.DefaultCryptographer;
import reveila.error.ConfigurationException;
import reveila.system.Constants;
import reveila.system.JsonConfiguration;
import reveila.system.Logo;
import reveila.system.MetaObject;
import reveila.system.Proxy;
import reveila.system.SystemContext;
import reveila.util.RuntimeUtil;
import reveila.util.TimeFormat;
import reveila.util.event.EventManager;
import reveila.util.io.FileManager;
import reveila.util.io.FileUtil;

public final class Reveila {

	private Properties properties;
	private SystemContext systemContext;
	private final Map<Object, FileManager> fileManagers = Collections.synchronizedMap(new HashMap<>());
	private EventManager eventManager;
	private Logger logger;

	public SystemContext getSystemContext() {
		return systemContext;
	}

	private void shutdown() {
		synchronized (this) {
			logOrPrint("\n\nShutting down system...", Level.INFO);
			boolean error = false;

			if (this.systemContext != null) {
				try {
					systemContext.destruct();
				} catch (Exception e) {					
					error = true;
					logOrPrint("System shutdown failed: " + e.getMessage(), Level.SEVERE, e);
				}
			}

			if (!error) {
				logOrPrint("System shut down successfully\n\n", Level.INFO);
			}
		}
	}


	/**
	 * By default, this method looks for the "system.properties" file
	 * on the classpath to load the system properties.
	 * Alternatively, a command-line argument can be specified, in the form of
	 * "system.init.url=url", to load system properties
	 * from the URL specified. Any system property can also be specified as
	 * commmand-line argument in the same format using the keys defined in the
	 * system.properties file, in this case, the value from the command-line will
	 * overwrite the value defined in the system.properties file.
	 */
	public void start(String[] args) throws Exception {
		
		RuntimeUtil.addShutdownHook(this::shutdown);
		Logo.print();
		
		this.properties = loadProperties(processArguments(args));
		this.logger = configureLogging(this.properties);
		initializeClassLoader(this.properties);

		String displayName = getDisplayName(this.properties);

		logger.info("Starting " + displayName);
		long beginTime = System.currentTimeMillis();

		initializeSystemContext(this.properties);
		loadComponents(this.properties);

		long msecs = System.currentTimeMillis() - beginTime;
		String timeUsed = TimeFormat.getInstance().format(msecs);
		logger.info(displayName + " started successfully. Time taken = " + timeUsed);
		System.out.println();
		System.out.println();
	}

	private Properties processArguments(String[] args) throws ConfigurationException {
		Properties cmdArgs = new Properties();
		if (args != null) {
			for (String arg : args) {
				String[] parts = arg.split("=", 2);
				if (parts.length == 2 && !parts[0].isEmpty()) {
					cmdArgs.put(parts[0], parts[1]);
				}
			}
		}
		return cmdArgs;
	}

	private String getDisplayName(Properties props) {
		String displayName = props.getProperty(Constants.S_SERVER_DISPLAY_NAME);
		if (displayName == null || displayName.isEmpty()) {
			displayName = props.getProperty(Constants.S_SERVER_NAME);
		}
		if (displayName == null || displayName.isEmpty()) {
			displayName = "Reveila";
		}
		return displayName;
	}

	private Properties loadProperties(Properties cmdArgs) throws IOException, ConfigurationException {

		// retrieve OS environment variables
		Properties envs = new Properties();
		Map<String, String> m = System.getenv();
		// Use a modern for-each loop for better readability.
		for (Map.Entry<String, String> entry : m.entrySet()) {
			envs.put(entry.getKey().toLowerCase(), entry.getValue());
		}

		// load properties from system.properties file
		String urlStr = cmdArgs.getProperty(Constants.S_SYSTEM_PROPERTIES_URL);
		URL url;
		try {
			if (urlStr != null && !urlStr.isBlank()) {
				url = new URI(urlStr).toURL();
			} else {
				url = ClassLoader.getSystemResource(Constants.S_SYSTEM_PROPERTIES_FILE_NAME);
				if (url == null) {
					throw new ConfigurationException("Could not find the system.properties file on classpath");
				}
			}
		} catch (Exception e) {
			throw new ConfigurationException("Failed to resolve system properties URL", e);
		}

		System.out.println("\nSystem properties URL: " + url);
		Properties loadedProps = new Properties();
		try (InputStream stream = url.openStream()) {
			loadedProps.load(stream);
		}

		Properties combined = new Properties();
		combined.putAll(envs);
		combined.putAll(loadedProps);
		combined.putAll(cmdArgs);
		return combined;
	}

	private Logger configureLogging(Properties props) throws IOException {
		Logger newLogger = Logger.getLogger(Reveila.class.getName());

		// Remove default handlers from the root logger to prevent duplicate console output
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        for (Handler handler : handlers) {
            rootLogger.removeHandler(handler);
        }

		String logFilePath = props.getProperty(Constants.S_SYSTEM_LOGGING_FILE);
		Handler handler;
		String logTo;
		if (logFilePath == null || logFilePath.isBlank()) {
			logTo = "System Console";
			handler = new ConsoleHandler();
		} else {
			File logFile = new File(logFilePath.trim());
			File logDir = logFile.getParentFile();
			if (logDir != null && !logDir.exists()) {
				logDir.mkdirs();
			}
			logTo = logFile.getAbsolutePath();
			handler = new FileHandler(logFilePath, true); // true = append mode
			handler.setFormatter(new SimpleFormatter());
		}
        
        handler.setLevel(Level.ALL);
        newLogger.addHandler(handler);
        newLogger.setUseParentHandlers(false);

        boolean debug = "true".equalsIgnoreCase(props.getProperty(Constants.S_SYSTEM_DEBUG));
		String levelStr = props.getProperty(Constants.S_SYSTEM_LOGGING_LEVEL, "ALL").trim().toUpperCase();

		if (debug) {
			newLogger.setLevel(Level.ALL); // Debug mode overrides level setting
		} else {
            try {
                newLogger.setLevel(Level.parse(levelStr));
            } catch (IllegalArgumentException e) {
                newLogger.setLevel(Level.ALL); // Default to ALL if the level string is invalid
            }
		}

		newLogger.info("Logging set to: " + logTo + ", level=" + newLogger.getLevel() + ", debug=" + debug);
		return newLogger;
	}

	private void initializeClassLoader(Properties props) throws ConfigurationException {
		String systemHome = props.getProperty(Constants.S_SYSTEM_HOME);
		if (systemHome == null || systemHome.isBlank()) {
			throw new ConfigurationException("System property '" + Constants.S_SYSTEM_HOME + "' is not set.");
		}

		File homeDir = new File(systemHome);
		if (!homeDir.exists() || !homeDir.isDirectory() || !homeDir.canWrite()) {
			throw new ConfigurationException("Problem with the " + Constants.S_SYSTEM_HOME + " directory: " + homeDir.getAbsolutePath());
		}

		File libDir = new File(homeDir, Constants.C_LIB_DIR_NAME);
		if (!libDir.exists() || !libDir.isDirectory() || !libDir.canRead()) {
			throw new ConfigurationException("Problem with the " + Constants.C_LIB_DIR_NAME + " directory: " + libDir.getAbsolutePath());
		}

		File[] jarFiles = libDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
		if (jarFiles == null) {
			throw new ConfigurationException("Could not list files in lib directory: " + libDir.getAbsolutePath());
		}

		URL[] urlArray = Arrays.stream(jarFiles)
				.map(jarFile -> {
					try {
						return jarFile.toURI().toURL();
					} catch (Exception e) {
						logger.warning("Failed to include jar file in classloader: " + jarFile.getAbsolutePath());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.toArray(URL[]::new);
		URLClassLoader classLoader = new URLClassLoader(urlArray, Reveila.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);
	}

	private void initializeSystemContext(Properties props) throws Exception {

		String home = props.getProperty(Constants.S_SYSTEM_HOME, 
							System.getProperty("user.dir") + File.separator + "Reveila");
		
		String fileStore = props.getProperty(Constants.S_SYSTEM_FILE_STORE);
		if (fileStore == null || fileStore.isBlank()) {
			fileStore = home + File.separator + "data";
			props.setProperty(Constants.S_SYSTEM_FILE_STORE, fileStore);
		}
		FileUtil.createDirectory(fileStore);

		String tempFileStore = props.getProperty(Constants.S_SYSTEM_TMP_FILE_STORE);
		if (tempFileStore == null || tempFileStore.isBlank()) {
			tempFileStore = home + File.separator + "temp";
			props.setProperty(Constants.S_SYSTEM_TMP_FILE_STORE, tempFileStore);
		}
		FileUtil.createDirectory(tempFileStore);

		FileManager rootFileManager = new FileManager(fileStore, tempFileStore);
		this.fileManagers.put(this, rootFileManager);

		this.eventManager = new EventManager();
		this.eventManager.setLogger(this.logger);

		String secretKey = props.getProperty(Constants.S_SYSTEM_CRYPTOGRAPHER_SECRETKEY);
		if (secretKey == null || secretKey.isBlank()) {
			throw new ConfigurationException("System property '" + Constants.S_SYSTEM_CRYPTOGRAPHER_SECRETKEY + "' is not set.");
		}

		this.systemContext = new SystemContext(
				props, this.fileManagers, this.eventManager, this.logger,
				new DefaultCryptographer(secretKey.getBytes())
		);
	}

	private void loadComponents(Properties props) throws Exception {
		String homeDir = props.getProperty(Constants.S_SYSTEM_HOME);
		File componentsDir = new File(homeDir, "configs" + File.separator + "components");
		String dirString = componentsDir.getAbsolutePath();

		File[] files = FileUtil.listFilesWithExtension(dirString, "json");
		if (files == null) {
			throw new ConfigurationException("Failed to access components directory: " + dirString);
		} else if (files.length == 0) {
			logger.info("No components found to load in " + dirString);
			return; // nothing to load
		}

		for (File f : files) {
			try {
				logger.info("Loading components from: " + f.getName());
				JsonConfiguration group = new JsonConfiguration(f.getAbsolutePath());
				List<MetaObject> list = group.read();
				for (MetaObject item : list) {
					new Proxy(item).setSystemContext(this.systemContext);
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Failed to load components from " + f.getAbsolutePath(), e);
			}
		}
	}

	private void logOrPrint(String message, Level level) {
		if (this.logger != null) {
			this.logger.log(level, message);
		} else {
			System.out.println(message);
		}
	}

	private void logOrPrint(String message, Level level, Throwable throwable) {
		if (this.logger != null) {
			this.logger.log(level, message, throwable);
		} else {
			System.err.println(message);
			throwable.printStackTrace(System.err);
		}
	}
}
