package com.reveila.system;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.reveila.crypto.Cryptographer;
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

	public EventManager getEventManager() {
		return eventManager;
	}

	public Logger getLogger() {
		return this.logger;
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

	public synchronized void add(Proxy proxy) throws SystemException {
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
		proxy.setSystemContext(this);
		proxiesByName.put(name, proxy);
		eventManager.addEventWatcher(proxy);
	}

	public synchronized void remove(Proxy proxy) {
		if (proxy == null) {
			return;
		}
		proxiesByName.remove(proxy.getName());
		eventManager.removeEventConsumer(proxy);
	}

	public synchronized void clear() {
		proxiesByName.clear();
		eventManager.clear();
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
