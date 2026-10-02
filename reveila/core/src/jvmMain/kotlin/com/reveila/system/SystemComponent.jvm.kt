package com.reveila.system

import com.reveila.event.EventConsumer
import com.reveila.event.EventObject

actual abstract class SystemComponent actual constructor() : AbstractComponent(), EventConsumer {

    @JvmField
    protected var context: SystemContext? = null

    open fun getContext(): SystemContext? = context

    open fun setContext(context: SystemContext?) {
        this.context = context
    }

    @Throws(Exception::class)
    actual override fun notifyEvent(evtObj: EventObject) {
    }
}
