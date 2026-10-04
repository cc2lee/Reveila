package com.reveila.system

open class PluginProxy(
    private val targetProxy: Proxy,
    private val subject: Subject
) : Proxy {

    init {
        requireNotNull(targetProxy) { "Argument 'targetProxy' cannot be null." }
        requireNotNull(subject) { "Argument 'subject' cannot be null." }
    }

    override fun getName(): String {
        return targetProxy.getName()
    }

    override fun getRequiredRoles(): List<String> {
        return targetProxy.getRequiredRoles()
    }

    @Throws(Exception::class)
    override fun invoke(methodName: String, args: Array<out Any?>?): Any? {
        return targetProxy.invoke(methodName, args)
    }

    override fun getInstance(): Any? {
        return targetProxy.getInstance()
    }
}
