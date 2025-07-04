/**
 * @author Charles Lee
 */
package reveila;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import reveila.crypto.DefaultCryptographer;
import reveila.system.Constants;
import reveila.system.MetaObject;
import reveila.system.JsonConfiguration;
import reveila.system.Logo;
import reveila.system.Proxy;
import reveila.system.SystemContext;
import reveila.util.RuntimeUtil;
import reveila.util.TimeFormat;
import reveila.util.event.EventManager;
import reveila.util.io.FileManager;
import reveila.util.io.FileUtil;


public final class Reveila {

	private static Properties properties;
	private static SystemContext systemContext;
	
	public SystemContext getSystemContext() {
		return systemContext;
	}

	private static Map<Object,FileManager> fileManagers 
			= Collections.synchronizedMap(new HashMap<Object,FileManager>());
	private static EventManager eventManager;
	private static Logger logger;
	private static String systemHome;
	
	private void shutdown() {
		
		synchronized (this) {

			System.out.println();
			System.out.println();
			System.out.println("Shutting down system...");

			boolean error = false;

			if (systemContext != null) {
				try {
					systemContext.destruct();
				} catch (Exception e) {
					error = true;
					String msg = "System shutdown failed: " + e.getMessage();
					if (logger != null) {
						logger.severe(msg);
					} else {
						System.out.println(msg);
					}
					e.printStackTrace();
				}
			}

			if (!error) {
				String msg = "System shut down successfully";
				if (logger != null) {
					logger.info(msg);
				} else {
					System.out.println(msg);
					System.out.println();
					System.out.println();
				}
			}
		}
	}
	
	/**
	 * By default, this method looks for the "system.properties" file
	 * on the classpath to load the system properties.
	 * Alternatively, a command-line argument can be specified, in the form of
	 * "system.init.url={url}", to load system properties
	 * from the URL specified. Any system property can also be specified as
	 * commmand-line argument in the same format using the keys defined in the
	 * system.properties file, in this case, the value from the command-line will will
	 * overwrite the value defined in the system.properties file.
	 */
	public void start(String[] args) throws Exception {

		Logo.print();

		// Install JVM shutdown hook
		// so that we can have a smooth exit if anything goes wrong.
		RuntimeUtil.addShutdownHook(new Runnable() {
			public void run() {
				shutdown();
			}
		});
		
		/* 
		Process command-line parameters.
		If a property is specified as a command-line parameter, 
		it overrides the value that is also defined in the system.properties file.
		*/
		Properties cmdArgs = new Properties();
		if (args != null && args.length > 0) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				System.out.println("Command-line argument(s): " + arg);
				try {
					int e = arg.indexOf("=");
					cmdArgs.put(arg.substring(0, e), arg.substring(e + 1));
				} catch (Exception e) {
					throw new RuntimeException(
							"Malformed command-line argument: " + arg, e);
				}

			}
		}
		
		// Process command-line parameters first!
		// If the command-line parameter "system.init.url" is present,
		// use its value, which is a URL, to load the system properties.
		// Otherwise,
		// search for the system.properties file on the classpath.
		
		String urlStr = cmdArgs.getProperty(Constants.S_SYSTEM_PROPERTIES_URL);
		URL url;
		if (urlStr != null && urlStr.length() > 0) {
			url = new URI(urlStr).toURL();
		} else {
			url = ClassLoader.getSystemResource(Constants.S_SYSTEM_PROPERTIES_FILE_NAME);
			if (url == null) {
				throw new RuntimeException(
						"Could not find the system.properties file on classpath");
			}
		}

		// Load system properties from the URL.
		// The URL can be a file URL or a http URL.
		System.out.println();
		System.out.println("System properties URL: " + url);
		
		properties = new Properties();
		properties.load(url.openStream());
		
		// Overwrite properties defined in the system.properties file
		// with the ones specified on the command-line.
		if (cmdArgs != null) {
			properties.putAll(cmdArgs);
		} // done with loading system properties

		setupLogger();
		
		systemHome = properties.getProperty(Constants.S_SYSTEM_HOME);
		File homeDir = new File(systemHome);
		if (homeDir == null || !homeDir.exists() || !homeDir.isDirectory() || !homeDir.canWrite()) {
			throw new RuntimeException(
				"Problem with the " + Constants.S_SYSTEM_HOME + " directory: " + homeDir.getAbsolutePath());
		}
		
		File libDir = new File(systemHome, Constants.C_LIB_DIR_NAME);
		if (libDir == null || !libDir.exists() || !libDir.isDirectory() || !libDir.canRead()) {
			throw new RuntimeException(
				"Problem with the " + Constants.C_LIB_DIR_NAME + " directory: " + libDir.getAbsolutePath());
		}
		
		// Build the system classpath as URL[].
		// The system classpath is used to load the system classes.
		FilenameFilter filter = new FilenameFilter() {

			public boolean accept(File dir, String name) {
				name = name.toLowerCase();
				return name.endsWith(".jar");
			}
		};

		File[] jarFileArray = libDir.listFiles(filter);
		if (jarFileArray == null) { // should not happen
			// The lib directory may be inaccessible.
			throw new RuntimeException(
					"Unexpected problem with the " + Constants.C_LIB_DIR_NAME + " directory: " + libDir.getAbsolutePath());
		}

		List<URL> urlList = new LinkedList<URL>();
		for (int i = 0; i < jarFileArray.length; i++) {
			File jarFile = jarFileArray[i];
			try {
				urlList.add(jarFile.toURI().toURL());
			} catch (Exception e) {
				// Ignore the exception and continue
				// to the next jar file.
				logger.severe("Failed to include jar file: " + jarFile);
			}
		}

		URL[] urlArray = new URL[urlList.size()];
		urlList.toArray(urlArray);
		URLClassLoader classLoader = new URLClassLoader(urlArray, Reveila.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);
		
		// Resolve server display name
		String displayName = properties.getProperty(Constants.S_SERVER_DISPLAY_NAME);
		if (displayName == null || displayName.length() == 0) {
			displayName = properties.getProperty(Constants.S_SERVER_NAME);
		}

		// If no server name was given, use a generic name
		if (displayName == null || displayName.length() == 0) {
			displayName = "Reveila";
		}

		logger.info("Starting " + displayName);
		
		long beginTime = System.currentTimeMillis();

		fileManagers.put(this, createRootFileManager());
		eventManager = new EventManager();
		eventManager.setLogger(logger);
		//Map<String, XmlConf> xmlConfs = new HashMap<String, XmlConf>();
		systemContext = new SystemContext(
			properties, fileManagers, eventManager, logger, 
			new DefaultCryptographer(properties.getProperty(Constants.S_SYSTEM_CRYPTOGRAPHER_SECRETKEY).getBytes()));
		
		createProxyObjects();

		long msecs = System.currentTimeMillis() - beginTime;

		String timeUsed = TimeFormat.getInstance().format(msecs);
		System.out.println(displayName + " started successfully. Time taken = " + timeUsed);
		System.out.println();
		System.out.println();
	}

	private FileManager createRootFileManager() throws Exception {
		
		return new FileManager(
			FileUtil.createDirectory(properties.getProperty(Constants.S_SYSTEM_FILE_STORE)), 
			FileUtil.createDirectory(properties.getProperty(Constants.S_SYSTEM_TMP_FILE_STORE)));
	}

	private void setupLogger() throws Exception {

		// Remove default handlers
        logger = Logger.getLogger("");
        Handler[] handlers = logger.getHandlers();
        for (Handler handler : handlers) {
            logger.removeHandler(handler);
        }

		String logFilePath = properties.getProperty(Constants.S_SYSTEM_LOGGING_FILE);
		Handler handler;
		String logTo;
		if (logFilePath == null || logFilePath.trim().length() == 0) {
			logTo = "System Console";
			handler = new ConsoleHandler() {
				@Override
				protected synchronized void setOutputStream(java.io.OutputStream out) throws SecurityException {
					super.setOutputStream(out);
				}
			};
		} else {
			// Ensure the directory exists
			File logFile = new File(logFilePath.trim());
			File logDir = logFile.getParentFile();
			if (logDir != null && !logDir.exists()) {
				logDir.mkdirs();
			}

			logTo = logFile.getAbsolutePath();

			// Create and configure the FileHandler
			handler = new FileHandler(logFilePath, true); // true = append mode
			handler.setFormatter(new SimpleFormatter());
		}
        
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);

        boolean debug = "true".equalsIgnoreCase(properties.getProperty(Constants.S_SYSTEM_DEBUG));
		
		String level = properties.getProperty(Constants.S_SYSTEM_LOGGING_LEVEL);
		if (level == null) level = "";
		else level = level.trim().toUpperCase();

		if (debug) {
			logger.setLevel(Level.ALL);
		} else {
			switch (level) {
				case "OFF":
					logger.setLevel(Level.OFF);
					break;
			
				case "SEVERE":
					logger.setLevel(Level.SEVERE);
					break;
			
				case "WARNING":
					logger.setLevel(Level.WARNING);
					break;
			
				case "INFO":
					logger.setLevel(Level.INFO);
					break;
			
				case "CONFIG":
					logger.setLevel(Level.CONFIG);
					break;
			
				case "FINE":
					logger.setLevel(Level.FINE);
					break;
			
				case "FINER":
					logger.setLevel(Level.FINER);
					break;
			
				case "FINEST":
					logger.setLevel(Level.FINEST);
					break;
			
				case "ALL":
					logger.setLevel(Level.ALL);
					break;
			
				default:
					logger.setLevel(Level.ALL);
					break;
			}
		}

		logger.info("Logging set to: " + logTo + ", level=" + level + ", debug=" + debug);
	}
	
	private void createProxyObjects() throws Exception {
		
		String homeDir = properties.getProperty(Constants.S_SYSTEM_HOME);
		File dir = new File(homeDir + File.separator + "configs", "components");
		String dirString = dir.getAbsolutePath();
		File[] files = FileUtil.listFilesWithExtension(dirString, "json");
		if (files == null) {
			throw new RuntimeException("Failed to access directory: " + dirString);
		} else if (files.length == 0) {
			return; // nothing to load
		}

		for (File f : files) {
			JsonConfiguration group = new JsonConfiguration(f.getAbsolutePath());
			List<MetaObject> list = group.read();
			for (MetaObject item : list) {
				new Proxy(item).setSystemContext(systemContext);
			}
		}
	}
}
