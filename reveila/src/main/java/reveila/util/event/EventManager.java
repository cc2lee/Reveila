package reveila.util.event;

import java.util.EventObject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Charles Lee
 * 
 * This class implements an abstract event manager, which is designed to centralize
 * event management. All inter-service events are managed by this event manager.
 */
public class EventManager {

	private List<EventWatcher> listeners = new LinkedList<EventWatcher>();
	private ThreadGroup tGroup = new ThreadGroup("Event dispatching thread group");
	private Logger logger;
	
	public void setLogger(Logger logger) {
		synchronized (this) {
			this.logger = logger;
		}
	}

	public EventManager() {
		super();
	}
	
	public void addEventWatcher(EventWatcher l) {
		if (l == null) {
			throw new IllegalArgumentException("Argument 'EventReceiver' must not be null");
		}
		
		synchronized (this) {
			if (!this.listeners.contains(l)) {
				this.listeners.add(l);
			}
		}
	}

	public void removeEventWatcher(EventWatcher l) {
		if (l == null) {
			throw new IllegalArgumentException("Argument 'EventReceiver' must not be null");
		}
		
		synchronized (this) {
			if (this.listeners.contains(l)) {
				this.listeners.remove(l);
			}
		}
	}
	
	public void dispatchEvent(EventObject event) {
		Runnable r = new Runnable() {
			public void run() {
				Iterator<EventWatcher> i = listeners.iterator();
				while (i.hasNext()) {
					try {
						i.next().onEvent(event);
					} catch (Exception e) {
						if (logger != null) {
							logger.severe(e.toString() + e.getStackTrace());
						} else {
							e.printStackTrace();
						}
					}
				}
			}
		};
		
		Thread t = new Thread(tGroup, r);
		t.start();
	}

    public void clear() {
		synchronized (this) {
			listeners.clear();
		}
    }
}
