package com.reveila.system

import java.util.Properties
import java.util.logging.Logger
import javax.security.auth.Subject

class PluginContext(
    private val systemContext: SystemContext?,
    private val manifest: Manifest,
    properties: Properties?
) : Context {

    private val internalProperties = Properties()
    private val subject: Subject = Subject()

    init {
        this.subject.principals.add(RolePrincipal(manifest.componentType ?: "plugin"))
        if (properties != null) {
            this.internalProperties.putAll(properties)
        }
    }

    @Throws(com.reveila.error.SecurityException::class, IllegalArgumentException::class)
    override fun getProxy(name: String): Proxy {
        if (systemContext == null) {
            throw IllegalStateException("SystemContext is not available")
        }
        var proxy: Proxy = systemContext.getProxy(name, subject)
        if (proxy is SystemProxy) {
            proxy = PluginProxy(proxy, subject)
        }
        return proxy
    }

    override fun getLogger(): Logger? {
        return Logger.getLogger(manifest.name ?: "reveila.plugin")
    }

    override val properties: Properties
        get() = object : Properties() {
            override fun getProperty(key: String): String? {
                val dynamicValue = systemContext?.getProperty(key, manifest.name)
                if (dynamicValue != null) {
                    return dynamicValue
                }
                return this@PluginContext.internalProperties.getProperty(key)
            }
        }

    override val platformAdapter: PlatformAdapter?
        get() {
            val roles = manifest.roles
            if (roles.contains(Constants.SYSTEM)) {
                return systemContext?.platformAdapter
            }
            return null
        }
}
