package com.reveila.system

expect interface Proxy {
    fun getName(): String
    fun getRequiredRoles(): List<String>
    @Throws(Exception::class)
    fun invoke(methodName: String, args: Array<out Any?>?): Any?
}
