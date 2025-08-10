package reveila.util.task;

import java.util.logging.Logger;

import reveila.system.SystemContext;

/**
 * @author Charles Lee
 */
public abstract class Job implements Runnable {

	protected volatile JobException exception;
	protected volatile JobStatus status = JobStatus.PENDING;
	protected volatile double percentageCompleted = -1.0;
	protected volatile String message;
	
	protected SystemContext systemContext;
	protected Logger logger;
	
	protected Job() {}

	/**
	 * Sets the system context for the job. This is package-private to restrict its use to the framework.
	 */
	void setSystemContext(SystemContext systemContext) {
		logger = systemContext.getLogger(this);
		this.systemContext = systemContext;
	}

	public JobException getException() {
		return exception;
	}
	
	public synchronized void setException(JobException exception) {
		this.exception = exception;
	}

	public double getPercentageCompleted() {
		return percentageCompleted;
	}

	public synchronized void setPercentageCompleted(double percentageCompleted) {
		this.percentageCompleted = percentageCompleted;
	}

	public JobStatus getStatus() {
		return status;
	}

	public synchronized void setStatus(JobStatus status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public synchronized void setMessage(String message) {
		this.message = message;
	}

	public abstract void run();
}
