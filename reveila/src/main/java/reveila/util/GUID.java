/*
 * Created on Mar 4, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util;

/**
 * @author Charles Lee
 *
 * A 32 byte GUID (Globally Unique ID) generator implementation.
 * <p><b>Usage:</b></p>
 * <p><code>
 * Object object = new Object();
 * String guid = GUID.getGUID(object);
 * </code></p>
 */
public class GUID {

	/* Cached per JVM server IP. */
	private static String hexServerIP = null;

	/* Initialize the secure random instance. */
	private static final java.security.SecureRandom seeder = new java.security.SecureRandom();

	/**
	 * Creates a 32 byte GUID (Globally Unique ID) for the given <code>Object obj</code>.
	 * @param obj - The Object for which the GUID is to be generated.
	 * @return A 32 byte GUID string.
	 */
	public static final String getGUID(Object obj) {
		StringBuffer tmpBuffer = new StringBuffer(16);
		if (hexServerIP == null) {
			java.net.InetAddress localInetAddress = null;
			try {
				// get the inet address
				localInetAddress = java.net.InetAddress.getLocalHost();
			}
			catch (java.net.UnknownHostException uhe) {
				System.err.println(GUID.class.getName() +
						": Could not get the local IP address using InetAddress.getLocalHost()!");
				// TODO: find a better way to get around this...
				uhe.printStackTrace();
				return null;
			}
			byte[] serverIP = localInetAddress.getAddress();
			hexServerIP = hexFormat(getInt(serverIP), 8);
		}
		String hashcode = hexFormat(System.identityHashCode(obj), 8);
		tmpBuffer.append(hexServerIP);
		tmpBuffer.append(hashcode);

		long timeNow      = System.currentTimeMillis();
		int timeLow       = (int)timeNow & 0xFFFFFFFF;
		int node          = seeder.nextInt();

		StringBuffer guid = new StringBuffer(32);
		guid.append(hexFormat(timeLow, 8));
		guid.append(tmpBuffer.toString());
		guid.append(hexFormat(node, 8));
		
		return guid.toString();
	}

	private static int getInt(byte bytes[]) {
		int i = 0;
		int j = 24;
		for (int k = 0; j >= 0; k++) {
			int l = bytes[k] & 0xff;
			i += l << j;
			j -= 8;
		}
		return i;
	}

	private static String hexFormat(int i, int j) {
		String s = Integer.toHexString(i);
		return padHex(s, j) + s;
	}

	private static String padHex(String s, int i) {
		StringBuffer tmpBuffer = new StringBuffer();
		if (s.length() < i) {
			for (int j = 0; j < i - s.length(); j++) {
				tmpBuffer.append('0');
			}
		}
		return tmpBuffer.toString();
	}
}
