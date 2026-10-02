package com.reveila.system

actual interface Proxy {
    actual fun getName(): String
    actual fun getRequiredRoles(): List<String>
    @Throws(Exception::class)
    actual fun invoke(methodName: String, args: Array<out Any?>?): Any?
}
