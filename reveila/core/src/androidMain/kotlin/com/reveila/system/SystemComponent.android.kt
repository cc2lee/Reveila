package com.reveila.system

import com.reveila.event.EventConsumer
import com.reveila.event.EventObject

actual abstract class SystemComponent actual constructor() : AbstractComponent(), EventConsumer {

    @JvmField
    protected var context: Context? = null

    open fun getContext(): Context? = context

    open fun setContext(context: Context?) {
        this.context = context
    }

    @Throws(Exception::class)
    actual override fun notifyEvent(evtObj: EventObject) {
    }
}
