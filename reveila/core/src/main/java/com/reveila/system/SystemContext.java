package com.reveila.system;

import java.util.Collection;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import com.reveila.crypto.Cryptographer;
import com.reveila.error.ConfigurationException;
import com.reveila.error.SecurityException;
import com.reveila.error.SystemException;
import com.reveila.event.EventConsumer;
import com.reveila.event.EventManager;

/**
 * @author Charles Lee
 * 
 *         This class serves the purpose of a system container.
 *         It controls the life cycle of all system level objects.
 */
public final class SystemContext {

	private Properties properties;
	private EventManager eventManager;
	private Logger logger;
	private Cryptographer cryptographer;
	private Map<String, SystemProxy> proxiesByName = new ConcurrentHashMap<>();
	private PlatformAdapter platformAdapter;
	private Subject subject;

	public PlatformAdapter getPlatformAdapter() {
		return platformAdapter;
	}

	public Cryptographer getCryptographer() {
		return cryptographer;
	}

	public Properties getProperties() {
		return properties;
	}

	public String getProperty(String name, Subject subject) {
		if (name == null || subject == null) {
			return null;
		}

		// Security Check: Only allow access to properties prefixed with the plugin's name,
		// or safe common properties, to enforce the Principle of Least Privilege.
		java.util.Set<PluginPrincipal> plugins = subject.getPrincipals(PluginPrincipal.class);
		if (!plugins.isEmpty()) {
			PluginPrincipal plugin = plugins.iterator().next();
			String pluginName = plugin.getName();
			
			String prefix1 = "plugin." + pluginName + ".";
			String prefix2 = pluginName + ".";

			if (name.startsWith(prefix1) || name.startsWith(prefix2)) {
				return properties.getProperty(name);
			}

			// Handle unprefixed keys requested by the plugin
			// by automatically checking for the prefixed version in the global properties
			String prefixedGlobal1 = properties.getProperty(prefix1 + name);
			if (prefixedGlobal1 != null) return prefixedGlobal1;

			String prefixedGlobal2 = properties.getProperty(prefix2 + name);
			if (prefixedGlobal2 != null) return prefixedGlobal2;

			// Safe globals
			if ("system.home".equals(name) || "system.mode".equals(name)) {
				return properties.getProperty(name);
			}
			
			// Allow common LLM keys to pass through if they are set globally without a prefix
			if ("apiKey".equals(name) || "endpoint".equals(name) || "provider".equals(name)) {
				return properties.getProperty(name);
			}

			// Deny access to other global properties
			logger.warning("Security Policy Violation: Plugin '" + pluginName + "' attempted to read unauthorized global property: " + name);
			return null;
		}

		// If it's a System role (not a plugin), allow full access
		return properties.getProperty(name);
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

		this.subject = new Subject();
		this.subject.getPrincipals().add(new RolePrincipal(Constants.SYSTEM));
		this.properties = new Properties(Objects.requireNonNull(properties, "Argument 'properties' must not be null"));
		this.eventManager = Objects.requireNonNull(eventManager, "Argument 'eventManager' must not be null");
		this.logger = Objects.requireNonNull(logger, "Argument 'logger' must not be null");
		this.cryptographer = Objects.requireNonNull(cryptographer, "Argument 'cryptographer' must not be null");
		this.platformAdapter = Objects.requireNonNull(platformAdapter, "Argument 'platformAdapter' must not be null");
	}

	public synchronized void add(SystemProxy proxy) throws SystemException, ConfigurationException {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");
		if (proxiesByName.size() == Integer.MAX_VALUE) {
			throw new SystemException("Too many components (" + proxiesByName.size() + ")!");
		}
		String name = proxy.getName();
		if (name == null || name.isBlank()) {
			throw new ConfigurationException("Component name cannot be null or blank.");
		}

		if (proxiesByName.containsKey(name)) {
			throw new ConfigurationException("Component name '" + name + "' is already in use.");
		}

		proxiesByName.put(name, proxy);
		if (proxy instanceof EventConsumer) {
			eventManager.addEventWatcher((EventConsumer)proxy);
		}
		
		proxy.setContext(this);
	}

	public synchronized void remove(SystemProxy proxy) {
		if (proxy == null) {
			return;
		}
		proxiesByName.remove(proxy.getName());
		if (proxy instanceof EventConsumer) {
			eventManager.removeEventConsumer((EventConsumer)proxy);
		}
		proxy.setContext(null);
	}

	public synchronized void clear() {
		proxiesByName.clear();
		eventManager.clear();
		properties.clear();
		cryptographer = null;
	}

	public SystemProxy getProxy(String name) throws com.reveila.error.SecurityException, IllegalArgumentException {
		return getProxy(name, this.subject);
	}

	public SystemProxy getProxy(String name, Subject subject) throws com.reveila.error.SecurityException, IllegalArgumentException {
		Objects.requireNonNull(name, "Component name cannot be null when getting a proxy.");
		Objects.requireNonNull(subject, "Subject cannot be null when getting a proxy.");
		Set<RolePrincipal> roles = subject.getPrincipals(RolePrincipal.class);
		if (roles == null || roles.isEmpty()) {
			throw new IllegalArgumentException("Subject must have at least one role.");
		}

		SystemProxy proxy = this.proxiesByName.get(name);
		if (proxy == null) {
			throw new IllegalArgumentException("Component '" + name + "' does not exist.");
		}

		List<String> requiredRoles = proxy.getRequiredRoles();
		if (requiredRoles == null || requiredRoles.isEmpty() || requiredRoles.contains("*")) {
			// No role check required
			return proxy;
		} else {
			for (RolePrincipal role : roles) {
				if (requiredRoles.contains(role.getName())) {
					return proxy;
				}
			}
			throw new SecurityException("Access to component '" + name + "' is denied. Subject must have one of the following roles: " + requiredRoles);
		}
	}

	public void notifyEvent(EventObject evtObj) {
		Collection<SystemProxy> proxies = this.proxiesByName.values();
		for (SystemProxy proxy : proxies) {
			if (proxy instanceof EventConsumer) {
				try {
					((EventConsumer) proxy).notifyEvent(evtObj);
				} catch (Throwable t) {
					logger.severe(t.toString() + t.getStackTrace());
				}
			}
		}
	}

}
