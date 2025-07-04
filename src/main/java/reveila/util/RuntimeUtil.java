/*
 * Created on May 20, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class RuntimeUtil {

	public static Thread addShutdownHook(Runnable r) {
		Thread thread = new Thread(r);
		Runtime.getRuntime().addShutdownHook(thread);
		return thread;
	  }

}
