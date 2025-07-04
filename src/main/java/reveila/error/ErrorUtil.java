package reveila.error;

public class ErrorUtil {
	
	/**
	 * Convert the <code>Throwable</code> object to String representation.
	 * This method traverses any nested <code>Throwable</code> objects and
	 * append them to the returned String.
	 * @param thrown - the <code>Throwable</code> object.
	 * @return a String representation of the <code>Throwable</code> object.
	 */
	public static String toString(Throwable thrown) {
		if (thrown == null) {
			return "";
		}
		
		Throwable t = thrown;
		StringBuffer strBuf = new StringBuffer(t.getClass().getName());
		strBuf.append(": ");
		if (t instanceof ErrorCode) {
			String errCode = ((ErrorCode)t).getErrorCode();
			if (errCode != null && errCode.length() > 0) {
				strBuf.append("[").append(errCode).append("] ");
			}
			
		}
		
		String msg = t.getLocalizedMessage();
		if (msg != null && msg.length() > 0) {
			strBuf.append(msg);
		} else {
			strBuf.append("(no detail message)");
		}
		
		while ((t = t.getCause()) != null) {
			strBuf.append(" > Caused by: ")
				.append(t.getClass().getName()).append(" - ")
				.append(t.getLocalizedMessage());
		}
		
		return strBuf.toString();
	}
	
	/**
	 * Return the root cause of this <code>Throwable</code> object.
	 * If there is no root cause, this method returns the <code>Throwable</code> object
	 * itself.
	 * 
	 * @param thrown the <code>Throwable</code> object.
	 * @return the root cause, or itself if no root cause found.
	 */
	public static Throwable getRootCause(Throwable thrown) {
		if (thrown == null) {
			throw new IllegalArgumentException("null argument: " + Throwable.class.getName());
		}
		
		Throwable t = thrown;
		while ((thrown.getCause() != null)) {
			t = thrown.getCause();
		}
		
		return t;
	}
}
