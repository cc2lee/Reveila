# [ADR-0001]: Foundational Design and Architecture Principals

* **Status:** Accepted
* **Deciders:** Charles Lee
* **Date:** 2026-01-17
* **Tags:** #architecture #proxy #invocation #security #fabric

The Reveila-Suite is a strictly proxied system, where all interactions among its running services (components and plugins) are achieved through carefully designed proxies.

Proxies and Orchestration:

1. Proxy.invoke: The base mechanism for all cross-component communication. If called through a PluginProxy, it enforces role-based access control (RBAC). It's the "Secure Pipe" between components.
2. Reveila.invoke: The General Service Bus. It's the entry point for standard user or system requests. It handles load balancing (clustering), logging, and standard security before calling Proxy.invoke.
3. System Context: The orchestration layer for standard (traditional) system servicves. All proxies are registered with and accessed through the `SystemContext`, which implements the `Context` interface. Reveila Engine consists of 2 types of `services`, backed respectively by the system components (definitions in the configs/${platform.name} directory) and plugins (definitions in the configs/plugins directory). Different context is assigned to the 2 types of services - PluginContext is set on Plugin Proxy, where access privileges are restricted and executions are isolated. The origianl SystemContext is used for System Proxy (they are trusted system services), which has unrestricted access to system components.
4. InvocationBridge (UIB): The Agentic Control Plane. It is the dedicated gateway for AI-originated tool calls. Before an AI intent is allowed to reach the proxy system, the UIB performs:
    * Safety Audits (via Gemini) to prevent prompt injections.
    * Intent Validation against the Metadata Registry.
    * Perimeter Enforcement to restrict the agent's scope.
    * Secret Masking to protect PII/PHI.
5. AgenticFabric: The orchestration layer for AI era. It manages multi-agent workflows, sessions, and delegation (A2A communication) by calling into the UIB.