# 🏗️ Architectural Integrity Audit

This document serves as a permanent record of the sovereign architectural principles enforced within the Reveila Suite. It must be used as a reference for all future development to prevent architectural drift.

## 1. Platform Independence
*   **Spring Decoupling**: The [`ApiController.java`](spring/core/src/main/java/com/reveila/spring/system/ApiController.java) is strictly a transport layer. It depends solely on the `Reveila` engine instance and delegates all business, AI, and orchestration logic to the sovereign `reveila.invoke()` method.
*   **Environment Agnosticism**: Core logic is encapsulated within [`AbstractService`](reveila/src/main/java/com/reveila/system/AbstractService.java) implementations. This allows the entire agentic fabric to be ported across Spring, Android, or Standalone environments without modifying core execution paths.

## 2. Unified Invocations & Cluster Readiness
*   **Dynamic Routing**: The `Reveila.invoke()` implementation provides a unified interface that transparently handles local execution versus remote routing to high-performance nodes. This supports multi-tenant and distributed enterprise workflows (e.g., M&A).
*   **Standardized API Pattern**: All external interactions follow the `/components/{componentName}/invoke` pattern. This minimizes the security audit surface and ensures consistent behavioral guardrails across all components.

## 3. Sovereign Context & Guardrails
*   **Isolated Lifecycle**: The `SystemContext` operates independently of the host container (e.g., Spring's ApplicationContext). This ensures that the **Dual-Model Audit** and **Docker Sandbox Isolation** are enforced at the engine level.
*   **Oversight Integrity**: The **Dual-Key Overwatch Gate** ensures that high-privilege operations (like the Kill Switch) are physically and logically isolated from standard execution.

## 4. Forensic Traceability
*   **Immutable Audit Trail**: Every oversight action is enriched with the `oversight_token_id` in the **Flight Recorder**, establishing a definitive "Who watches the watchers?" record.

---
*Last Updated: 2026-02-18*
