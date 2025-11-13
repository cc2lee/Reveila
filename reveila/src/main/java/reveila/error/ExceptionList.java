package reveila.error;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Charles Lee
 *
 * This class probides a mechanism to bundle a list of exception objects,
 * which can be thrown as a single exception. The getCause method of this
 * class always returns null; use getExceptions to retrieve the list of
 * exceptions enclosed.
 */
 
public class ExceptionList extends Exception {

	private List<Throwable> exceptions = new LinkedList<Throwable>();
	
	/**
	 * Default constructor
	 */
	public ExceptionList() {
		super();
	}

	/**
	 * @param message
	 */
	public ExceptionList(String message) {
		super(message);
	}

	/**
	 * @param throwable
	 */
	public ExceptionList(Throwable throwable) {
		super();
		addException(throwable);
	}

	/**
	 * @param message
	 * @param throwable
	 */
	public ExceptionList(String message, Throwable throwable) {
		super(message);
		addException(throwable);
	}

	/**
	 * @param throwable
	 */
	public void addException(Throwable throwable) {
		exceptions.add(throwable);
	}

	/**
	 * @return
	 */
	public List<Throwable> getExceptions() {
		return exceptions;
	}

	/**
	 * @param list
	 */
	public void setExceptions(List<Throwable> list) {
		if (this.exceptions.size() > 0) {
			this.exceptions.clear();
		}
		
		if (list != null) {
			this.exceptions.addAll(list);
		}
	}

	@Override
	public String toString() {
		return ErrorUtil.toString(this);
	}

	@Override
	public String getMessage() {
		if (exceptions != null && exceptions.size() > 0) {
			StringBuffer strBuf = new StringBuffer();
			String endl = System.getProperty("line.separator", "\n");
			strBuf.append(endl).append("Exceptions:");
			synchronized (exceptions) {
				Iterator<Throwable> itr = exceptions.iterator();
				while (itr.hasNext()) {
					strBuf.append(endl);
					Throwable t = itr.next();
					String string = ErrorUtil.toString(t);
					strBuf.append(string);
				}
			}
			return strBuf.toString();
		} else {
			return null;
		}
	}

}
