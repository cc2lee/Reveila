package com.reveila.system

import javax.security.auth.Subject

open class PluginProxy(
    private val systemProxy: SystemProxy,
    private val subject: Subject
) : Proxy {

    init {
        requireNotNull(systemProxy) { "Argument 'systemProxy' cannot be null." }
        requireNotNull(subject) { "Argument 'subject' cannot be null." }
    }

    override fun getClassLoader(): ClassLoader? {
        return systemProxy.getClassLoader()
    }

    override fun getName(): String {
        return systemProxy.getName()
    }

    override fun getRequiredRoles(): List<String> {
        return systemProxy.getRequiredRoles()
    }

    @Throws(Exception::class)
    override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
        return systemProxy.invoke(methodName, args, this.subject)
    }
}
