package com.reveila.event;

import java.util.EventObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * @author Charles Lee
 *         * This class implements an abstract event manager, which is designed
 *         to centralize
 *         event management. All inter-service events are managed by this event
 *         manager.
 */
public class EventManager {

	private static final Logger logger = Logger.getLogger(EventManager.class.getName());
	private final List<EventConsumer> listeners = new CopyOnWriteArrayList<>();
	private final Executor executor = Executors.newFixedThreadPool(10);

	public EventManager() {
		// No initialization needed for now, but constructor is defined for potential
		// future use
		// and to allow for dependency injection if needed later on.
		// Using CopyOnWriteArrayList allows us to avoid synchronization issues with
		// concurrent modifications.
		// The executor is initialized with a fixed thread pool to handle event
		// dispatching without blocking the main thread.
		// This design ensures that event consumers can be added or removed safely while
		// events are being dispatched.
	}

	public void addEventWatcher(EventConsumer l) {
		if (l == null) {
			throw new IllegalArgumentException("Argument 'EventConsumer' must not be null");
		}
		
		if (!this.listeners.contains(l)) {
			this.listeners.add(l);
		}
	}

	public void removeEventConsumer(EventConsumer c) {
		if (c == null) {
			return;
		}
		this.listeners.remove(c);
	}

	public void dispatchEvent(EventObject event) {
		if (event == null)
			return;

		executor.execute(() -> {
			// Safe, non-blocking read traversal over CopyOnWriteArrayList thread snapshots
			for (EventConsumer listener : listeners) {
				try {
					listener.notifyEvent(event);
				} catch (Exception e) {
					logger.severe(e.toString() + " at " + e.getStackTrace()[0]);
				}
			}
		});
	}

	public void clear() {
		listeners.clear();
	}
}