package com.reveila.system

internal actual fun executeContainerPlugin(args: Array<String>) {
    throw UnsupportedOperationException("Containerized PluginRunner main entry point is only supported on JVM.")
}
