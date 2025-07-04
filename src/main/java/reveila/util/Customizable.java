package reveila.util;

import java.util.ResourceBundle;

/**
 * @author Charles Lee
 * 
 * Signature that indicates the implementing class
 * supports using of ResourceBundle.
 */
public interface Customizable {

	/**
	 * Sets the ResourceBundle object for customization.
	 * @param r - the ResourceBundle object.
	 */
	public void setResourceBundle(ResourceBundle r);

}
