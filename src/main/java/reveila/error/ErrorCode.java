package reveila.error;

/**
 * @author Charles Lee
 * 
 * Signature that indicates the implementing class supports the ErrorCode concept.
 * This interface is often implemented by sub-classes of Throwable.
 * The <code>getErrorCode</code> method returns the error code.
 */
public interface ErrorCode {

	/**
	 * Returns the error code.
	 * @return the error code.
	 */
	public String getErrorCode();

}
