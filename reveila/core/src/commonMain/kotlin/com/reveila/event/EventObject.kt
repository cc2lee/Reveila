package com.reveila.event

/**
 * Multiplatform base event object representing an event state change.
 * Replaces java.util.EventObject for cross-platform sovereign execution.
 */
open class EventObject(
    open val source: Any
) {
    override fun toString(): String {
        return "${this::class.simpleName ?: "EventObject"}[source=$source]"
    }
}
