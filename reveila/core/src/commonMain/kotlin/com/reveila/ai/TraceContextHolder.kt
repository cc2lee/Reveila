package com.reveila.ai

import kotlin.jvm.JvmStatic

/**
 * TraceContextHolder provides a mechanism for passing trace_id through nested
 * calls, ensuring that the Flight Recorder can maintain a tree-structure
 * in the audit logs.
 */
class TraceContextHolder private constructor() {

    companion object {
        @JvmStatic
        fun setTraceId(traceId: String?) {
            PlatformTraceContext.set(traceId)
        }

        @JvmStatic
        fun getTraceId(): String? = PlatformTraceContext.get()

        @JvmStatic
        fun clear() {
            PlatformTraceContext.clear()
        }
    }
}

expect object PlatformTraceContext {
    fun get(): String?
    fun set(value: String?)
    fun clear()
}
