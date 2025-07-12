package reveila.util.task;

import java.util.EventObject;

 
public class JobEvent extends EventObject {
	
	public static final int JOB_STARTED 	= 101;
	public static final int JOB_UPDATE		= 102;
	public static final int JOB_FINISHED	= 103;
	public static final int JOB_FAILED		= 104;
	
	private int eventType = -1;
	private long timeStamp = 0;

	public JobEvent(Object source, int eventType, long timeStamp) {
		super(source);
		this.eventType = eventType;
		this.timeStamp = timeStamp;
	}

	public int getEventType() {
		return eventType;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

}
