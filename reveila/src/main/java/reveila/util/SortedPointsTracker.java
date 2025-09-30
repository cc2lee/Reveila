package reveila.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import com.fasterxml.jackson.core.type.TypeReference;

import reveila.system.Constants;
import reveila.util.io.IOHelper;
import reveila.util.json.JsonUtil;

public class SortedPointsTracker {

    private String name;
    public String getName() {
        return name;
    }

    private TreeMap<Long, String> treeMap = new TreeMap<>();
    private NavigableMap<Long, String> tracker = Collections.synchronizedNavigableMap(treeMap);
    private Map<String, Long> reverseLookup = Collections.synchronizedMap(new HashMap<String, Long>());
    private static Timer timer = new Timer();

    public SortedPointsTracker(String name) {
        this.name = name;
        timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public synchronized void run() {
                    // Maintenance task to run, e.g., decay points over time
                }
            }, Constants.DELAY_TIME_UNIT, Constants.DELAY_TIME_UNIT * 12); // Initial delay: 5s, interval: 60s
    }

    public synchronized String getBest() {
        Map.Entry<Long, String> entry = tracker.lastEntry();
        if (entry != null) {
            return entry.getValue();
        }
        return null;
    }

    public synchronized String getWorst() {
        Map.Entry<Long, String> entry = tracker.firstEntry();
        if (entry != null) {
            return entry.getValue();
        }
        return null;
    }

    public synchronized void applyPoints(Long points, String name) {
        if (name == null || points == null) {
            return; // Ignore
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (SortedPointsTracker.this) {
                    Long currentPoints = reverseLookup.get(name);
                    if (currentPoints == null) {
                        currentPoints = 0L;
                    }
                    long newPoints = currentPoints + points;
                    tracker.put(newPoints, name);
                    reverseLookup.put(name, newPoints);
                }
            }
        }).start();
    }

    public synchronized void write(OutputStream output) throws Exception {
        try {
            IOHelper.writeAsJsonArray(output, new Map[] { treeMap, reverseLookup });
        } catch (Exception e) {
            throw new Exception("Failed to write " + this.getClass().getSimpleName() + " to output stream: " + e.getMessage(), e);
        }
    }

    public synchronized void read(InputStream input) throws Exception {
        try {
            Map<?, ?>[] mapArray = IOHelper.readJsonArray(input);
            if (mapArray.length == 2) {
                treeMap = JsonUtil.MAPPER.convertValue(mapArray[0], new TypeReference<TreeMap<Long, String>>() {});
                tracker = Collections.synchronizedNavigableMap(treeMap);
                HashMap<String, Long> hashMap = JsonUtil.MAPPER.convertValue(mapArray[1], new TypeReference<HashMap<String, Long>>() {});
                reverseLookup = Collections.synchronizedMap(hashMap);
            } else {
                throw new IOException("Invalid data format: Expected 2 JSON objects in the array.");
            }
        
        } catch (Exception e) {
            throw new Exception("Failed to read " + this.getClass().getSimpleName() + " from input stream: " + e.getMessage(), e);
        }
    }
}
