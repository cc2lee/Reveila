package com.reveila.safety

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Encapsulates the result of a plugin invocation.
 * Supports handling of Human-in-the-Loop (HITL) scenarios.
 */
data class InvocationResult(
    val status: Status,
    val data: Any? = null,
    val message: String? = null,
    val callbackUrl: String? = null
) {
    enum class Status {
        SUCCESS,
        ERROR,
        PENDING_APPROVAL,
        SECURITY_BREACH
    }

    fun status(): Status = status
    fun data(): Any? = data
    fun message(): String? = message
    fun callbackUrl(): String? = callbackUrl

    companion object {
        @JvmStatic
        fun success(data: Any?): InvocationResult {
            return InvocationResult(Status.SUCCESS, data, null, null)
        }

        @JvmStatic
        fun success(data: Any?, message: String?): InvocationResult {
            return InvocationResult(Status.SUCCESS, data, message, null)
        }

        @JvmStatic
        fun error(message: String?): InvocationResult {
            return InvocationResult(Status.ERROR, null, message, null)
        }

        @JvmStatic
        @JvmOverloads
        fun pendingApproval(intent: String, traceId: String, approvalData: Any? = null): InvocationResult {
            val callbackUrl = "https://reveila.io/approve/$traceId"
            val data = mutableMapOf<String, Any?>()
            data["intent"] = intent
            data["trace_id"] = traceId
            if (approvalData != null) {
                data["approval_data"] = approvalData
            }
            return InvocationResult(
                status = Status.PENDING_APPROVAL,
                data = data,
                message = "Action '$intent' requires human approval.",
                callbackUrl = callbackUrl
            )
        }

        @JvmStatic
        fun securityBreach(message: String?): InvocationResult {
            return InvocationResult(Status.SECURITY_BREACH, null, message, null)
        }
    }
}
