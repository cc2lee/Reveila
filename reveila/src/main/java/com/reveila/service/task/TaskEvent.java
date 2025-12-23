package com.reveila.service.task;

import java.util.EventObject;

import com.reveila.error.ExceptionList;

 
public class TaskEvent extends EventObject {

	public static final int STARTED = 1;
	public static final int UPDATE = 2;
	public static final int COMPLETED = 3;
	public static final int FAILED = 4;

	private int eventType = 0;
	private long timeStamp = 0;
	private ExceptionList exceptions = null;


	public TaskEvent(Object source, int eventType, long timeStamp, ExceptionList exceptions) {
		super(source);
		this.eventType = eventType;
		this.timeStamp = timeStamp;
		this.exceptions = exceptions;
	}

	public int getEventType() {
		return eventType;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

	public ExceptionList getExceptions() {
		return exceptions;
	}	

}
