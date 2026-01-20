package com.reveila.system;

import java.util.List;
import java.util.Map;

public class MetaObject {

	private Map<String, Object> dataMap;
	
	public MetaObject (Map<String,Object> map) {
		if (map == null) {
			throw new IllegalArgumentException("Map cannot be null.");
		}
		this.dataMap = map;
	}

	public Map<String,Object> getDataMap() {
		return this.dataMap;
	}

	/**
	 * Checks if the component is configured to be thread-safe, which implies
	 * a singleton lifecycle (one instance is created and reused).
	 * Defaults to {@code true} if the property is not specified.
	 * @return the configured value of the {@code thread-safe} property, or {@code true} if not specified (default).
	 */
	public boolean isThreadSafe() {
		Object value = this.dataMap.get(Constants.THREAD_SAFE);
		return !"false".equalsIgnoreCase(String.valueOf(value)); // default to true if not specified, or incorrect value
	}

	public String getName() {
		return (String)this.dataMap.get(Constants.NAME);
	}

	public String getImplementationClassName() {
		return (String)this.dataMap.get(Constants.CLASS);
	}

	public String getDescription() {
		return (String)this.dataMap.get(Constants.DESCRIPTION);
	}

	public String getVersion() {
		return (String)this.dataMap.get(Constants.VERSION);
	}

	public String getAuthor() {
		return (String)this.dataMap.get(Constants.AUTHOR);
	}

	public String getLicense() {
		return (String)this.dataMap.get(Constants.LICENSE_TOKEN);
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getArguments() {
		return (List<Map<String, Object>>)this.dataMap.get(Constants.ARGUMENTS);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getAutoRunConf() {
		return (Map<String, Object>)this.dataMap.get(Constants.RUNNABLE);
	}

	@SuppressWarnings("unchecked")
	public String getPluginDir() {
        Map<String, Object> plugin = (Map<String, Object>)this.dataMap.get(Constants.PLUGIN);
		return (String)plugin.get(Constants.DIRECTORY);
    }

	@SuppressWarnings("unchecked")
	public boolean isHotDeployEnabled() {
        Map<String, Object> plugin = (Map<String, Object>)this.dataMap.get(Constants.PLUGIN);
		String hotDeploy = (String)plugin.get(Constants.HOT_DEPLOY);
		return "true".equalsIgnoreCase(hotDeploy);
    }

	public int getStartPriority() {
		try {
			return Integer.parseInt((String)this.dataMap.get(Constants.START_PRIORITY));
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	@SuppressWarnings("unchecked")
	public List<String> getDependencies() {
		return (List<String>)this.dataMap.get(Constants.DEPENDENCIES);
	}
}