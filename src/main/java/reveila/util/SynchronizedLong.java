/*
 * Created on Jan 15, 2004
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
public class SynchronizedLong {

	private long value = 0;
	
	public SynchronizedLong() {
		this(0);
	}
	
	public SynchronizedLong(long startValue) {
		super();
		this.value = startValue;
	}
	
	public long nextValue() {
		synchronized (this) {
			value = value + 1;
			return value;
		}
	}
	
	public void setValue(long newValue) {
		synchronized (this) {
			this.value = newValue;
		}
	}

}
