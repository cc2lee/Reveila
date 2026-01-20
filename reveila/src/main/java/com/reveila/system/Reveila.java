/**
 * @author Charles Lee
 */
package com.reveila.system;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reveila.crypto.DefaultCryptographer;
import com.reveila.error.ConfigurationException;
import com.reveila.error.SystemException;
import com.reveila.event.EventManager;
import com.reveila.util.TimeFormat;

public class Reveila {

	private PlatformAdapter platformAdapter;
	private Properties properties;
	private SystemContext systemContext;
	private boolean strictMode = true;
	private List<Proxy> startedProxies;

	public SystemContext getSystemContext() {
		return systemContext;
	}

	private Logger logger;
	private String localAddress;
	private URL localUrl;
	private boolean standaloneMode = true;
	private boolean shutdown = false;

	public synchronized void shutdown() {

		if (shutdown) {
			return;
		}

		shutdown = true;
		boolean error = false;
		
		String message = "Shutting down Reveila...";
		System.out.println(message);
		if (logger != null) {
			logger.info(message);
		}
		
		error = !stopComponents();
		
		if (systemContext != null) {
			systemContext.clear();
		}

		if (platformAdapter != null) {
			try {
				platformAdapter.unplug();
			} catch (Throwable t) {
				error = true;
				message = "Failed to unplug platform adapter, cause: " + t.getMessage();
				if (logger != null) {
					logger.log(Level.WARNING, message, t);
				}
				System.out.println();
				System.out.println(message);
				t.printStackTrace();
			}
		}

		if (!error) {
			message = "Reveila shut down successfully.";
			System.out.println(message);
			if (logger != null) {
				logger.info(message);
			}
		} else {
			message = "Reveila shut down with errors. Check logs for details.";
			System.out.println(message);
			if (logger != null) {
				logger.warning(message);
			}
		}

		if (logger != null) {
			for (Handler handler : logger.getHandlers()) {
				try {
					handler.close();
				} catch (Throwable t) {
					System.err.println("Failed to close logger handler: " + handler);
					t.printStackTrace();
				}
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
			this.standaloneMode = !"false"
					.equalsIgnoreCase(this.properties.getProperty(Constants.STANDALONE_MODE));
		} catch (Exception e) {
		}

		createSystemContext(this.properties);
		startComponents(parseMetaObjects());
		logStartupCompletion(beginTime);

		System.out.println(getDisplayName(this.properties) + " is running...");
		this.platformAdapter.plug(this);
	}

	private void printLogo() {

		System.out.println();

		BufferedReader reader = null;
		try (InputStream is = this.platformAdapter.getFileInputStream("logo.txt")) {
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

	private List<MetaObject> parseMetaObjects() throws Exception {
		strictMode = !"false".equalsIgnoreCase(this.properties.getProperty(Constants.LAUNCH_STRICT_MODE));
		String[] files = this.platformAdapter.getConfigFilePaths();

		if (files == null || files.length == 0) {
			logger.info("No components found to load.");
			return null;
		}

		logger.info("Loading components...");
		
		// PHASE 1: DISCOVERY
		List<MetaObject> list = new ArrayList<>();
		for (String file : files) {
			try (InputStream is = this.platformAdapter.getFileInputStream(file)) {
				Configuration config = new Configuration(is);
				for (MetaObject mObj : config.getMetaObjects()) {
					if (mObj.getName() == null || mObj.getName().isBlank()) {
						throw new ConfigurationException(
								"Component name is not set in configuration file: " + file);
					}
					list.add(mObj);
				}
				logger.info("Processed configuration file: " + file);
			} catch (Exception e) {
				handleStartError("Failed to parse configuration file: " + file, e);
			}
		}

		// PHASE 2: VALIDATION (Linting & Cycle Detection)
		try {
			new ConfigurationLinter().lint(list);
		} catch (Exception e) {
			handleStartError("Configuration validation failed.", e);
		}

		return list;
	}

	/**
	 * Helper to centralize Strict Mode logic
	 */
	private void handleStartError(String message, Exception e) throws Exception {
		if (message == null || message.isBlank()) {
			message = "System startup error";
		}
		if (strictMode) {
			throw new ConfigurationException(message, e); // Rethrow in strict mode
		} else {
			logger.log(Level.SEVERE, message + (message.endsWith(".") ? " " : ". ") + "Continuing in non-strict mode.", e);
		}
	}

	private void setupAutoCall(Proxy proxy) throws Exception {
		if (proxy == null) {
			throw new IllegalArgumentException("Argument 'proxy' cannot be null.");
		}

		MetaObject mObj = proxy.getMetaObject();
		Map<String, Object> autoRunConf = mObj.getAutoRunConf();
		
		if (autoRunConf == null || autoRunConf.isEmpty()) {
			return;
		}

		final String methodName = String.valueOf(autoRunConf.get(Constants.RUNNABLE_METHOD));
		if (methodName == null || methodName.isBlank()) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_METHOD + "' is not set.");
		}

		String delay = String.valueOf(autoRunConf.get(Constants.RUNNABLE_DELAY));
		if (delay == null || delay.isBlank()) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_DELAY + "' is not set.");
		}

		String interval = String.valueOf(autoRunConf.get(Constants.RUNNABLE_INTERVAL));
		if (interval == null || interval.isBlank()) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_INTERVAL + "' is not set.");
		}

		if (Long.parseLong(interval) < 0) {
			throw new ConfigurationException(
					"Component auto-call configuration property '"
							+ Constants.RUNNABLE_INTERVAL + "' cannot be negative.");
		}

		platformAdapter.registerAutoCall(proxy.getName(), methodName, 
				Long.parseLong(delay), Long.parseLong(interval), proxy);
	}

	private void startComponents(List<MetaObject> list) throws Exception {
		if (startedProxies != null && !startedProxies.isEmpty()) {
			stopComponents();
		}

		if (list == null || list.isEmpty()) {
			return;
		}

		if (startedProxies == null) {
			startedProxies = new ArrayList<>();
		}

		// 1. Sort based on priority (Ascending)
		list.sort(Comparator.comparingInt(m -> m.getStartPriority()));

		// 2. Sequential Bootstrapping
		for (MetaObject mObj : list) {
			try {
				Proxy proxy = new Proxy(mObj);
				proxy.setName(mObj.getName());
				proxy.start();
				startedProxies.add(proxy); // Track successful starts
				systemContext.add(proxy);
				setupAutoCall(proxy);
			} catch (Exception e) {
				String errorMsg = "❌ Failed to start component " + mObj.getName() + ": " + e.getMessage();
				if (strictMode) {
					handleStartError(errorMsg + " - Initiating emergency shutdown.", e);
					// ROLLBACK: Stop everything that was already started in reverse order
					stopComponents();
					throw new SystemException(errorMsg, e);
				} else {
					handleStartError(errorMsg + " - Continuing in non-strict mode.", e);
				}
			}
		}
	}

	private boolean stopComponents() {
		if (startedProxies == null || startedProxies.isEmpty()) {
			return true;
		}
		
		// Stop in reverse order of starting (LIFO)
		boolean success = true;
		Collections.reverse(startedProxies);
		for (Proxy p : startedProxies) {
			try {
				p.stop();
			} catch (Exception e) {
				success = false;
				logger.log(Level.WARNING, "⚠️ Failed to stop " + p.toString(), e);
			}
		}

		startedProxies.clear();
		return success;
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
			URL url = PerformanceTracker.getInstance().getBestNodeUrl();

			if (url != this.localUrl) {
				Optional<Proxy> remoteProxy = systemContext.getProxy(Constants.REMOTE_REVEILA);
				if (remoteProxy.isPresent()) {
					proxy = remoteProxy.get();
					try {
						startTime = System.currentTimeMillis();
						Object result = proxy.invoke("invoke", new Object[] { url, componentName, methodName, params });
						Long timeUsed = System.currentTimeMillis() - startTime;
						PerformanceTracker.getInstance().track(timeUsed, url); // Track remote invocation time
						return result;
					} catch (Exception e) {
						PerformanceTracker.getInstance()
								.track(Long.valueOf(PerformanceTracker.DEFAULT_PENALTY_MS), url); // Penalize the
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
		PerformanceTracker.getInstance().track(timeUsed, this.localUrl); // Track local invocation time
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
