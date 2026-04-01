package com.reveila.system;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MetaObject {

	private Map<String, Object> dataMap;
	private boolean isPlugin;

	public MetaObject(Map<String, Object> map) {
		if (map == null) {
			throw new IllegalArgumentException("Map cannot be null.");
		}
		this.dataMap = map;
	}

	public boolean isPlugin() {
		return isPlugin;
	}

	public void setPlugin(boolean plugin) {
		isPlugin = plugin;
	}

	public Map<String, Object> getDataMap() {
		return this.dataMap;
	}

	/**
	 * Checks if the component is configured to be thread-safe, which implies
	 * a singleton lifecycle (one instance is created and reused).
	 * Defaults to {@code true} if the property is not specified.
	 * 
	 * @return the configured value of the {@code thread-safe} property, or
	 *         {@code true} if not specified (default).
	 */
	public boolean isThreadSafe() {
		Object value = this.dataMap.get(Constants.THREAD_SAFE);
		return !"false".equalsIgnoreCase(String.valueOf(value)); // default to true if not specified, or incorrect value
	}

	public String getName() {
		return (String) this.dataMap.get(Constants.NAME);
	}

	public String getImplementationClassName() {
		return (String) this.dataMap.get(Constants.CLASS);
	}

	public String getDescription() {
		return (String) this.dataMap.get(Constants.DESCRIPTION);
	}

	public String getVersion() {
		return (String) this.dataMap.get(Constants.VERSION);
	}

	public String getAuthor() {
		return (String) this.dataMap.get(Constants.AUTHOR);
	}

	public String getLicense() {
		return (String) this.dataMap.get(Constants.LICENSE_TOKEN);
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getArguments() {
		Object value = this.dataMap.get(Constants.ARGUMENTS);
		return (value instanceof List) ? (List<Map<String, Object>>) value : Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getAutoRunConf() {
		Object value = this.dataMap.get(Constants.RUNNABLE);
		return (value instanceof Map) ? (Map<String, Object>) value : Collections.emptyMap();
	}

	public boolean isHotDeployEnabled() {
		return "true".equalsIgnoreCase(String.valueOf(this.dataMap.get(Constants.HOT_DEPLOY)));
	}

	@SuppressWarnings("unchecked")
	public List<String> getDependencies() {
		Object value = this.dataMap.get(Constants.DEPENDENCIES);
		return (value instanceof List) ? (List<String>) value : Collections.emptyList();
	}

	@SuppressWarnings("unchecked")
	public boolean requiresRuntimeIsolation() {
		Object perimeter = this.dataMap.get(Constants.SECURITY_PERIMETER);
		if (perimeter instanceof Map) {
			Map<String, Object> pMap = (Map<String, Object>) perimeter;
			Object iv = pMap.get(Constants.ISOLATION);
			if (iv instanceof Boolean) {
				return (Boolean) iv;
			}
			return "true".equalsIgnoreCase(String.valueOf(iv));
		}

		return false;
	}

	/**
	 * ADR 0006: Retrieves the network policy from the security-perimeter.
	 * 
	 * @return The network policy string (e.g., "restricted"), or null if not defined.
	 */
	@SuppressWarnings("unchecked")
	public String getNetworkPolicy() {
		Object perimeter = this.dataMap.get(Constants.SECURITY_PERIMETER);
		if (perimeter instanceof Map) {
			Map<String, Object> pMap = (Map<String, Object>) perimeter;
			return (String) pMap.get(Constants.NETWORK);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public boolean isAutoStart() {
		Object service = this.dataMap.get(Constants.SERVICE);
		if (service instanceof Map) {
			Map<String, Object> sMap = (Map<String, Object>) service;
			Object autoStart = sMap.get(Constants.AUTO_START);
			if (autoStart instanceof Boolean) {
				return (Boolean) autoStart;
			}
			return !"false".equalsIgnoreCase(String.valueOf(autoStart));
		}
		return true; // Default to true
	}

	public Manifest getManifest() {
		return (Manifest) this.dataMap.get(Constants.MANEFEST);
	}
}
