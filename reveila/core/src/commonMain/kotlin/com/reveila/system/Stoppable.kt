package com.reveila.system

/**
 * Defines a contract for components that have a shutdown lifecycle method.
 */
interface Stoppable {
    @Throws(Exception::class)
    fun stop()
}
