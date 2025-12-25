/**
 * @author Charles Lee
 */
package com.reveila;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reveila.crypto.DefaultCryptographer;
import com.reveila.error.ConfigurationException;
import com.reveila.system.Constants;
import com.reveila.system.EventManager;
import com.reveila.system.JsonConfiguration;
import com.reveila.system.MetaObject;
import com.reveila.system.NodePerformanceTracker;
import com.reveila.system.PlatformAdapter;
import com.reveila.system.Proxy;
import com.reveila.system.SystemContext;
import com.reveila.util.TimeFormat;

import java.util.concurrent.CompletableFuture;

public class Reveila {

	private PlatformAdapter platformAdapter;
	private Properties properties;
	private SystemContext systemContext;
	private Logger logger;
	private String localAddress;
	private URL localUrl;
	private boolean standaloneMode = true;
	private boolean isShutdown = false;

	public synchronized void shutdown() {
		
		if (isShutdown) {
			return;
		}

		isShutdown = true;

		System.out.println("\n\nShutting down Reveila...");
		if (logger != null) {
			logger.info("Shutting down Reveila...");
		}

		boolean error = false;

		if (this.systemContext != null) {
			try {
				systemContext.destruct();
			} catch (Exception e) {
				error = true;
				System.out.println("Reveila shutdown failed: " + e.getMessage());
				e.printStackTrace();
				if (logger != null) {
					logger.log(Level.SEVERE, "Reveila shutdown failed.", e);
				}
			}
		}

		if (this.platformAdapter != null) {
			try {
				this.platformAdapter.destruct();
			} catch (Exception e) {
				error = true;
				System.out.println("Failed to destruct Reveila platform adapter: " + e.getMessage());
				e.printStackTrace();
				if (logger != null) {
					logger.log(Level.SEVERE, "Failed to destruct Reveila platform adapter.", e);
				}
			}
		}

		if (!error) {
			System.out.println("Reveila shut down successfully\n\n");
			if (logger != null) {
				logger.info("Reveila shut down successfully.");
			}
		} else {
			System.out.println("Reveila shut down with errors. Check logs for details.\n\n");
			if (logger != null) {
				logger.warning("Reveila shut down with errors. Check logs for details.");
			}
		}

		// Close logger handlers at the very end of the shutdown sequence.
		if (this.logger != null) {
			for (Handler handler : this.logger.getHandlers()) {
				handler.close();
			}
		}
	}

	public synchronized void start(PlatformAdapter platformAdapter) throws Exception {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdown();
		}));

		if (platformAdapter == null) {
			throw new IllegalArgumentException("PlatformAdapter cannot be null");
		}

		this.platformAdapter = platformAdapter;
		this.properties = platformAdapter.getProperties();
		this.logger = platformAdapter.getLogger();
		this.localAddress = getLocalHost();
		this.localUrl = new URI("http://" + this.localAddress + "/").toURL();

		printLogo();

		logStartupBanner();
		long beginTime = System.currentTimeMillis();

		try {
			this.standaloneMode = "true"
					.equalsIgnoreCase(this.properties.getProperty(Constants.STANDALONE_MODE));
		} catch (Exception e) {
		}

		createSystemContext(this.properties);
		loadComponents();

		logStartupCompletion(beginTime);

		System.out.println(getDisplayName(this.properties) + " is running...");
	}

	private void printLogo() {

		System.out.println();

		BufferedReader reader = null;
		try (InputStream is = this.platformAdapter.getInputStream(PlatformAdapter.CONF_STORAGE, "logo.txt")) {
			if (is == null) {
				System.out.println("Reveila"); // Fallback if logo resource is not found
			} else {
				reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
				reader.lines().forEach(System.out::println);
			}
		} catch (Exception e) {
			// Log a warning or handle the exception if the logo fails to load
			System.out.println("Warning: Could not load Reveila logo resource file.");
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					// Ignore
				}
			}
		}

		String version = this.properties.getProperty(Constants.SYSTEM_VERSION);
		if (version != null && !version.isBlank()) {
			System.out.println(version);
		}

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
		String displayName = props.getProperty(Constants.SYSTEM_NAME);
		if (displayName != null && !displayName.isBlank()) {
			return displayName;
		}
		return "Reveila"; // Default fallback
	}

	private void createSystemContext(Properties props) throws Exception {
		EventManager eventManager = new EventManager();
		eventManager.setLogger(this.logger);

		String secretKey = props.getProperty(Constants.CRYPTOGRAPHER_SECRETKEY);
		if (secretKey == null || secretKey.isBlank()) {
			throw new ConfigurationException(
					"Reveila system property '" + Constants.CRYPTOGRAPHER_SECRETKEY + "' is not set.");
		}

		String charset = props.getProperty(Constants.CHARACTER_ENCODING);
		if (charset == null || charset.isBlank()) {
			charset = StandardCharsets.UTF_8.name();
		}

		this.systemContext = new SystemContext(
				props, eventManager, this.logger,
				new DefaultCryptographer(secretKey.getBytes(charset)), this.platformAdapter);
	}

	private void loadComponents() throws Exception {
		String[] componentFiles = this.platformAdapter.listComponentConfigs();
		if (componentFiles == null || componentFiles.length == 0) {
			logger.info("No components found to load.");
			return;
		}

		for (String fileName : componentFiles) {
			if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
				try (InputStream is = this.platformAdapter.getInputStream(PlatformAdapter.CONF_STORAGE, fileName)) {
					logger.info("Loading components from: " + fileName);
					JsonConfiguration jsonConf = new JsonConfiguration(is);
					List<MetaObject> list = jsonConf.getMetaObjects();
					for (MetaObject mObj : list) {
						if (mObj.isStartOnLoad()) {
							logger.info("Starting component on load: " + mObj.getName());
							// The Proxy class handles both stateful (singleton) and stateless (prototype)
							// lifecycles.
							Proxy proxy = new Proxy(mObj);
							proxy.setSystemContext(this.systemContext);
							proxy.start();
						}
					}
				} catch (Exception e) {
					boolean isStrictMode = "true"
							.equalsIgnoreCase(this.properties.getProperty(Constants.LAUNCH_STRICT_MODE));
					if (isStrictMode) {
						throw new ConfigurationException("Failed to load components from " + fileName, e);
					} else {
						logger.log(Level.SEVERE,
								"Failed to load components from " + fileName + ". Continuing in non-strict mode.", e);
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
	 * @param methodName    The name of the method to invoke.
	 * @param params        The parameters to pass to the method.
	 * @return The result of the method invocation.
	 * @throws Exception if the component or method is not found, or if invocation
	 *                   fails.
	 */
	public Object invoke(String componentName, String methodName, Object[] params) throws Exception {
		return invoke(componentName, methodName, params, null);
	}

	/**
	 * Asynchronous version of the invoke method, returning a CompletableFuture.
	 * This allows non-blocking calls from the caller.
	 * 
	 * Blocking retrieval:
	 * 
	 * try {
	 * Object result = future.get(); // This blocks the thread until the future is
	 * complete.
	 * // Or with a timeout: Object result = future.get(5, TimeUnit.SECONDS);
	 * } catch (Exception e) {
	 * // Handle exceptions
	 * }
	 * 
	 * Non-blocking retrieval:
	 * 
	 * future.thenAccept(result -> {
	 * System.out.println("Received result: " + result);
	 * });
	 * 
	 * @param componentName The name of the component to invoke.
	 * @param methodName    The name of the method to invoke.
	 * @param params        The parameters to pass to the method.
	 * @return The CompletableFuture result of the method invocation.
	 */
	public CompletableFuture<Object> invokeAsync(String componentName, String methodName, Object[] params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(componentName, methodName, params, null);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	public Object invoke(String componentName, String methodName, Object[] params, String callerIp) throws Exception {

		// This method actually performs the invocation logic

		long startTime = System.currentTimeMillis();
		if (systemContext == null) {
			throw new IllegalStateException("SystemContext is not initialized. Cannot invoke component.");
		}

		if (callerIp != null && !callerIp.isBlank()) {
			logger.info("Received invocation request from " + callerIp + " for target: " + componentName + ", method: "
					+ methodName);
		}

		Proxy proxy = null;

		if (standaloneMode == false) {
			// Use the fastest node in the cluster to handle the request
			URL url = NodePerformanceTracker.getInstance().getBestNodeUrl();

			if (url != this.localUrl) {
				Optional<Proxy> remoteProxy = systemContext.getProxy(Constants.REMOTE_REVEILA);
				if (remoteProxy.isPresent()) {
					proxy = remoteProxy.get();
					try {
						startTime = System.currentTimeMillis();
						Object result = proxy.invoke("invoke", new Object[] { url, componentName, methodName, params });
						Long timeUsed = System.currentTimeMillis() - startTime;
						NodePerformanceTracker.getInstance().track(timeUsed, url); // Track remote invocation time
						return result;
					} catch (Exception e) {
						NodePerformanceTracker.getInstance()
								.track(Long.valueOf(NodePerformanceTracker.DEFAULT_PENALTY_MS), url); // Penalize the
																										// remote node
																										// for failure
						logger.severe(
								"Remote invocation failed. Falling back to local invocation. Error: " + e.getMessage());
						e.printStackTrace();
					}
				}
			}
		}

		// Use local invocation if standalone mode, or no better remote option, or
		// remote invocation failed
		Optional<Proxy> localProxy = systemContext.getProxy(componentName);
		if (localProxy.isPresent()) {
			proxy = localProxy.get();
		} else {
			throw new ConfigurationException("Component '" + componentName + "' not found.");
		}

		startTime = System.currentTimeMillis();
		Object result = proxy.invoke(methodName, params);
		Long timeUsed = System.currentTimeMillis() - startTime;
		NodePerformanceTracker.getInstance().track(timeUsed, this.localUrl); // Track local invocation time
		return result;
	}

	private String getLocalHost() {
		String address = null;
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			address = localHost.getHostAddress();
		} catch (UnknownHostException e) {
			logger.info("Unable to retrieve local IP address. Invocation time tracking is disabled. Error: "
					+ e.getMessage());
		}

		return address;
	}
}
