/*
 * Created on May 20, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.reveila.system;

import java.util.Properties;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class RuntimeUtil {

	public static Properties getArgsAsProperties(String[] args) {
		Properties cmdArgs = new Properties();
		if (args != null) {
			for (String arg : args) {
				String[] parts = arg.split("=", 2);
				if (parts.length == 2 && !parts[0].isEmpty()) {
					cmdArgs.put(parts[0], parts[1]);
				} else {
					// It's good practice to warn about arguments that don't fit the expected format.
					// Since the logger isn't configured yet, System.err is the best option.
					System.err.println("Warning: Ignoring malformed command-line argument: " + arg);
				}
			}
		}
		return cmdArgs;
	}

}
