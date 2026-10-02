package com.reveila.system

actual interface Context {
    @Throws(com.reveila.error.SecurityException::class, IllegalArgumentException::class)
    actual fun getProxy(name: String): Proxy
}
