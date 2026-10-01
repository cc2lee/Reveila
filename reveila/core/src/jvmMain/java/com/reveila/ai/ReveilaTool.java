package com.reveila.ai;

import java.util.Map;

public interface ReveilaTool {
    String getName();        // e.g., "sys_health"
    String getDescription(); // e.g., "Checks CPU and Thermal status"
    String execute(Map<String, Object> args); // The actual logic
}