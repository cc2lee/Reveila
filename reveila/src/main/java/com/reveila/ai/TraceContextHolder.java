package com.reveila.ai;

/**
 * TraceContextHolder provides a mechanism for passing trace_id through nested
 * calls, ensuring that the Flight Recorder can maintain a tree-structure
 * in the audit logs.
 * 
 * @author CL
 */
public class TraceContextHolder {
    private static final ThreadLocal<String> CURRENT_TRACE_ID = new ThreadLocal<>();

    /**
     * Sets the current trace_id for the thread.
     * 
     * @param traceId The trace_id to set.
     */
    public static void setTraceId(String traceId) {
        CURRENT_TRACE_ID.set(traceId);
    }

    /**
     * Retrieves the current trace_id.
     * 
     * @return The current trace_id, or null if not set.
     */
    public static String getTraceId() {
        return CURRENT_TRACE_ID.get();
    }

    /**
     * Clears the current trace_id.
     */
    public static void clear() {
        CURRENT_TRACE_ID.remove();
    }
}
