# ADR 0007: REST API Entry Points

## Status
Accepted

## Context
Reveila requires a unified, secure, and tiered entry mechanism to manage agentic execution while protecting high-privilege oversight functions. The architecture must strictly isolate standard operations from security-critical management.

## Decision
We define two, and only two, distinct "entry gates" into the Reveila execution fabric, serving two very different levels of privilege.

### 1. The "Standard" Gate (`/api/components/...`)
*   **Purpose:** Standard application interactions (e.g., submitting a healthcare claim for audit, searching data).
*   **Security:** Standard user/tenant authentication.
*   **Mechanism:** Handled by the unified `ApiController.invokeComponent`.
*   **Restriction:** These endpoints can trigger authorized tools, but they are physically and logically blocked from accessing the **Flight Recorder** internal monologue or the **CISO Kill Switch**.

### 2. The "Overwatch" Gate (`/api/v1/overwatch/...`)
*   **Purpose:** High-privilege CISO oversight, performance monitoring, and emergency control.
*   **Security:** This path is explicitly intercepted by the `OversightInterceptor`. It requires the **Dual-Key** hardware-bound Oversight Token.
*   **Mechanism:** Handled by `ApiController.invokeOversightComponent`.
*   **Capability:** Only through this mapping can a user view the agent’s internal reasoning rationale, access startup latency metrics, or trigger a `SYSTEM_LOCKDOWN`.

## Consequences
*   **Sovereign Isolation:** Agentic oversight is logically isolated from agentic execution. A compromise of a standard user session provides zero access to Overwatch endpoints.
*   **Forensic Integrity:** Overwatch actions are enriched with the `oversight_token_id` in the Flight Recorder, creating a "Who watches the watchers?" audit trail.
*   **Predictable Interface:** All external-facing logic is unified under the `components/{componentName}/invoke` pattern, significantly reducing the surface area for security audits.