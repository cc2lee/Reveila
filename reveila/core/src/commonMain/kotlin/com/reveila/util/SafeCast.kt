package com.reveila.util

/**
 * Utility for safe casting of objects.
 */
object SafeCast {
    /**
     * Safely casts an object to the specified type or returns null.
     */
    inline fun <reified T> cast(obj: Any?): T? = obj as? T
}
