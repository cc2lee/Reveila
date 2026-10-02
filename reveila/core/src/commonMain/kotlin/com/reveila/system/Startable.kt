package com.reveila.system

/**
 * Defines a contract for components that have a startup lifecycle method.
 */
interface Startable {
    @Throws(Exception::class)
    fun start()
}
