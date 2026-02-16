# ADR 0006: Reveila Booting and Proxy Architecture

## Status
Accepted

## Context
Reveila requires a platform-agnostic, secure, and predictable execution environment for both core services and third-party plugins. The architecture must support strict isolation (for high security requirements in regulated environment, e.g. healthcare compliance) and a centralized "kill switch" capability.

## 🏛️ The Execution Flow (Architectural Guardrails)

1.  **Discovery:** During boot, Reveila parses component JSON files. If a component entry is found, a `MetaObject` is created.
2.  **Proxy Wrap:** Reveila creates a `Proxy` for every `MetaObject`.
3.  **Lifecycle Hook:** When Reveila starts, it iterates through Proxies. If the configuration includes a `plugin` section:
    *   `Proxy.onStart()` triggers `loadPlugin(Path)`.
    *   A **Child-First Class Loader** isolates those specific libraries (crucial for handling conflicting dependencies).
4.  **Service State:** Classes must extend `AbstractService` to inherit standardized `start()`/`stop()` hooks, making the entire fabric predictable for the **CISO Kill Switch**.

## 1. Bootstrapping & Infrastructure

*   **Configuration First:** The `Reveila` class initializes by loading `reveila.properties` and receiving a `PlatformAdapter` (e.g., Spring or Android).
*   **Platform Abstraction:** All interactions with the underlying host (file system, network, or Spring beans) must occur through the PlatformAdapter interface.

## 2. Component & Plugin Lifecycle

*   **Metadata Discovery:** During boot, Reveila parses all component JSON files in `REVEILA_HOME` to generate `MetaObject` maps.
*   **Proxy-Based Invocations:** Every component must be wrapped in a `Proxy`. Proxies manage plugin loading and dedicated ClassLoaders.
*   **Registry Access:** All active proxies are stored in the `SystemContext`. Invocations happen via `Reveila.invoke()` or by fetching the proxy from the context.

## 3. Integration of the Docker Guarded Runtime

The Docker implementation is a **Specialized Platform Adapter** behavior:

*   **The Container is the Proxy:** For "Guarded Components," the `Proxy` triggers the `DockerGuardedRuntime` instead of local JVM execution.
*   **The Mini-Reveila Runtime:** The Docker image contains a "Reveila Worker Agent" (minimal Java app) that receives method calls via an entry point, executes in isolation, and returns results.

