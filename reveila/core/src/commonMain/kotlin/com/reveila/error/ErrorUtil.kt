package com.reveila.error

import kotlin.jvm.JvmStatic

/**
 * Multiplatform utility functions for error formatting and inspection.
 */
object ErrorUtil {

    /**
     * Converts a Throwable object to its formatted string representation,
     * including error codes and causal chains.
     */
    @JvmStatic
    fun toString(thrown: Throwable?): String {
        if (thrown == null) {
            return ""
        }

        var t: Throwable? = thrown
        val className = thrown::class.qualifiedName ?: thrown::class.simpleName ?: "Throwable"
        val strBuf = StringBuilder(className).append(": ")
        if (thrown is ErrorCode) {
            val code = thrown.errorCode
            if (!code.isNullOrBlank()) {
                strBuf.append("[").append(code).append("] ")
            }
        }

        val msg = thrown.message
        if (!msg.isNullOrBlank()) {
            strBuf.append(msg)
        } else {
            strBuf.append("(no detail message)")
        }

        while (t?.cause != null) {
            t = t.cause
            val causeObj = t ?: break
            val causeClassName = causeObj::class.qualifiedName ?: causeObj::class.simpleName ?: "Throwable"
            strBuf.append(" > Caused by: ")
                .append(causeClassName).append(" - ")
                .append(causeObj.message ?: "(no detail message)")
        }

        return strBuf.toString()
    }

    /**
     * Traverses the cause chain to return the root cause of the given Throwable.
     */
    @JvmStatic
    fun getRootCause(thrown: Throwable?): Throwable {
        if (thrown == null) {
            throw IllegalArgumentException("null argument: Throwable")
        }

        var current: Throwable = thrown
        while (current.cause != null) {
            current = current.cause!!
        }

        return current
    }
}
