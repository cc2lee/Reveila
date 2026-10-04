package com.reveila.system

import com.reveila.event.EventConsumer

/**
 * Multiplatform contract for the Reveila engine instance.
 */
interface ReveilaEngine : EventConsumer {
    fun isRunning(): Boolean
    fun shutdown()
    @Throws(Exception::class)
    fun invoke(
        componentName: String,
        methodName: String,
        params: Array<Any?>? = null,
        callerIp: String? = null,
        subject: Subject
    ): Any?
}
