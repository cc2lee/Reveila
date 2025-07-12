/*
 * Created on Jan 21, 2004
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class TimeFormat {

	private static TimeFormat format = new TimeFormat();
	private static final int[] factors = {
		1000, 60, 60, 24
	};
	
	private static final String[] units = {
		"ms", "s", "m", "h", "days"
	};
	
	public TimeFormat() {
		super();
	}
	
	public static final TimeFormat getInstance() {
		return format;
	}
	
	public String format(long miniSec) {
		long value = miniSec;
		int cursor = 0;
		int factor = factors[cursor];
		StringBuffer strBuf = new StringBuffer();
		
		while (factor < value && cursor < factors.length) {
			strBuf.insert(0, (value % factor) + units[cursor]);
			value = (long) value / factor;
			if (value > 0) {
				strBuf.insert(0, ":");
			}
			cursor++;
		}
		
		if (value > 0) {
			strBuf.insert(0, value + units[cursor]);
		}
		
		return strBuf.toString();
	}

}
