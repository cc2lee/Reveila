# Perimeter Enforcement: Metadata-Driven Security

This document outlines the enforcement strategies used to prevent **Shadow AI** and ensure secure agentic execution through metadata-driven perimeters.

## 1. Three-Tiered Capability Scoping
Instead of "all or nothing" access, plugins are categorized into risk tiers to minimize the attack surface:

*   **Tier 1 (Restricted):** No network access, read-only local compute (e.g., a math calculator).
*   **Tier 2 (Internal):** Access to specific internal APIs/databases with **Least Privilege** credentials.
*   **Tier 3 (External):** Authorized to call external SaaS endpoints (e.g., Martech platforms).

## 2. Just-In-Time (JIT) Credential Injection
Rather than storing long-lived secrets in the plugin, the **Universal Invocation Bridge** injects short-lived OAuth tokens into the guarded runtime *only* when an agent triggers a tool call.

> **Safety Benefit:** If a plugin is compromised, it holds no persistent credentials, significantly reducing the impact of a breach.

## 3. Intent-Based Policy Matching
Before the bridge executes a plugin, it uses a **Policy Matrix** to compare the agent's intent with the perimeter metadata:

*   **Scenario:** An agent tries to use a "Reporting Plugin" to delete a database table.
*   **Enforcement:** The Bridge identifies that `db.delete` is not within the authorized `access_scopes` and rejects the call immediately, logging a "Perimeter Violation".

## 4. The "Flight Recorder" (Auditability)
Every movement across the perimeter is recorded. This log includes the agent's **Reasoning Trace**—the "why" behind its tool call—providing full transparency for:
- M&A due diligence
- Compliance audits
- Debugging autonomous decision-making

## Template
```
{
  "plugin_id": "reveila.analytics.sql_runner",
  "version": "1.2.0",
  "capabilities": ["data_query", "report_generation"],
  "agency_perimeter": {
    "tier": "Tier 2 (Sensitive)",
    "access_scopes": ["db.read", "fs.read:/reports/tmp"],
    "network_egress": {
      "allowed_domains": ["api.internal-analytics.com"],
      "protocols": ["https"]
    },
    "human_in_the_loop": {
      "trigger_on": ["db.delete", "db.drop"],
      "escalation_policy": "admin_approval_required"
    },
    "resource_limits": {
      "max_memory_mb": 512,
      "max_execution_sec": 30
    }
  }
}
```