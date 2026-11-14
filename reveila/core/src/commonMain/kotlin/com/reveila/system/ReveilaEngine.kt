package com.reveila.system

import com.reveila.error.VersionConflictException
import com.reveila.event.EventConsumer

/**
 * Multiplatform contract for the Reveila engine instance.
 */
interface ReveilaEngine : EventConsumer {
    fun isRunning(): Boolean
    fun shutdown()

    fun getVersion(): String = Constants.DEFAULT_SYSTEM_VERSION

    fun isVersionCompatible(clientVersion: String?): Boolean {
        if (clientVersion.isNullOrBlank()) return false
        return normalizeVersion(getVersion()) == normalizeVersion(clientVersion)
    }

    @Throws(VersionConflictException::class)
    fun validateVersion(clientVersion: String?) {
        if (!isVersionCompatible(clientVersion)) {
            throw VersionConflictException(clientVersion, getVersion())
        }
    }

    @Throws(Exception::class)
    fun invoke(
        componentName: String,
        methodName: String,
        params: Array<Any?>? = null,
        callerIp: String? = null,
        subject: Subject? = null
    ): Any?

    companion object {
        @kotlin.jvm.JvmStatic
        fun normalizeVersion(version: String?): String {
            if (version.isNullOrBlank()) return ""
            return version.trim().removePrefix("v").removePrefix("V")
        }
    }
}
