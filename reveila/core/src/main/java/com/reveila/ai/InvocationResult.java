package com.reveila.ai;

import java.util.Map;

/**
 * Encapsulates the result of a plugin invocation.
 * Supports handling of Human-in-the-Loop (HITL) scenarios.
 */
public record InvocationResult(
    Status status,
    Object data,
    String message,
    String callbackUrl
) {
    public enum Status {
        SUCCESS,
        ERROR,
        PENDING_APPROVAL,
        SECURITY_BREACH
    }

    public static InvocationResult success(Object data) {
        return new InvocationResult(Status.SUCCESS, data, null, null);
    }

    public static InvocationResult error(String message) {
        return new InvocationResult(Status.ERROR, null, message, null);
    }

    public static InvocationResult pendingApproval(String intent, String traceId) {
        return pendingApproval(intent, traceId, null);
    }

    public static InvocationResult pendingApproval(String intent, String traceId, Object approvalData) {
        String callbackUrl = "https://reveila.io/approve/" + traceId;
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("intent", intent);
        data.put("trace_id", traceId);
        if (approvalData != null) {
            data.put("approval_data", approvalData);
        }
        return new InvocationResult(
            Status.PENDING_APPROVAL, 
            data, 
            "Action '" + intent + "' requires human approval.", 
            callbackUrl
        );
    }

    public static InvocationResult securityBreach(String message) {
        return new InvocationResult(Status.SECURITY_BREACH, null, message, null);
    }
}
