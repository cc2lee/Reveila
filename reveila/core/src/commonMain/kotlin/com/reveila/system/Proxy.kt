package com.reveila.system

/**
 * Multiplatform contract for secure proxy invocations.
 */
interface Proxy {
    fun getName(): String
    fun getRequiredRoles(): List<String>
    @Throws(Exception::class)
    fun invoke(methodName: String, args: Array<out Any?>? = null): Any?
    fun getInstance(): Any? = null
}
