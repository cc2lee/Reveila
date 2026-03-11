package com.reveila.ai;

import java.util.Map;

/**
 * PerformanceReport aggregates startup metrics for core agentic components.
 * Used for investor presentations and operational monitoring.
 */
public record PerformanceReport(
    long bridgeStartupMs,
    long geminiProviderStartupMs,
    long claimsAuditorStartupMs,
    Map<String, Long> details
) {}
