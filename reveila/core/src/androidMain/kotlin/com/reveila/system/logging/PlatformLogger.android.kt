package com.reveila.system.logging

import android.util.Log

class AndroidPlatformLogger(private val name: String) : PlatformLogger {

    override fun info(message: () -> String) {
        Log.i(name, message())
    }

    override fun warning(message: () -> String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(name, message(), throwable)
        } else {
            Log.w(name, message())
        }
    }

    override fun severe(message: () -> String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(name, message(), throwable)
        } else {
            Log.e(name, message())
        }
    }

    override fun debug(message: () -> String) {
        Log.d(name, message())
    }
}

actual fun createPlatformLogger(name: String): PlatformLogger = AndroidPlatformLogger(name)
