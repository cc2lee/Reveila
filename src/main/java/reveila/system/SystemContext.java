package reveila.system;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Logger;

import reveila.crypto.Cryptographer;
import reveila.util.event.EventManager;
import reveila.util.io.FileManager;


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
	private Map<Object,FileManager> fileManagers;
	private Cryptographer cryptographer;

	private String fileHome;
	private String tempFileHome;

	public Cryptographer getCryptographer() {
		return cryptographer;
	}

	private Map<Class<?>,Proxy> proxies 
			= Collections.synchronizedMap(new HashMap<Class<?>,Proxy>());
	
	public Properties getProperties() {
		return properties;
	}

	Properties implClassNames = new Properties();

	public FileManager getFileManager(Proxy object) throws IOException {
		if (object == null) {
			throw new IllegalArgumentException("Argument 'object' must not be null");
		}

		FileManager fileManager = fileManagers.get(object);
		if (fileManager == null) {
				// Create a new file manager for the object
				// The file manager is used to manage the files related to the object
				String h = fileHome + object.getName();
				String t = tempFileHome + object.getName();
				if (!new File(h).mkdirs()) {
					throw new RuntimeException("Failed to create directory: " + h);
				}
				if (!new File(t).mkdirs()) {
					throw new RuntimeException("Failed to create directory: " + t);
				}
				fileManager = new FileManager(h, t);
				fileManagers.put(object, fileManager);
			}
		return fileManager;
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public Logger getLogger(Object object) {
		if (object != null && object instanceof Proxy) {
			return Logger.getLogger(this.logger.getName() + "." + ((Proxy)object).getName());
		} else {
			return this.logger;
		}
	}
	
	public SystemContext(
			Properties properties, 
			Map<Object,FileManager> fileManagers, 
			EventManager eventManager, 
			Logger logger, 
			Cryptographer cryptographer) {

		if (properties == null) {
			throw new IllegalArgumentException("Argument 'properties' must not be null");
		}
		if (fileManagers == null) {
			throw new IllegalArgumentException("Argument 'fileManagers' must not be null");
		}
		if (eventManager == null) {
			throw new IllegalArgumentException("Argument 'eventManager' must not be null");
		}
		if (logger == null) {
			throw new IllegalArgumentException("Argument 'logger' must not be null");
		}
		if (cryptographer == null) {
			throw new IllegalArgumentException("Argument 'cryptographer' must not be null");
		}
		
		this.properties = new Properties(properties);
		this.fileManagers = fileManagers;
		this.eventManager = eventManager;
		this.logger = logger;
		this.cryptographer = cryptographer;
		this.fileHome = this.properties.getProperty(Constants.S_SYSTEM_FILE_STORE);
		this.tempFileHome = this.properties.getProperty(Constants.S_SYSTEM_TMP_FILE_STORE);
		
		if (!fileHome.endsWith(File.separator)) {
			fileHome += File.separator;
		}

		if (!tempFileHome.endsWith(File.separator)) {
			tempFileHome += File.separator;
		}
	}

	public void register(Proxy proxy) throws Exception {
		if (proxy == null) {
			throw new IllegalArgumentException("Argument 'proxy' must not be null");
		}

		Class<?> implClass = Class.forName(proxy.getImplementationClassName());
		if (proxies.containsKey(implClass)) {
			throw new IllegalArgumentException(
					"Registering more than 1 proxy of the same implementation '" + implClass + "' is not permitted!");
		} else {
			implClassNames.setProperty(proxy.getName(), proxy.getImplementationClassName());
			proxies.put(implClass, proxy);
			eventManager.addEventReceiver(proxy);
		}
	}

	public void unregister(Proxy proxy) throws Exception {
		if (proxy == null) {
			throw new IllegalArgumentException("Argument 'proxy' must not be null");
		}

		implClassNames.remove(proxy.getName());
		proxies.remove(Class.forName(proxy.getImplementationClassName()));
		eventManager.removeEventReceiver(proxy);
		fileManagers.remove(proxy);
	}

	public void destruct() {
		for (Proxy proxy : proxies.values()) {
			try {
				proxy.kill();
			} catch (Exception e) {
				logger.severe(
						"Failed to destruct object: " + proxy.getImplementationClassName() + " - " + e.getMessage());
			}
		}

		proxies.clear();
		eventManager.clear();
		fileManagers.clear();
		properties.clear();
		;
		cryptographer = null;
		logger.info("System context destructed.");

		for (Handler handler : logger.getHandlers()) {
			handler.close();
		}
	}

	public Proxy getProxy(String name) {
		
		// ** name of the object as defined in the config file, not the class name **
		
		Proxy proxy = null;
		try {
			proxy = this.proxies.get(Class.forName(this.implClassNames.getProperty(name)));
		} catch (ClassNotFoundException e) {
			logger.info("Failed to retrieve proxy - " + e.getMessage());
			e.printStackTrace();
		}
		return proxy;
	}

	public Proxy getProxy(Class<?> clazz) {
		return this.proxies.get(clazz);
	}

}
