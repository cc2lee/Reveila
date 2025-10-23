package reveila.system;

import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class NodePerformanceTracker {

    public static final long DEFAULT_PENALTY_MS = 5000;
	
    private int capacity = 1000;
    private NavigableMap<Number, URL> tracker = Collections.synchronizedNavigableMap(new TreeMap<Number, URL>());
    private static NodePerformanceTracker sharedInstance = new NodePerformanceTracker(1000);

    public static NodePerformanceTracker getInstance() {
        return sharedInstance;
    }

    public synchronized void track(Number timeUsed, URL url) {
        if (url == null || timeUsed == null) {
            return; // Ignore
        }

        tracker.put(timeUsed, url);
        if (tracker.size() > capacity) {
            tracker.pollLastEntry();
        }
    }

    public synchronized int size() {
        return tracker.size();
    }

    public synchronized URL getBestNodeUrl() {
        Map.Entry<Number, URL> entry = tracker.firstEntry();
        if (entry != null) {
            return entry.getValue();
        }
        return null; // No valid node found
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = capacity;
        if (tracker.size() > capacity) {
            while (tracker.size() > capacity) {
                tracker.pollLastEntry();
            }
        }
    }

    public NodePerformanceTracker() {
        super();
    }

    public NodePerformanceTracker(int capacity) {
        super();
        this.capacity = capacity;
    }

    public synchronized void clear() {
        tracker.clear();
    }
}
