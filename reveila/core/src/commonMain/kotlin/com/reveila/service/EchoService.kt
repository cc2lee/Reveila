package com.reveila.service

import com.reveila.event.EventObject
import com.reveila.system.SystemComponent

/**
 * A simple echo service that can reverse, repeat, and delay responses.
 * Its main purpose is to demonstrate service functionality and for testing.
 */
open class EchoService : SystemComponent() {

    var isReverse: Boolean = false
    var repeat: Int = 0

    open fun echo(name: String): String {
        var textToEcho = name
        if (this.isReverse) {
            textToEcho = name.reversed()
        }
        if (this.repeat > 0) {
            val repeated = StringBuilder()
            for (i in 0 until this.repeat) {
                repeated.append(textToEcho)
                if (i < this.repeat - 1) {
                    repeated.append(", ")
                }
            }
            textToEcho = repeated.toString()
        }
        return textToEcho
    }

    @Throws(Exception::class)
    override fun onStart() {
    }

    @Throws(Exception::class)
    override fun onStop() {
    }

    @Throws(Exception::class)
    override fun notifyEvent(evtObj: EventObject) {
    }
}
