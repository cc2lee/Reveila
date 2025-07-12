package reveila.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
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

	private final Properties properties;
	private final EventManager eventManager;
	private final Logger logger;
	private final Map<Object, FileManager> fileManagers;
	private Cryptographer cryptographer;

	private final Path fileHome;
	private final Path tempFileHome;

	private final Map<String, Proxy> proxiesByName = new ConcurrentHashMap<>();
	private final Map<Class<?>, Proxy> proxiesByClass = new ConcurrentHashMap<>();

	public Cryptographer getCryptographer() {
		return cryptographer;
	}

	public Properties getProperties() {
		return properties;
	}

	public FileManager getFileManager(Proxy object) throws IOException {
		
		Objects.requireNonNull(object, "Argument 'object' must not be null");

		FileManager fileManager = fileManagers.get(object);
		if (fileManager == null) {
			// Create a new file manager for the object
			// The file manager is used to manage the files related to the object
			Path objectFileHome = fileHome.resolve(object.getName());
			Path objectTempHome = tempFileHome.resolve(object.getName());
			
			Files.createDirectories(objectFileHome);
			Files.createDirectories(objectTempHome);
			fileManager = new FileManager(objectFileHome.toString(), objectTempHome.toString());
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
			Map<Object, FileManager> fileManagers,
			EventManager eventManager,
			Logger logger,
			Cryptographer cryptographer) {

		this.properties = new Properties(Objects.requireNonNull(properties, "Argument 'properties' must not be null"));
		this.fileManagers = Objects.requireNonNull(fileManagers, "Argument 'fileManagers' must not be null");
		this.eventManager = Objects.requireNonNull(eventManager, "Argument 'eventManager' must not be null");
		this.logger = Objects.requireNonNull(logger, "Argument 'logger' must not be null");
		this.cryptographer = Objects.requireNonNull(cryptographer, "Argument 'cryptographer' must not be null");

		this.fileHome = Paths.get(this.properties.getProperty(Constants.S_SYSTEM_FILE_STORE, "data/files"));
		this.tempFileHome = Paths.get(this.properties.getProperty(Constants.S_SYSTEM_TMP_FILE_STORE, "data/temp"));
	}

	public void register(Proxy proxy) throws Exception {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");

		Class<?> implClass = Class.forName(proxy.getImplementationClassName());
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

	public void unregister(Proxy proxy) throws Exception {
		Objects.requireNonNull(proxy, "Argument 'proxy' must not be null");

		proxiesByName.remove(proxy.getName());
		proxiesByClass.remove(Class.forName(proxy.getImplementationClassName()));
		eventManager.removeEventReceiver(proxy);
		fileManagers.remove(proxy);
		logger.info("Unregistered component: " + proxy.getName());
	}

	public void destruct() {
		// Use a copy of the values to avoid ConcurrentModificationException if kill() modifies the map
		for (Proxy proxy : List.copyOf(proxiesByName.values())) {
			try {
				proxy.kill();
			} catch (Exception e) {
				logger.severe(
						"Failed to destruct object: " + proxy.getImplementationClassName() + " - " + e.getMessage());
			}
		}

		proxiesByName.clear();
		proxiesByClass.clear();
		eventManager.clear();
		fileManagers.clear();
		properties.clear();
		cryptographer = null;
		logger.info("System context destructed.");

		for (Handler handler : logger.getHandlers()) {
			handler.close();
		}
	}

	public Optional<Proxy> getProxy(String name) {
		return Optional.ofNullable(this.proxiesByName.get(name));
	}

	public Optional<Proxy> getProxy(Class<?> clazz) {
		return Optional.ofNullable(this.proxiesByClass.get(clazz));
	}

}
