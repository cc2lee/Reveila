package reveila.examples;

import reveila.system.MetaObject;
import reveila.system.Service;

/**
 * @author Charles Lee
 *
 * An example ServiceProvider implementation of the EchoService.
 * Notice that this class extends AbstractServiceProvider, a convenience
 * abstract class that implements both the Service and Loggable interfaces.
 */
public class EchoService extends Service {

	public EchoService(MetaObject objectDescriptor) throws Exception {
		super(objectDescriptor);
	}

	private boolean reverse = false;
	private int repeat = 0;
	
	public String echo(String arg) throws Exception {
		if (arg == null) {
			return "null";
		}
		
		String string = String.valueOf(arg);
		
		if (reverse) {
			char[] chars = string.toCharArray();
			int left = 0;
			int right = chars.length - 1;
			char buf;
			while (left < right) {
				buf = chars[left];
				chars[left] = chars[right];
				chars[right] = buf;
				left++;
				right--;
			}
			string = new String(chars);
		}
		String newString = string;
		for (int i = 0; i < repeat; i++) {
			newString += ", " + string;
		}
		
		return newString;
	}

	/**
	 * @param b
	 */
	public void setReverse(Boolean b) {
		this.reverse = b != null && b.booleanValue();
	}
	
	/**
	 * @param n
	 */
	public void setRepeat(Integer n) {
		this.repeat = n == null ? 0 : n.intValue();
	}
}
