package com.reveila.system

import com.reveila.event.EventConsumer
import com.reveila.event.EventObject

abstract class SystemComponent : AbstractComponent(), EventConsumer {
    @kotlin.jvm.JvmField
    var context: Context? = null

    fun getContext(): Context? = context
    fun setContext(ctx: Context?) {
        this.context = ctx
    }

    open fun getComponent(name: String): Any? {
        val ctx = context ?: return null
        return try {
            val proxy = ctx.getProxy(name)
            proxy.getInstance() ?: proxy
        } catch (e: Exception) {
            null
        }
    }

    @Throws(Exception::class)
    override fun notifyEvent(evtObj: EventObject) {
    }
}
