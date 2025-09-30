package reveila.system;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class Cluster {
    private static NavigableMap<Number, URL> tracker = Collections.synchronizedNavigableMap(new TreeMap<Number, URL>());
    private static int capacity = 1000;
    private static NavigableMap<Number, URL> configs = Collections.synchronizedNavigableMap(new TreeMap<Number, URL>());
    private static Timer timer = new Timer();

    public synchronized static void addNode(Number priority, URL url) {
        if (url == null || priority == null) {
            return; // Ignore
        }
        configs.put(priority, url);
        track(priority, url);
    }

    public synchronized static void removeNode(URL url) {
        if (url == null) {
            return; // Ignore
        }
        configs.values().remove(url);
    }

    public synchronized static void reset() {
        List<Map.Entry<Number, URL>> sortedEntries = new ArrayList<>(configs.entrySet());
        for (int i = 0; i < capacity && i < sortedEntries.size(); i++) {
            Map.Entry<Number, URL> entry = sortedEntries.get(i);
            track(entry.getKey(), entry.getValue());
        }
    }

    static {
        synchronized (Cluster.class) {
            // Maintenance task to run every 3 seconds after an initial 1-second delay
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    reset();
                }
            }, Constants.DELAY_TIME_UNIT, Constants.DELAY_TIME_UNIT * 12); // Initial delay: 5s, interval: 60s
        }
    }
    
    public synchronized static void track(Number timeUsed, URL url) {
        if (url == null || timeUsed == null) {
            return; // Ignore
        }

        new Thread(new Runnable() {
            @Override
            public synchronized void run() {
                tracker.put(timeUsed, url);
                if (tracker.size() > capacity) {
                    tracker.pollLastEntry();
                }
            }
        }).start();
    }

    public synchronized static int size() {
        return tracker.size();
    }

    public synchronized static URL getBestNodeUrl() {
        Map.Entry<Number, URL> entry = tracker.firstEntry();
        if (entry != null) {
            return entry.getValue();
        }
        return null; // No valid node found
    }

    public synchronized static int getCapacity() {
        return capacity;
    }

    public synchronized static void setCapacity(int capacity) {
        Cluster.capacity = capacity;
    }

    private Cluster() {
        // Private constructor to prevent instantiation
    }
}
