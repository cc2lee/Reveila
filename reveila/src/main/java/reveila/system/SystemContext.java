package reveila.system;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import reveila.crypto.Cryptographer;
import reveila.platform.PlatformAdapter;
import reveila.util.event.EventManager;
import reveila.util.io.StorageManager;


/**
 * @author Charles Lee
 * 
 * This class serves the purpose of a system container.
 * It controls the life cycle of all system level objects.
 */
public final class SystemContext {

	private Properties properties;
	private EventManager eventManager;
	private Logger logger;
	private Map<Object, StorageManager> storageManagers = new ConcurrentHashMap<>();
	private Map<String, Logger> loggersByName = new ConcurrentHashMap<>();
	private Cryptographer cryptographer;
	private Map<String, Proxy> proxiesByName = new ConcurrentHashMap<>();
	private Map<Class<?>, Proxy> proxiesByClass = new ConcurrentHashMap<>();
	private PlatformAdapter platformAdapter;

	public Cryptographer getCryptographer() {
		return cryptographer;
	}

	public Properties getProperties() {
		return properties;
	}

	public StorageManager getStorageManager(Proxy proxy) throws IOException {
		
		Objects.requireNonNull(proxy, "Argument 'object' must not be null");

		StorageManager storageManager = storageManagers.get(proxy);
		if (storageManager == null) {
			// Use double-checked locking to ensure thread-safe, lazy initialization.
			synchronized (this.storageManagers) {
				// Check again in case another thread created the manager while we were waiting for the lock.
				storageManager = storageManagers.get(proxy);
				if (storageManager == null) {
					// Each Proxy object has its own storage manager
					storageManager = new StorageManager(platformAdapter, proxy);
					storageManagers.put(proxy, storageManager);
				}
			}
		}
		return storageManager;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public Logger getLogger(Object object) {
		if (object instanceof Proxy) {
			String proxyName = ((Proxy) object).getName();
			// Use computeIfAbsent for a concise, thread-safe way to get or create the logger.
			return this.loggersByName.computeIfAbsent(proxyName,
					name -> Logger.getLogger(this.logger.getName() + "." + name));
		}
		return this.logger; // Return the root logger for non-proxy objects or null
	}
	
	public SystemContext(
			Properties properties,
			EventManager eventManager,
			Logger logger,
			Cryptographer cryptographer,
			PlatformAdapter platformAdapter) {

		this.properties = new Properties(Objects.requireNonNull(properties, "Argument 'properties' must not be null"));
		this.eventManager = Objects.requireNonNull(eventManager, "Argument 'eventManager' must not be null");
		this.logger = Objects.requireNonNull(logger, "Argument 'logger' must not be null");
		this.cryptographer = Objects.requireNonNull(cryptographer, "Argument 'cryptographer' must not be null");
		this.platformAdapter = Objects.requireNonNull(platformAdapter, "Argument 'platformAdapter' must not be null");
	}

	public synchronized void register(Proxy proxy) throws Exception {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");

		//Class<?> implClass = Class.forName(proxy.getImplementationClassName());
		Class<?> implClass = proxy.getImplementationClass();
		String proxyName = proxy.getName();

		if (proxiesByClass.containsKey(implClass) || proxiesByName.containsKey(proxyName)) {
			throw new IllegalStateException(
					"A proxy with name '" + proxyName + "' or implementation class '" + implClass.getName() + "' is already registered.");
		}

		proxiesByName.put(proxyName, proxy);
		proxiesByClass.put(implClass, proxy);
		eventManager.addEventReceiver(proxy);
		logger.info("Registered component: " + proxyName);
	}

	public synchronized void unregister(Proxy proxy) throws Exception {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");

		proxiesByName.remove(proxy.getName());
		//proxiesByClass.remove(Class.forName(proxy.getImplementationClassName()));
		proxiesByClass.remove(proxy.getImplementationClass());
		eventManager.removeEventReceiver(proxy);
		storageManagers.remove(proxy);
		loggersByName.remove(proxy.getName());
		logger.info("Unregistered component: " + proxy.getName());
	}

	public void destruct() {
		// First, gracefully stop all stoppable services.
		// This is done before unregistering to allow services to interact during shutdown.
		logger.info("Stopping all components...");
		for (Proxy proxy : List.copyOf(proxiesByName.values())) {
			try {
				proxy.stop();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Failed to stop component: " + proxy.getName(), e);
			}
		}

		// Then, kill (unregister) all proxies.
		for (Proxy proxy : List.copyOf(proxiesByName.values())) {
			try {
				this.unregister(proxy);
				proxy.cleanup();
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Failed to unregister or clean up component: " + proxy.getName(), e);
			}
		}

		proxiesByName.clear();
		proxiesByClass.clear();
		loggersByName.clear();
		eventManager.clear();
		storageManagers.clear();
		properties.clear();
		cryptographer = null;
		logger.info("System context destructed.");
	}

	public Optional<Proxy> getProxy(String name) {
		return Optional.ofNullable(this.proxiesByName.get(name));
	}

}
