/*
 * Created on May 18, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.system;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class ComponentClassLoader extends URLClassLoader {

	/**
	 * @param urls
	 */
	public ComponentClassLoader(URL[] urls) {
		super(urls);
	}

	/**
	 * @param urls
	 * @param parent
	 */
	public ComponentClassLoader(URL[] urls, ClassLoader parent) {
		super(urls, parent);
	}

	/**
	 * @param urls
	 * @param parent
	 * @param factory
	 */
	public ComponentClassLoader(
		URL[] urls,
		ClassLoader parent,
		URLStreamHandlerFactory factory) {
		super(urls, parent, factory);
	}
	
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return super.findClass(name);
	}

}
