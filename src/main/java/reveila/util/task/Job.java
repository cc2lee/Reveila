package reveila.util.task;

import java.io.File;
import java.util.logging.Logger;

import reveila.system.SystemContext;

/**
 * @author Charles Lee
 */
public abstract class Job implements Runnable {

	public static final int SUCCESSFUL				= 201;
	public static final int COMPLETED_WITH_ERRORS	= 202;
	public static final int FAILED					= 203;
	public static final int ABORTED					= 204;
	public static final int IN_PROGRESS				= 205;

	protected JobException exception;
	protected int status = -1;
	protected double percentageCompleted = -1.0;
	protected String message;
	
	protected SystemContext systemContext;
	protected long lastRun = -1L;
	protected long interval = -1L;
	protected Logger logger;
	protected String configFilePath;
	
	public Job(String configFilePath) {
		File file = new File(configFilePath);
		if (!file.exists()) {
			throw new IllegalArgumentException(
				"File does not exist: " + file.getAbsolutePath());
		} else if (!file.canWrite()) {
            throw new IllegalArgumentException(
				"File is not writable: " + file.getAbsolutePath());
        }
        this.configFilePath = configFilePath;
	}

	public long getLastRun() {
		return lastRun;
	}

	public void setLastRun(long lastRun) {
		this.lastRun = lastRun;
	}

	public void setSystemContext(SystemContext systemContext) {
		logger = systemContext.getLogger(this);
		this.systemContext = systemContext;
	}

	public JobException getException() {
		return exception;
	}
	
	public double getPercentageCompleted() {
		return percentageCompleted;
	}

	public void setPercentageCompleted(double percentageCompleted) {
		this.percentageCompleted = percentageCompleted;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void run() {}
}
