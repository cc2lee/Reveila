/**
 * @author Charles Lee
 */
package reveila;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
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
import java.util.concurrent.CompletableFuture;

import reveila.crypto.DefaultCryptographer;
import reveila.error.ConfigurationException;
import reveila.platform.PlatformAdapter;
import reveila.system.NodePerformanceTracker;
import reveila.system.Constants;
import reveila.system.JsonConfiguration;
import reveila.system.MetaObject;
import reveila.system.Proxy;
import reveila.system.SystemContext;
import reveila.util.TimeFormat;
import reveila.util.event.EventManager;

public class Reveila {

	private PlatformAdapter platformAdapter;
	private Properties properties;
	private SystemContext systemContext;
	private Logger logger;
	private String localAddress;
	private URL localUrl;
	
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

			if (this.platformAdapter != null) {
				try {
					this.platformAdapter.destruct();
				} catch (Exception e) {
					error = true;
					System.err.println("Failed to destruct platform adapter: " + e.getMessage());
					e.printStackTrace(System.err);
				}
			}

			if (!error) {
				System.out.println("System shut down successfully\n\n");
			} else {
				System.out.println("System shut down with errors. Check logs for details.\n\n");
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
		this.localAddress = getLocalHost();
		this.localUrl = new URL("http://" + this.localAddress + "/");
		
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
            System.err.println("Warning: Could not load logo resource file.");
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
			throw new ConfigurationException("System property '" + Constants.CRYPTOGRAPHER_SECRETKEY + "' is not set.");
		}

		String charset = props.getProperty(Constants.CHARACTER_ENCODING);
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
				try (InputStream is = this.platformAdapter.getInputStream(PlatformAdapter.CONF_STORAGE, fileName)) {
					logger.info("Loading components from: " + fileName);
					JsonConfiguration group = new JsonConfiguration(is, this.logger);
					List<MetaObject> list = group.read();
					for (MetaObject mObj : list) {
						if (mObj.isStartOnLoad()) {
							logger.info("Starting component on load: " + mObj.getName());
							// The Proxy class handles both stateful (singleton) and stateless (prototype) lifecycles.
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
	 * @param methodName The name of the method to invoke.
	 * @param params The parameters to pass to the method.
	 * @return The result of the method invocation.
	 * @throws Exception if the component or method is not found, or if invocation fails.
	 */
	public Object invoke(String componentName, String methodName, Object[] params) throws Exception {
		return invoke(componentName, methodName, params, localAddress);
	}

	/**
	 * Asynchronous version of the invoke method, returning a CompletableFuture.
	 * This allows non-blocking calls from the caller.
	 * Any thread that needs to synchronously obtain the result of a CompletableFuture
	 * and is willing to block until the computation is complete, use:
     *  try {
	 *      future.join();
	 *  } catch (Exception e) {
	 *      // Handle exceptions if join() throws one
	 *  }
	 * @param componentName The name of the component to invoke.
	 * @param methodName The name of the method to invoke.
	 * @param params The parameters to pass to the method.
	 * @return The result of the method invocation.
	 */
	public CompletableFuture<Object> invokeAsync(String componentName, String methodName, Object[] params) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return invoke(componentName, methodName, params, localAddress);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, Runnable::run);
	}

	public Object invoke(String componentName, String methodName, Object[] params, String callerIp) throws Exception {
		long startTime = System.currentTimeMillis();
		if (systemContext == null) {
			throw new IllegalStateException("SystemContext is not initialized. Cannot invoke component.");
		}

		if (callerIp != null 
				&& this.localAddress != null 
				&& !callerIp.equalsIgnoreCase(this.localAddress)) {
			logger.info("Received remote invocation request from " + callerIp + " for target: " + componentName + ", method: " + methodName);
		}

		Proxy proxy = null;
		URL url = NodePerformanceTracker.getInstance().getBestNodeUrl();
		
		if (url != null 
				&& !url.getHost().equalsIgnoreCase("localhost") 
				&& !url.getHost().equals(this.localAddress)) {
			Optional<Proxy> remoteProxy= systemContext.getProxy(Constants.REMOTE_INVOCATION_SERVICE);
			if (remoteProxy.isPresent()) {
				proxy = remoteProxy.get();
			}
		}

		boolean penalize = false;
		if (proxy != null) { // Send the request to the remote server
			try {
				Object result = proxy.invoke("invoke", new Object[]{url, componentName, methodName, params});
				NodePerformanceTracker.getInstance().track(System.currentTimeMillis() - startTime, url); // Track remote invocation time
				return result;
			} catch (Exception e) {
				penalize = true;
				logger.severe("Remote invocation failed. Falling back to local invocation. Error: " + e.getMessage());
				e.printStackTrace(System.err);
			}
		}
		
		// Local invocation if no better remote option, or remote invocation failed
		Optional<Proxy> localProxy = systemContext.getProxy(componentName);
		if (localProxy.isPresent()) {
			proxy = localProxy.get();
		} else {
			throw new ConfigurationException("Component '" + componentName + "' not found.");
		}
		Object result = proxy.invoke(methodName, params);
		long timeUsed = System.currentTimeMillis() - startTime;
		NodePerformanceTracker.getInstance().track(timeUsed, localUrl); // Track local invocation time
		if (penalize) {
			NodePerformanceTracker.getInstance().track(timeUsed + NodePerformanceTracker.DEFAULT_PENALTY_MS, url); // Penalize the remote node for failure
		}
		return result;
	}

	private String getLocalHost() {
		String address = null;
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            address = localHost.getHostAddress();
        } catch (UnknownHostException e) {
			logger.info("Unable to retrieve local IP address. Invocation time tracking is disabled. Error: " + e.getMessage());
        }

		return address;
    }
}
