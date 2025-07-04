package reveila.util.task;

import reveila.util.ExceptionList;

/**
 * @author Charles Lee
 *
 * The root Class for all Job Exceptions.
 */
 
public class JobException extends ExceptionList {

	/**
	 * Constructor.
	 */
	public JobException() {
		super();
	}

	/**
	 * Constructor.
	 * @param message - detailed message.
	 */
	public JobException(String message) {
		super(message);
	}

	/**
	 * Constructor.
	 * @param throwable - cause of the exception.
	 */
	public JobException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * Constructor.
	 * @param message - detailed message.
	 * @param throwable - cause of the exception.
	 */
	public JobException(String message, Throwable throwable) {
		super(message, throwable);
	}

}
