package com.reveila.event;

import java.util.EventObject;

/**
 * @author Charles Lee
 * 
 * Defines a generic event listener interface. The interface is designed for
 * dispatching abstract events to anonymous event listeners. It is up to the
 * event listener to decide which event to consume.
 */
public interface EventConsumer {

	public void notifyEvent(EventObject evtObj) throws Exception;

}