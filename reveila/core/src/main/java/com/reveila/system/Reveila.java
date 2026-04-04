/**
 * @author Charles Lee
 */
package com.reveila.system;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.security.auth.Subject;

import com.reveila.crypto.DefaultCryptographer;
import com.reveila.error.ConfigurationException;
import com.reveila.error.SystemException;
import com.reveila.event.EventConsumer;
import com.reveila.event.EventManager;
import com.reveila.util.TimeFormat;

public class Reveila implements AutoCloseable, EventConsumer {

	private ExecutorService startExecutor;
	private PlatformAdapter platformAdapter;
	private final Properties properties = new Properties();
	private SystemContext systemContext;
	private boolean strictMode = true;
	private List<SystemProxy> startedProxies;
	private String cryptoKey;

	public void setCryptoKey(String cryptoKey) {
		this.cryptoKey = cryptoKey;
	}

	public SystemContext getSystemContext() {
		return systemContext;
	}

	private Logger logger;
	private URL localUrl;
	private boolean standalone = true;
	private boolean shutdown = false;

	@Override
	public void close() {
		shutdown();
	}

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

		// Stop the executor from taking new tasks
		startExecutor.shutdownNow();
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

		try {
			if (!startExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
				if (logger != null) {
					logger.warning("Some start-up threads did not exit cleanly.");
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
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
		if (platformAdapter == null) {
			throw new IllegalArgumentException("PlatformAdapter cannot be null");
		}

		this.platformAdapter = platformAdapter;
		this.startExecutor = platformAdapter.getExecutor();
		this.properties.putAll(platformAdapter.getProperties());
		this.logger = platformAdapter.getLogger();
		this.localUrl = new URI("http://localhost/").toURL();

		printLogo();

		logStartupBanner();
		long beginTime = System.currentTimeMillis();

		try {
			this.standalone = !"false"
					.equalsIgnoreCase(this.properties.getProperty(Constants.STANDALONE_MODE));
		} catch (Exception e) {
		}

		createSystemContext(this.properties);
		this.platformAdapter.plug(this);

		strictMode = !"false".equalsIgnoreCase(this.properties.getProperty(Constants.LAUNCH_STRICT_MODE));
		long timeoutSeconds = 60;
		try {
			timeoutSeconds = Long.parseLong(this.properties.getProperty(Constants.COMPONENT_START_TIMEOUT, "60"));
		} catch (NumberFormatException e) {
			logger.warning("Invalid value for " + Constants.COMPONENT_START_TIMEOUT + ". Using default: 60");
		}

		String platform = properties.getProperty("platform");
		if (platform == null) {
			throw new ConfigurationException("Platform not specified in " + Constants.SYSTEM_PROPERTIES + ".");
		}

		platform = platform.trim().toLowerCase();
		if (platform.isEmpty()) {
			throw new ConfigurationException("Platform not specified in " + Constants.SYSTEM_PROPERTIES + ".");
		}

		Map<String, MetaObject> componentMap = new java.util.LinkedHashMap<>();

		// 1. Discover Shared Components (Tier 1)
		List<MetaObject> sharedMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + "shared");
		for (MetaObject mObj : sharedMetaObjects) {
			mObj.setPlugin(false);
			componentMap.put(mObj.getName(), mObj);
		}

		// 2. Discover Platform-Specific Components (Tier 2 - Overwrites Shared)
		List<MetaObject> platformMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + platform);
		for (MetaObject mObj : platformMetaObjects) {
			mObj.setPlugin(false);
			componentMap.put(mObj.getName(), mObj);
		}

		List<MetaObject> finalComponentList = new ArrayList<>(componentMap.values());

		// 3. Discover Plugins
		List<MetaObject> pluginMetaObjects = parseMetaObjects(Constants.CONFIGS_DIR_NAME + File.separator + "plugins");
		for (MetaObject mObj : pluginMetaObjects) {
			mObj.setPlugin(true);
		}

		List<MetaObject> allMetaObjects = new ArrayList<>(finalComponentList);
		allMetaObjects.addAll(pluginMetaObjects);

		// PHASE 2: VALIDATION (Linting & Cycle Detection for ALL components together)
		try {
			new ConfigurationLinter().lint(allMetaObjects, this.properties);
			logger.info("✅ Full Configuration validation complete. No issues found.");
		} catch (Exception e) {
			handleStartError("Configuration validation failed.", e);
		}

		// PHASE 3: EXECUTION (Starting components in dependency-aware order)
		// We no longer rely purely on manual start-priority. 
		// Instead, we use a topological sort to ensure dependencies start first.
		List<MetaObject> sortedComponents = sortMetaObjects(finalComponentList);
		startComponents(Constants.COMPONENT, sortedComponents, timeoutSeconds);

		List<MetaObject> sortedPlugins = sortMetaObjects(pluginMetaObjects);
		startComponents(Constants.PLUGIN, sortedPlugins, timeoutSeconds);

		logStartupCompletion(beginTime);

		System.out.println(getDisplayName(this.properties) + " is running...");
	}

	private void printLogo() {
		String logoContent = loadLogoContent();
		String version = this.properties.getProperty(Constants.SYSTEM_VERSION);

		System.out.println("\n" + logoContent);
		if (version != null && !version.isBlank()) {
			System.out.println("Version: " + version);
		}
		System.out.println();
	}

	private String loadLogoContent() {
		// 1. Try to load from External Configuration Directory
		try {
			String logoPath = Constants.CONFIGS_DIR_NAME + "/logo.txt";
			try (InputStream is = this.platformAdapter.getFileInputStream(logoPath)) {
				java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
				byte[] data = new byte[1024];
				int nRead;
				while ((nRead = is.read(data, 0, data.length)) != -1) {
					buffer.write(data, 0, nRead);
				}
				return buffer.toString(StandardCharsets.UTF_8.name());
			}
		} catch (Exception e) {
			// Fall through to classpath
		}

		// 2. Fallback: Try to load from Classpath
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("logo.txt")) {
			if (is != null) {
				java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
				byte[] data = new byte[1024];
				int nRead;
				while ((nRead = is.read(data, 0, data.length)) != -1) {
					buffer.write(data, 0, nRead);
				}
				return buffer.toString(StandardCharsets.UTF_8.name());
			}
		} catch (IOException e) {
			// Fall through to hardcoded default
		}

		// 3. Ultimate Fallback: Plain Text
		return "REVEILA";
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

		com.reveila.crypto.Cryptographer crypto = this.platformAdapter.getCryptographer();
		if (crypto == null) {
			if (this.cryptoKey != null && !this.cryptoKey.isBlank()) {
				// Use the provided master password and a salt from properties
				String saltHex = props.getProperty("auth.master.salt", "00000000000000000000000000000000");
				crypto = new com.reveila.crypto.DefaultCryptographer(this.cryptoKey, hexToBytes(saltHex));
			} else {
				// Legacy fallback to raw byte key
				crypto = new com.reveila.crypto.DefaultCryptographer(secretKey.getBytes(charset));
			}
		}

		this.systemContext = new SystemContext(
				props, eventManager, this.logger,
				crypto, this.platformAdapter);
	}

	private byte[] hexToBytes(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
					+ Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	private List<MetaObject> parseMetaObjects(String dir) throws Exception {
		String[] fileArray = this.platformAdapter.listRelativePaths(dir, ".json");
		List<String> fileList = new ArrayList<>();
		if (fileArray != null) {
			Collections.addAll(fileList, fileArray);
		}

		// PHASE 1: DISCOVERY
		List<MetaObject> list = new ArrayList<>();
		for (String file : fileList) {
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

		return list;
	}

	/**
	 * Helper to centralize Strict Mode logic
	 */
	private void handleStartError(String message, Throwable t) throws Exception {
		if (message == null || message.isBlank()) {
			message = "System startup error!";
		}
		if (strictMode) {
			// ROLLBACK: Stop everything that was already started in reverse order
			stopComponents();
			throw new SystemException(message, t);
		} else {
			logger.log(Level.SEVERE, message + (message.endsWith(".") ? " " : ". ")
					+ "Continuing in non-strict mode.", t);
		}
	}

	private void setupAutoCall(Proxy proxy, Map<String, Object> autoRunConf, Subject subject) throws Exception {
		if (proxy == null) {
			throw new IllegalArgumentException("Argument 'proxy' cannot be null.");
		}

		if (autoRunConf == null || autoRunConf.isEmpty()) {
			return;
		}

		final String methodName = String.valueOf(autoRunConf.get(Constants.RUNNABLE_METHOD));
		if (methodName == null || methodName.isBlank()) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_METHOD + "' is not set.");
		}

		String delayStr = String.valueOf(autoRunConf.get(Constants.RUNNABLE_DELAY));
		if (delayStr == null || delayStr.isBlank() || "null".equals(delayStr)) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_DELAY + "' is not set.");
		}

		String intervalStr = String.valueOf(autoRunConf.get(Constants.RUNNABLE_INTERVAL));
		if (intervalStr == null || intervalStr.isBlank() || "null".equals(intervalStr)) {
			throw new ConfigurationException(
					"Component auto-run configuration property '"
							+ Constants.RUNNABLE_INTERVAL + "' is not set.");
		}

		long delay;
		long interval;
		try {
			delay = Long.parseLong(delayStr);
			interval = Long.parseLong(intervalStr);
		} catch (NumberFormatException e) {
			throw new ConfigurationException("Invalid numeric value for auto-run configuration: " + e.getMessage());
		}

		if (interval < 0) {
			throw new ConfigurationException(
					"Component auto-call configuration property '"
							+ Constants.RUNNABLE_INTERVAL + "' cannot be negative.");
		}

		platformAdapter.registerAutoCall(proxy.getName(), methodName,
				delay, interval, this, subject);
	}

	private Manifest createManifest(String tag, MetaObject mObj) {
		Manifest manifest = new Manifest();
		manifest.setComponentType(tag);

		Map<String, Object> map = mObj.getDataMap();
		manifest.setName((String) map.get(Constants.NAME));
		manifest.setDisplayName((String) map.get(Constants.DISPLAY_NAME));
		manifest.setVersion((String) map.get(Constants.VERSION));
		manifest.setDescription((String) map.get(Constants.DESCRIPTION));
		manifest.setAuthor((String) map.get(Constants.AUTHOR));
		manifest.setImplementationClass((String) map.get(Constants.CLASS));

		Object rolesObj = map.get(Constants.REQUIRED_ROLES);
		if (rolesObj != null && rolesObj instanceof List) {
			List<?> rolesList = (List<?>) rolesObj;
			for (Object rObj : rolesList) {
				if (rObj instanceof String) {
					manifest.getRequiredRoles().add((String) rObj);
				}
			}
		}
		
		Object methodsObj = map.get(Constants.METHODS);
		if (methodsObj != null && methodsObj instanceof List) {
			List<Manifest.ExposedMethod> parsedMethods = new ArrayList<>();
			List<?> methodsList = (List<?>) methodsObj;
			for (Object mDescription : methodsList) {
				if (mDescription instanceof Map) {
					Map<?, ?> mMap = (Map<?, ?>) mDescription;
					Manifest.ExposedMethod method = new Manifest.ExposedMethod();
					method.name = (String) mMap.get("name");
					method.description = (String) mMap.get("description");
					method.returnType = (String) mMap.get("return");

					rolesObj = mMap.get(Constants.REQUIRED_ROLES);
					if (rolesObj != null && rolesObj instanceof List) {
						List<?> rolesList = (List<?>) rolesObj;
						for (Object rObj : rolesList) {
							if (rObj instanceof String) {
								method.requiredRoles.add((String) rObj);
							}
						}
					}

					Object paramsObj = mMap.get("parameters");
					if (paramsObj != null && paramsObj instanceof List) {
						List<?> paramsList = (List<?>) paramsObj;
						for (Object pObj : paramsList) {
							if (pObj instanceof Map) {
								Map<?, ?> pMap = (Map<?, ?>) pObj;
								Manifest.Parameter param = new Manifest.Parameter();
								param.name = (String) pMap.get("name");
								param.description = (String) pMap.get("description");
								param.type = (String) pMap.get("type");
								param.isRequired = Boolean.TRUE.equals(pMap.get("isRequired"));
								param.isSecret = Boolean.TRUE.equals(pMap.get("isSecret"));
								method.parameters.add(param);
							}
						}
					}
					parsedMethods.add(method);
				}
			}
			manifest.getExposedMethods().addAll(parsedMethods);
		}
		return manifest;
	}

	private void startComponents(String tag, List<MetaObject> metaObjectList, long timeoutSeconds) throws Exception {

		if (metaObjectList == null || metaObjectList.isEmpty()) {
			return;
		}

		if (startedProxies == null) {
			startedProxies = new ArrayList<>();
		}

		for (MetaObject mObj : metaObjectList) {
			Manifest manifest = createManifest(tag, mObj);
			Subject subject = new Subject();
        	SystemProxy proxy = new SystemProxy(mObj, manifest);
			if (Constants.COMPONENT.equalsIgnoreCase(manifest.getComponentType())) {
				proxy.setContext(systemContext);
				subject.getPrincipals().add(new RolePrincipal(Constants.SYSTEM));
			}
			else { // treated as a plugin regardless and create a plugin context with restricted access
				proxy.setContext(new PluginContext(systemContext, manifest, new Properties()));
				subject.getPrincipals().add(PluginPrincipal.create(manifest.getName(), manifest.getOrg()));
			}
			systemContext.add(proxy);
			try {
				if (!mObj.isAutoStart()) {
					logger.info("ℹ️ Skipping auto-start for " + tag + ": " + mObj.getName());
					continue;
				}

				Map<String, Object> autoRunConf = mObj.getAutoRunConf();

				CompletableFuture<Void> startFuture = CompletableFuture.runAsync(() -> {
					try {
						proxy.start();
						setupAutoCall(proxy, autoRunConf, subject);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}, startExecutor);

				// Wait for completion or timeout
				startFuture.get(timeoutSeconds, TimeUnit.SECONDS);

				logger.info("✅ Started " + tag + ": " + mObj.getName());
				startedProxies.add(proxy); // Track successful starts
			} catch (TimeoutException e) {
				String msg = "⏱️ Timeout: Component [" + mObj.getName() + "] failed to start within "
						+ timeoutSeconds + " seconds.";
				try {
					proxy.stop(); // Attempt to stop the proxy if start timed out
				} catch (Exception ex) {
					msg = msg + "\n⚠️ Failed to stop " + tag + " after start timeout: " + mObj.getName() + " - "
							+ ex.getMessage();
				}

				systemContext.remove(proxy);
				platformAdapter.unregisterAutoCall(proxy.getName());

				handleStartError(msg, e);
			} catch (Throwable t) {
				String msg = "❌ Failed to start " + tag + " [" + mObj.getName() + "].";
				try {
					proxy.stop(); // Attempt to stop the proxy if start timed out
				} catch (Exception ex) {
					msg = msg + "\n⚠️ Failed to stop " + tag + " after start up error: " + mObj.getName() + " - "
							+ ex.getMessage();
				}

				systemContext.remove(proxy);
				platformAdapter.unregisterAutoCall(proxy.getName());

				handleStartError(msg, t);
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
		for (SystemProxy p : startedProxies) {
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
	public CompletableFuture<Object> invokeAsync(String componentName, String methodName, Object[] params, String callerIp, Subject subject) {
		Objects.requireNonNull(subject, "Subject is mandatory for tracking execution.");
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(componentName, methodName, params, callerIp, subject);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	public Object invoke(String componentName, String methodName, Object[] params, String callerIp, Subject subject) throws Exception {

		// This method actually performs the invocation logic
		Objects.requireNonNull(subject, "Subject is mandatory for tracking execution.");

		long startTime = System.currentTimeMillis();
		if (systemContext == null) {
			throw new IllegalStateException("SystemContext is not initialized. Cannot invoke component.");
		}

		if (callerIp != null && !callerIp.isBlank()) {
			logger.info("Received invocation request from " + callerIp + " for target: " + componentName + ", method: "
					+ methodName);
		}

		if (standalone == false) {
			// Use the fastest node in the cluster to handle the request
			URL url = PerformanceTracker.getInstance().getBestNodeUrl();

			if (!this.localUrl.equals(url)) {
				try {
					// Perform remote invocation
					Proxy proxy = systemContext.getProxy(Constants.REMOTE_REVEILA, subject);
					startTime = System.currentTimeMillis();
					Object result = proxy.invoke("invoke", new Object[] { url, componentName, methodName, params });
					Long timeUsed = System.currentTimeMillis() - startTime;
					PerformanceTracker.getInstance().track(timeUsed, url); // Track remote invocation time
					return result;
				} catch (IllegalArgumentException e) {
					// Ignore, Remote Reveila not configured
				} catch (Exception e) {
					// Penalize the failed remote node
					PerformanceTracker.getInstance()
							.track(Long.valueOf(PerformanceTracker.DEFAULT_PENALTY_MS), url);
					logger.severe(
							"Remote invocation failed. Falling back to local invocation. Error: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}

		// Use local invocation if in standalone mode.
		// Also as backup for when there is no better remote option,
		// or when remote invocation failed.

		try {
			Proxy proxy = systemContext.getProxy(componentName, subject);
			startTime = System.currentTimeMillis();
			Object result = proxy.invoke(methodName, params);
			Long timeUsed = System.currentTimeMillis() - startTime;
			PerformanceTracker.getInstance().track(timeUsed, this.localUrl); // Track local invocation time
			return result;
		} catch (IllegalArgumentException e) {
			throw new ConfigurationException("Component '" + componentName + "' not found.", e);
		}

		
	}

	/**
	 * Performs a topological sort of MetaObjects based on their declared
	 * dependencies.
	 * 
	 * @param list The original list of MetaObjects.
	 * @return A sorted list where dependencies come before dependents.
	 */
	private List<MetaObject> sortMetaObjects(List<MetaObject> list) {
		Map<String, MetaObject> map = list.stream().collect(Collectors.toMap(MetaObject::getName, m -> m));
		List<MetaObject> sorted = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		Set<String> stack = new HashSet<>();

		for (MetaObject m : list) {
			try {
				visit(m, map, sorted, visited, stack);
			} catch (Exception e) {
				// Log or handle sort error (cycles are handled in Linter, but just in case)
				System.err.println("Sort error: " + e.getMessage());
			}
		}
		return sorted;
	}

	private void visit(MetaObject m, Map<String, MetaObject> map, List<MetaObject> sorted, Set<String> visited,
			Set<String> stack) throws Exception {
		if (visited.contains(m.getName()))
			return;
		if (stack.contains(m.getName()))
			throw new Exception("Circular dependency detected at: " + m.getName());

		stack.add(m.getName());
		List<String> deps = m.getDependencies();
		if (deps != null) {
			for (String dName : deps) {
				MetaObject dm = map.get(dName);
				if (dm != null) {
					visit(dm, map, sorted, visited, stack);
				}
			}
		}
		stack.remove(m.getName());
		visited.add(m.getName());
		sorted.add(m);
	}

	@Override
	public void notifyEvent(EventObject evtObj) throws Exception {
		systemContext.notifyEvent(evtObj);
	}
}
