package reveila.system;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 * A specialized {@link URLClassLoader} for loading components (e.g., from JAR files)
 * at runtime. This class provides a named class loader for the Reveila component system,
 * allowing for dynamic extension of the application's classpath.
 * 
 * @author Charles Lee
 */
public class ComponentClassLoader extends URLClassLoader {

	/**
	 * Constructs a new ComponentClassLoader for the given URLs.
	 * @param urls the URLs from which to load classes and resources
	 */
	public ComponentClassLoader(URL[] urls) {
		super(urls);
	}

	/**
	 * Constructs a new ComponentClassLoader for the given URLs and parent class loader.
	 * @param urls the URLs from which to load classes and resources
	 * @param parent the parent class loader for delegation
	 */
	public ComponentClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	/**
	 * Constructs a new ComponentClassLoader for the specified URLs, parent class loader,
	 * and URL stream handler factory.
	 * @param urls the URLs from which to load classes and resources
	 * @param parent the parent class loader for delegation
	 * @param factory the URLStreamHandlerFactory to use when creating new URLs
	 */
	public ComponentClassLoader(
		URL[] urls,
		ClassLoader parent,
		URLStreamHandlerFactory factory) {
		super(urls, parent, factory);
	}
	
}
