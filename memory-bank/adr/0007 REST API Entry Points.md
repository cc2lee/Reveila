# ADR 0007: Unified REST API Entry Point & Payload Versioning

## Status
Accepted (Updated: 2026-11-14)

## Context
Reveila requires a unified, versioned, and secure REST entry mechanism to manage standard client invocations, distributed cluster routing (`RemoteService`), CISO Overwatch controls, and isolated Docker sandbox callbacks (`DockerGuardedRuntime` / `PluginRunner`). Previously, `ApiController` exposed multiple overlapping endpoints (`/api/components/{componentName}/invoke`, `/api/v1/overwatch/components/{componentName}/invoke`, and `/api/system/callback`), which duplicated invocation pipelines and lacked version compatibility checks between distributed nodes, clients, and container sandboxes.

## Decision
We consolidate all component, cluster, oversight, and sandbox callback REST invocations into a **single unified endpoint**:

### 1. The Unified Component Invocation Gate (`POST /api/components/{componentName}/invoke`)
*   **Mechanism:** Handled exclusively by `ApiController.invokeComponent(componentName, MethodDTO, HttpServletRequest)`.
*   **Payload Contract (`MethodDTO`):**
    ```json
    {
      "version": "1.0.0",
      "methodName": "targetMethod",
      "args": ["arg1", "arg2"]
    }
    ```
*   **Strict Version Enforcement (`409 Conflict`):** Every request MUST declare its `version` inside `MethodDTO`. The backend compares `request.getVersion()` against the currently deployed `reveila.getVersion()` (`system.version`). Missing or mismatched versions are rejected immediately with `409 Conflict` (`VERSION_CONFLICT`) before touching the execution bus.

### 2. Tiered Principal Elevation on the Unified Endpoint
Rather than proliferating separate URL paths, security tiers are enforced through header inspection and principal elevation on `Subject` combined with component/method `required-roles` RBAC:
*   **Standard Application & Peer Cluster Calls:** Authenticated via Spring Security session/JWT credentials (`ROLE_*` mapped to `RolePrincipal`).
*   **Overwatch Operations:** Intercepted by `OversightInterceptor` when `X-Reveila-Oversight-Token` is provided. Valid hardware-bound Dual-Key tokens attach `RolePrincipal("overwatch-operator")` and audit `oversight_token_id` in the `FlightRecorder`.
*   **Guarded Sandbox Callbacks (`DockerGuardedRuntime`):** Ephemeral containers receive `REVEILA_CALLBACK_URL`, `REVEILA_JIT_TOKEN`, and `REVEILA_VERSION` environment variables. Sandbox processes (`PluginRunner` / `ReveilaClient`) invoke `POST /api/components/ManagedInvocation/invoke` (`handleCallback` and `handleCallbackResult`) using `Authorization: Bearer <REVEILA_JIT_TOKEN>`, which is verified against `SecretManager` to grant `RolePrincipal("system")` and `RolePrincipal("plugin")`.

## Consequences
*   **Single Surface Area:** Every external client (`reveila-core.js`), cluster peer (`RemoteService`), and sandbox container (`PluginRunner`) uses `ReveilaClient` targeting `POST /api/components/{componentName}/invoke`.
*   **Deterministic Version Safety:** Rolling upgrades, incompatible client bundles, or stale container images fail fast with `409 Conflict` rather than causing subtle reflection or serialization failures.
*   **Forensic Integrity:** Overwatch actions and sandbox callbacks are recorded in `FlightRecorder` with full token validation and RBAC isolation.