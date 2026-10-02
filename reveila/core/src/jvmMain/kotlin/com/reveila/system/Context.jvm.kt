package com.reveila.system

import java.util.Properties
import java.util.logging.Logger

actual interface Context {
    fun getLogger(): Logger?
    @Throws(com.reveila.error.SecurityException::class, IllegalArgumentException::class)
    actual fun getProxy(name: String): Proxy
    val properties: Properties?
    val platformAdapter: PlatformAdapter?
}
