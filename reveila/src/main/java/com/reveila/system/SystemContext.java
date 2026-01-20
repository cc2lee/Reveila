package com.reveila.system;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.reveila.crypto.Cryptographer;
import com.reveila.data.FileAdapter;
import com.reveila.error.SystemException;
import com.reveila.event.EventManager;
import com.reveila.util.GUID;


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
	private Map<Proxy, FileAdapter> fileAdapters = new ConcurrentHashMap<>();
	private Map<String, Logger> loggersByName = new ConcurrentHashMap<>();
	private Cryptographer cryptographer;
	private Map<String, Proxy> proxiesByName = new ConcurrentHashMap<>();
	private PlatformAdapter platformAdapter;

	public PlatformAdapter getPlatformAdapter() {
		return platformAdapter;
	}

	public Cryptographer getCryptographer() {
		return cryptographer;
	}

	public Properties getProperties() {
		return properties;
	}

	public FileAdapter getFileAdapter(Proxy proxy) throws IOException {
		
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null.");

		FileAdapter fileAdapter = fileAdapters.get(proxy);
		if (fileAdapter == null) {
			// Use double-checked locking to ensure thread-safe, lazy initialization.
			synchronized (this.fileAdapters) {
				// Check again in case another thread created the manager while we were waiting for the lock.
				fileAdapter = fileAdapters.get(proxy);
				if (fileAdapter == null) {
					// Each Proxy object has its own storage manager
					fileAdapter = new FileAdapter(platformAdapter, proxy);
					fileAdapters.put(proxy, fileAdapter);
				}
			}
		}
		return fileAdapter;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public Logger getLogger(Object object) {
		if (object != null && object instanceof Proxy) {
			String proxyName = ((Proxy) object).getName();
			// Use computeIfAbsent for a concise, thread-safe way to get or create the logger.
			return this.loggersByName.computeIfAbsent(proxyName,
					name -> Logger.getLogger(this.logger.getName() + "." + name));
		}
		return this.logger; // Return the root logger for non-proxy objects
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

	public synchronized void register(Proxy proxy) throws SystemException {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");
		if (proxiesByName.size() == Integer.MAX_VALUE) {
			throw new SystemException("Too many components (" + proxiesByName.size() + ")!");
		}
		String name = proxy.getName();
		if (name == null || name.isBlank()) {
			name = GUID.getGUID(proxy);
		}

		for (int i = 0; proxiesByName.containsKey(name); i++) {
			if (i == Integer.MAX_VALUE) {
				throw new SystemException("Too many components (" + proxiesByName.size() + ")!");
			}
			name = name + "_" + i;
		}

		proxy.setName(name);
		proxiesByName.put(name, proxy);
		eventManager.addEventWatcher(proxy);
		proxy.setSystemContext(this);
		logger.info("Registered component: " + proxy.toString());
	}

	public synchronized void unregister(Proxy proxy) {
		if (proxy == null) {
			return;
		}
		proxiesByName.remove(proxy.getName());
		eventManager.removeEventConsumer(proxy);
		fileAdapters.remove(proxy);
		loggersByName.remove(proxy.getName());
	}

	public synchronized void clear() {
		proxiesByName.clear();
		loggersByName.clear();
		eventManager.clear();
		fileAdapters.clear();
		properties.clear();
		cryptographer = null;
	}

	public Optional<Proxy> getProxy(String name) {
		Objects.requireNonNull(name, "Component name cannot be null when getting a proxy.");
		return Optional.ofNullable(this.proxiesByName.get(name));
	}

	public List<Proxy> getProxies() {
		return List.copyOf(this.proxiesByName.values());
	}

}
