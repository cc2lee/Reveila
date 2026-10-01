package com.reveila.system;

import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class PerformanceTracker {

    public static final long DEFAULT_PENALTY_MS = 5000;
	
    private int capacity = 1000;
    private NavigableMap<Number, URL> tracker = null;
    private static PerformanceTracker sharedInstance = null;

    public static PerformanceTracker getInstance() {
        if (sharedInstance == null) {
            sharedInstance = new PerformanceTracker();
        }
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

    public PerformanceTracker() {
        super();
        this.tracker = Collections.synchronizedNavigableMap(new TreeMap<Number, URL>());
    }

    public synchronized void clear() {
        tracker.clear();
    }
}
