package com.reveila.event

/**
 * Event emitted when an automated component or proxy executes an invocation cycle.
 */
open class AutoCallEvent(
    source: Any,
    val proxyName: String? = null,
    val methodName: String? = null,
    val eventType: Int = -1,
    val timeStamp: Long = -1L,
    val error: Throwable? = null
) : EventObject(source) {

    companion object {
        const val STARTED: Int = 1
        const val UPDATE: Int = 2
        const val COMPLETED: Int = 3
        const val FAILED: Int = 4
    }
}
