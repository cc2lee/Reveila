package reveila.util.task;

import reveila.system.AbstractService;

/**
 * @author Charles Lee
 */
public abstract class Job extends AbstractService implements Runnable {

	protected volatile JobException exception;
	protected volatile JobStatus status = JobStatus.PENDING;
	protected volatile double percentageCompleted = -1.0;
	protected volatile String message;
	
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
