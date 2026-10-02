package com.reveila.ai

/**
 * TraceContextHolder provides a mechanism for passing trace_id through nested
 * calls, ensuring that the Flight Recorder can maintain a tree-structure
 * in the audit logs.
 */
class TraceContextHolder private constructor() {

    companion object {
        private val CURRENT_TRACE_ID: ThreadLocal<String?> = ThreadLocal()

        @JvmStatic
        fun setTraceId(traceId: String?) {
            CURRENT_TRACE_ID.set(traceId)
        }

        @JvmStatic
        fun getTraceId(): String? = CURRENT_TRACE_ID.get()

        @JvmStatic
        fun clear() {
            CURRENT_TRACE_ID.remove()
        }
    }
}
