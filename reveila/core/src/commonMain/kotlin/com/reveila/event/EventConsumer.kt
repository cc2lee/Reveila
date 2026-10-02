package com.reveila.event

/**
 * Functional interface for consumers receiving system and domain events.
 */
fun interface EventConsumer {
    @Throws(Exception::class)
    fun notifyEvent(evtObj: EventObject)
}
