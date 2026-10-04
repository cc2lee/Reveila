package com.reveila.system

import com.reveila.system.logging.PlatformLogger

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
        val proxy: Proxy = systemContext.getProxy(name, subject)
        return PluginProxy(proxy, subject)
    }

    override fun getLogger(): PlatformLogger? {
        return PlatformLogger("reveila.plugin.${manifest.name ?: "anonymous"}")
    }

    override val properties: Properties
        get() {
            val props = Properties()
            props.putAll(internalProperties)
            val sysProps = systemContext?.properties
            if (sysProps != null) {
                for (k in sysProps.stringPropertyNames()) {
                    val dynamicVal = systemContext.getProperty(k, manifest.name)
                    if (dynamicVal != null) {
                        props.setProperty(k, dynamicVal)
                    }
                }
            }
            return props
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
