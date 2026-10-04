package com.reveila.system

import com.reveila.crypto.Cryptographer
import com.reveila.error.ConfigurationException
import com.reveila.error.SecurityException
import com.reveila.error.SystemException
import com.reveila.event.EventManager
import com.reveila.event.EventObject
import com.reveila.system.logging.PlatformLogger

/**
 * Multiplatform system container.
 * Controls the life cycle of all system level objects.
 */
class SystemContext(
    properties: Properties,
    private val eventManager: EventManager,
    private val logger: PlatformLogger,
    override var cryptographer: Cryptographer?,
    override val platformAdapter: PlatformAdapter
) : Context {

    override val properties: Properties = Properties(properties)
    private val proxiesByName: MutableMap<String, Proxy> = mutableMapOf()
    private val subject: Subject = Subject().apply {
        principals.add(RolePrincipal(Constants.SYSTEM))
    }

    fun getProperty(propertyName: String?, pluginName: String?): String? {
        require(!propertyName.isNullOrBlank()) { "Property name cannot be null or blank." }

        if (pluginName == null) {
            return properties.getProperty(propertyName)
        }

        val prefix1 = "plugin.$pluginName."
        val prefix2 = "$pluginName."

        if (propertyName.startsWith(prefix1) || propertyName.startsWith(prefix2)) {
            return properties.getProperty(propertyName)
        }

        val prefixedGlobal1 = properties.getProperty(prefix1 + propertyName)
        if (prefixedGlobal1 != null) return prefixedGlobal1

        val prefixedGlobal2 = properties.getProperty(prefix2 + propertyName)
        if (prefixedGlobal2 != null) return prefixedGlobal2

        if ("system.home" == propertyName || "system.mode" == propertyName) {
            return properties.getProperty(propertyName)
        }

        if ("api.key" == propertyName || "endpoint" == propertyName || "provider" == propertyName) {
            return properties.getProperty(propertyName)
        }

        logger.warning(
            "Security Policy Violation: Plugin '$pluginName' attempted to read unauthorized global property: $propertyName"
        )
        return null
    }

    fun getEventManager(): EventManager = eventManager

    override fun getLogger(): PlatformLogger = logger

    @Throws(SystemException::class, ConfigurationException::class)
    fun add(proxy: Proxy) {
        val name = proxy.getName()
        if (name.isBlank()) {
            throw ConfigurationException("Component name cannot be null or blank.")
        }

        if (proxiesByName.containsKey(name)) {
            throw ConfigurationException("Component name '$name' is already in use.")
        }

        proxiesByName[name] = proxy
        if (proxy is com.reveila.event.EventConsumer) {
            eventManager.addEventWatcher(proxy)
        }
        if (proxy is SystemComponent) {
            proxy.context = this
        }
    }

    fun remove(proxy: Proxy?) {
        if (proxy == null) return
        proxiesByName.remove(proxy.getName())
        if (proxy is com.reveila.event.EventConsumer) {
            eventManager.removeEventConsumer(proxy)
        }
        if (proxy is SystemComponent) {
            proxy.context = null
        }
    }

    fun clear() {
        proxiesByName.clear()
        eventManager.clear()
        properties.clear()
        cryptographer = null
    }

    @Throws(SecurityException::class, IllegalArgumentException::class)
    override fun getProxy(name: String): Proxy {
        return getProxy(name, this.subject)
    }

    @Throws(SecurityException::class, IllegalArgumentException::class)
    fun getProxy(name: String, subject: Subject): Proxy {
        val roles = subject.getPrincipals(RolePrincipal::class)
        require(roles.isNotEmpty()) { "Subject must have at least one role." }

        val proxy = this.proxiesByName[name]
            ?: throw IllegalArgumentException("Component '$name' does not exist.")

        val requiredRoles = proxy.getRequiredRoles()
        if (requiredRoles.isEmpty() || requiredRoles.contains("*")) {
            return proxy
        } else {
            for (role in roles) {
                if (requiredRoles.contains(role.name)) {
                    return proxy
                }
            }
            throw SecurityException(
                "Access to component '$name' is denied. Subject must have one of the following roles: $requiredRoles"
            )
        }
    }

    fun notifyEvent(evtObj: EventObject) {
        for (proxy in this.proxiesByName.values) {
            try {
                if (proxy is com.reveila.event.EventConsumer) {
                    proxy.notifyEvent(evtObj)
                }
            } catch (t: Throwable) {
                logger.severe(t.toString() + t.stackTraceToString())
            }
        }
    }
}
