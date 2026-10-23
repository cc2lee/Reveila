package com.reveila.system

import java.io.File

/**
 * Standard JVM PlatformAdapter for desktop, server, and test applications.
 * Automatically resolves system home and platform properties if not explicitly provided.
 */
open class DefaultPlatformAdapter @Throws(Exception::class) @JvmOverloads constructor(
    commandLineArgs: Properties? = null
) : BasePlatformAdapter(ensureDefaultProperties(commandLineArgs)) {

    companion object {
        private fun ensureDefaultProperties(args: Properties?): Properties {
            val props = Properties()
            args?.let { props.putAll(it) }

            // 1. Resolve SYSTEM_HOME if not already specified
            if (props.getProperty(Constants.SYSTEM_HOME).isNullOrBlank()) {
                val resolvedHome = resolveSystemHome()
                if (resolvedHome != null) {
                    props.setProperty(Constants.SYSTEM_HOME, resolvedHome)
                }
            }

            // 2. Resolve PLATFORM if not already specified
            if (props.getProperty(Constants.PLATFORM).isNullOrBlank()) {
                val sysPlatform = System.getProperty(Constants.PLATFORM)
                if (!sysPlatform.isNullOrBlank()) {
                    props.setProperty(Constants.PLATFORM, sysPlatform)
                }
            }

            return props
        }

        private fun resolveSystemHome(): String? {
            val sysProp = System.getProperty(Constants.SYSTEM_HOME)
                ?: System.getProperty("reveila.system.home")
            if (!sysProp.isNullOrBlank()) return sysProp

            val envHome = System.getenv("REVEILA_HOME")
            if (!envHome.isNullOrBlank()) return envHome

            val candidates = listOf(
                "system-home/standard",
                "../system-home/standard",
                "../../system-home/standard",
                "build/distributions/reveila-system-home"
            )
            for (candidate in candidates) {
                val f = File(candidate)
                if (f.exists() && f.isDirectory) {
                    return f.canonicalPath
                }
            }

            return null
        }
    }
}
