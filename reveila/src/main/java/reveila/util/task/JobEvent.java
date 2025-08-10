package reveila.util.task;

import java.util.EventObject;

 
public class JobEvent extends EventObject {

	private final JobEventType eventType;
	private long timeStamp = 0;

	public JobEvent(Object source, JobEventType eventType, long timeStamp) {
		super(source);
		this.eventType = eventType;
		this.timeStamp = timeStamp;
	}

	public JobEventType getEventType() {
		return eventType;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

}
