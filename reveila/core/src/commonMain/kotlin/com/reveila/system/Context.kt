package com.reveila.system

expect interface Context {
    @Throws(com.reveila.error.SecurityException::class, IllegalArgumentException::class)
    fun getProxy(name: String): Proxy
}
