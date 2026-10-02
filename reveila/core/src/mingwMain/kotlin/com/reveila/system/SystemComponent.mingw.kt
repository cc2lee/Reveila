package com.reveila.system

import com.reveila.event.EventConsumer
import com.reveila.event.EventObject

actual abstract class SystemComponent actual constructor() : AbstractComponent(), EventConsumer {
    @Throws(Exception::class)
    actual override fun notifyEvent(evtObj: EventObject) {
    }
}
