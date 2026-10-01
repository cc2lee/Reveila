package com.reveila.event;

import java.util.EventObject;

public class AutoCallEvent extends EventObject {

	public static final int STARTED = 1;
	public static final int UPDATE = 2;
	public static final int COMPLETED = 3;
	public static final int FAILED = 4;

	private String proxyName = null;
	private int eventType = -1;
	private long timeStamp = -1;
	private Throwable error = null;
	private String methodName = null;

	public AutoCallEvent(Object source, String targetProxyName, String targetMethodName, int eventType, long timeStamp,
			Throwable error) {
		super(source);
		this.proxyName = targetProxyName;
		this.methodName = targetMethodName;
		this.eventType = eventType;
		this.timeStamp = timeStamp;
		this.error = error;
	}

	public int getEventType() {
		return eventType;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

	public Throwable getError() {
		return error;
	}

	public String getProxyName() {
		return proxyName;
	}

	public String getMethodName() {
		return methodName;
	}

}
