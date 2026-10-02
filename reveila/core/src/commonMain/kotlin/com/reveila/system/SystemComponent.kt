package com.reveila.system

import com.reveila.event.EventConsumer
import com.reveila.event.EventObject

expect abstract class SystemComponent() : AbstractComponent, EventConsumer {
    @Throws(Exception::class)
    override fun notifyEvent(evtObj: EventObject)
}
