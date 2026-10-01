package com.reveila.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ScoreTracker {

    private String name;
    private TreeMap<Long, String> treeMap = new TreeMap<>();
    private NavigableMap<Long, String> tracker = Collections.synchronizedNavigableMap(treeMap);
    private Map<String, Long> reverseLookup = Collections.synchronizedMap(new HashMap<String, Long>());

    public ScoreTracker(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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

        Runnable runnable = () -> {
            synchronized (ScoreTracker.this) {
                Long currentPoints = reverseLookup.get(name);
                if (currentPoints == null) {
                    currentPoints = 0L;
                }
                Long newPoints = currentPoints + points;
                tracker.put(newPoints, name);
                reverseLookup.put(name, newPoints);
            }
        };

        new Thread(runnable).start();
    }
}
