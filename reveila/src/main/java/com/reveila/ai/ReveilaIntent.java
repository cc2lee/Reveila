package com.reveila.ai;

import java.util.Map;

/**
 * Represents a normalized intent within the Reveila ecosystem,
 * converted from external tool signals.
 * 
 * @author CL
 */
public record ReveilaIntent(String intent, Map<String, Object> arguments, String sourceTool) {
}
