package com.reveila.tools

actual fun executePlatformProcess(command: List<String>, timeoutSeconds: Long): String {
    return "Process execution not available in native mode."
}
