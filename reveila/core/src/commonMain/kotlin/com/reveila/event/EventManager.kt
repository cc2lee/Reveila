package com.reveila.event

import com.reveila.system.concurrency.PlatformScheduler
import com.reveila.system.logging.PlatformLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Centralized multiplatform event manager.
 * Uses atomic state flow for lock-free concurrent listener registration
 * and dispatches events asynchronously through the platform scheduler.
 */
open class EventManager {

    private val logger = PlatformLogger("com.reveila.event.EventManager")
    private val listenersState = MutableStateFlow<List<EventConsumer>>(emptyList())
    private val scheduler = PlatformScheduler(poolSize = 10)

    fun addEventWatcher(l: EventConsumer?) {
        requireNotNull(l) { "Argument 'EventConsumer' must not be null" }
        listenersState.update { current ->
            if (current.contains(l)) current else current + l
        }
    }

    fun removeEventConsumer(c: EventConsumer?) {
        if (c == null) return
        listenersState.update { current ->
            current - c
        }
    }

    fun dispatchEvent(event: EventObject?) {
        if (event == null) return

        val snapshot = listenersState.value
        scheduler.execute {
            for (listener in snapshot) {
                try {
                    listener.notifyEvent(event)
                } catch (e: Exception) {
                    logger.severe({ "Error in event consumer: ${e.message}" }, e)
                }
            }
        }
    }

    fun clear() {
        listenersState.value = emptyList()
    }
}
