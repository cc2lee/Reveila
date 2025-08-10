/**
 * @author Charles Lee
 */
package reveila;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import reveila.crypto.DefaultCryptographer;
import reveila.error.ConfigurationException;
import reveila.platform.PlatformAdapter;
import reveila.system.Constants;
import reveila.system.JsonConfiguration;
import reveila.system.MetaObject;
import reveila.system.Proxy;
import reveila.system.SystemContext;
import reveila.util.TimeFormat;
import reveila.util.event.EventManager;

public final class Reveila {

	private PlatformAdapter platformAdapter;
	private Properties properties;
	private SystemContext systemContext;
	private Logger logger;

	public SystemContext getSystemContext() {
		return systemContext;
	}

	/**
	 * Made public to be accessible from the Android Service.
	 */
	public void shutdown() {
		synchronized (this) {
			System.out.println("\n\nShutting down system...");
			boolean error = false;

			if (this.systemContext != null) {
				try {
					systemContext.destruct();
				} catch (Exception e) {					
					error = true;
					System.err.println("System shutdown failed: " + e.getMessage());
					e.printStackTrace(System.err);
				}
			}

			if (!error) {
				System.out.println("System shut down successfully\n\n");
			}

			// Close logger handlers at the very end of the shutdown sequence.
			if (this.logger != null) {
				for (Handler handler : this.logger.getHandlers()) {
					handler.close();
				}
			}
		}
	}

	public void start(PlatformAdapter platformAdapter) throws Exception {
		if (platformAdapter == null) {
			throw new IllegalArgumentException("PlatformAdapter cannot be null");
		}

		this.platformAdapter = platformAdapter;
		this.properties = platformAdapter.getProperties();
		this.logger = platformAdapter.getLogger();
		
		printLogo();

		logStartupBanner();
		long beginTime = System.currentTimeMillis();

		createSystemContext(this.properties);
		loadComponents();

		logStartupCompletion(beginTime);

		System.out.println(getDisplayName(this.properties) + " is running...");
	}

	private void printLogo() {
        System.out.println();
        try (InputStream is = this.platformAdapter.getInputStream(PlatformAdapter.CONF_STORAGE, "logo.txt")) {
            if (is == null) {
                System.out.println("Reveila"); // Fallback if logo resource is not found
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    reader.lines().forEach(System.out::println);
                }
            }
        } catch (Exception e) {
            // Log a warning or handle the exception if the logo fails to load
            System.err.println("Warning: Could not load logo resource file.");
        }

        String version = this.properties.getProperty(Constants.S_SYSTEM_VERSION, "Unknown Version");
        System.out.println("Version " + version);
        System.out.println();
    }

	private void logStartupBanner() {
		String displayName = getDisplayName(this.properties);
		logger.info("Starting " + displayName + "...");
	}

	private void logStartupCompletion(long beginTime) {
		String displayName = getDisplayName(this.properties);
		long msecs = System.currentTimeMillis() - beginTime;
		String timeUsed = TimeFormat.getInstance().format(msecs);
		logger.info(displayName + " started successfully. Time taken = " + timeUsed);
	}

	private String getDisplayName(Properties props) {
		String displayName = props.getProperty(Constants.S_SERVER_DISPLAY_NAME);
		if (displayName != null && !displayName.isBlank()) {
			return displayName;
		}
		displayName = props.getProperty(Constants.S_SERVER_NAME);
		if (displayName != null && !displayName.isBlank()) {
			return displayName;
		}
		return "Reveila"; // Default fallback
	}

	private void createSystemContext(Properties props) throws Exception {
		EventManager eventManager = new EventManager();
		eventManager.setLogger(this.logger);

		String secretKey = props.getProperty(Constants.S_SYSTEM_CRYPTOGRAPHER_SECRETKEY);
		if (secretKey == null || secretKey.isBlank()) {
			throw new ConfigurationException("System property '" + Constants.S_SYSTEM_CRYPTOGRAPHER_SECRETKEY + "' is not set.");
		}

		String charset = props.getProperty(Constants.S_SYSTEM_CHARSET);
		if (charset == null || charset.isBlank()) {
			charset = StandardCharsets.UTF_8.name();
		}

		this.systemContext = new SystemContext(
				props, eventManager, this.logger,
				new DefaultCryptographer(secretKey.getBytes(charset)), this.platformAdapter
		);
	}

	private void loadComponents() throws Exception {
		String[] componentFiles = this.platformAdapter.listComponentConfigs();
		if (componentFiles == null || componentFiles.length == 0) {
			logger.info("No components found to load.");
			return;
		}

		for (String fileName : componentFiles) {
			if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
				InputStream is = null;
				try {
					is = this.platformAdapter.getInputStream(PlatformAdapter.CONF_STORAGE, fileName);
					logger.info("Loading components from: " + fileName);
					JsonConfiguration group = new JsonConfiguration(is, this.logger);
					List<MetaObject> list = group.read();
					for (MetaObject item : list) {
						// The Proxy class handles both stateful (singleton) and stateless (prototype) lifecycles.
						Proxy proxy = new Proxy(item);
						proxy.setSystemContext(this.systemContext);
						if (item.isStartOnLoad()) {
							logger.info("Starting component on load: " + item.getName());
							proxy.start();
						}
					}
				} catch (Exception e) {
					boolean isStrictMode = "true".equalsIgnoreCase(this.properties.getProperty(Constants.S_SYSTEM_STRICT_MODE));
					if (isStrictMode) {
						throw new ConfigurationException("Failed to load components from " + fileName, e);
					} else {
						logger.log(Level.SEVERE, "Failed to load components from " + fileName + ". Continuing in non-strict mode.", e);
					}
				} finally {
					if (is != null) {
						is.close();
					}
				}
			}
		}
	}

	/**
	 * Invokes a method on a loaded component. This is the main entry point for the
	 * React Native bridge to interact with the Reveila backend.
	 *
	 * @param componentName The name of the component to invoke.
	 * @param methodName The name of the method to invoke.
	* @param params The parameters to pass to the method.
	 * @return The result of the method invocation.
	 * @throws Exception if the component or method is not found, or if invocation fails.
	 */
	public Object invoke(String componentName, String methodName, Object[] params) throws Exception {
		if (systemContext == null) {
			throw new IllegalStateException("SystemContext is not initialized. Cannot invoke component.");
		}

		// Find the proxy for the requested component.
		Proxy proxy = systemContext.getProxy(componentName)
				.orElseThrow(() -> new ConfigurationException("Component '" + componentName + "' not found."));

		// Use the new flexible invoke method on the Proxy.
		// This method finds the target method by name and argument count.
		return proxy.invoke(methodName, params);
	}

}
