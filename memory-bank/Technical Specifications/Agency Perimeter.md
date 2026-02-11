# 🏛️ Technical Specification: Reveila Agency Perimeter

The **Agency Perimeter** serves as a policy-as-code layer, positioned between the AI Agent's intent and the execution of the invocation endpoints (e.g., `POST /api/components/{componentName}/invoke`).

## 1. Perimeter Metadata Schema (`agency-policy.json`)

Every third-party plugin must provide a manifest defining its "Agency Perimeter." This enables automated validation at the bridge level.

| Field | Type | Description |
| :--- | :--- | :--- |
| `scope_id` | String | Unique identifier for the permission set (e.g., `revenue.read`). |
| `access_level` | Enum | `READ_ONLY`, `READ_WRITE`, or `ADMIN`. |
| `egress_whitelist` | Array | List of authorized external domains/APIs the plugin can call. |
| `auth_injection` | Boolean | If true, the bridge injects a JIT OAuth token for the call. |
| `hitl_required` | Boolean | Triggers a Human-in-the-Loop pause for this specific method. |

## 2. Enforcement Logic (The Universal Invocation Bridge)

The Bridge performs a three-step validation before any code is executed:

1.  **Intent Matching:** Verifies that the `componentName` and method requested by the AI Agent exist in the plugin's metadata.
2.  **Constraint Check:** Checks the `agency-policy.json` to ensure the agent’s current identity (**Agent Principal**) is authorized to cross that specific perimeter.
3.  **Credential Wrapping:** If authorized, the Bridge wraps the call with a short-lived token, masking underlying enterprise credentials from the third-party plugin.

## 🛡️ Guarded Runtime Integration

To ensure the perimeter cannot be bypassed at the OS level, the runtime environment enforces metadata constraints:

*   **Network Isolation:** The runtime's virtual NIC strictly permits traffic *only* to domains listed in the `egress_whitelist`.
*   **Shadowing (DEX/JAR):** Third-party code is loaded using a custom `ClassLoader` (Shadowing) to prevent inspection or reflection into core Reveila classes or the host environment.
*   **Resource Throttling:** If a plugin exceeds its defined `max_memory_mb`, the runtime terminates the thread, preventing Denial of Service (DoS) attacks.
