package reveila.util;

/**
 * @author Charles Lee
 * 
 * This interface defines an abstract Rule that can be used
 * to qualify a target object.
 */
public interface Rule {
	public boolean accept(Object object);
}
