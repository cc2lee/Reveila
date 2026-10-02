package com.reveila.system

import kotlin.jvm.JvmField

abstract class PluginComponent : AbstractComponent() {

    @JvmField
    protected var context: Context? = null

    open fun getContext(): Context? = context

    open fun setContext(context: Context?) {
        this.context = context
    }
}
