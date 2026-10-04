package com.reveila.system

import com.reveila.crypto.Cryptographer
import com.reveila.system.logging.PlatformLogger

interface Context {
    fun getLogger(): PlatformLogger?
    @Throws(com.reveila.error.SecurityException::class, IllegalArgumentException::class)
    fun getProxy(name: String): Proxy
    val properties: Properties?
    val platformAdapter: PlatformAdapter?
    val cryptographer: Cryptographer? get() = null
}
