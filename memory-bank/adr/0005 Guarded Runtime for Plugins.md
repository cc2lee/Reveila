# ADR 0005: Guarded Runtime for Agentic Plugin Execution

## Status
Proposed

## Context
The Reveila framework is pivoting to support Agentic AI workflows where third-party plugins must be executed autonomously. To mitigate the "Confused Deputy" problem and prevent unauthorized system access, a secure isolation layer is required.

## 🏛️ The Decision
We will implement a **Two-Tiered Sandbox Architecture** for plugin execution. This leverages the existing Universal Invocation Model as the primary gateway for all model-to-code interactions.

### 1. The Gateway (Universal Invocation Bridge)
- **Intent Validation:** Every agent request is intercepted by the bridge to ensure the intent maps to a registered, metadata-defined plugin.
- **Schema Enforcement:** Model-generated arguments are strictly validated against JSON schemas before being passed to the plugin.

### 2. The Execution Layer (Guarded Runtime)
- **Micro-VM/Container Isolation:** Plugins will run in highly restricted, ephemeral environments (e.g., Firecracker micro-VMs or gVisor-sandboxed containers).
- **Network Egress Control:** By default, plugins will have zero network access unless a specific "Integration Perimeter" is explicitly declared in the plugin's metadata.
- **Resource Quotas:** Hard limits on CPU, memory, and disk I/O will be enforced to prevent a malicious or runaway plugin from impacting the core platform.

## 🛠️ Proposed Feature Set for the Pivot

| Component | Technical Implementation | Security Objective |
| :--- | :--- | :--- |
| **Plugin Shadowing** | Leveraging `Custom ClassLoader` strategies to isolate third-party JARs. | Prevent classpath conflicts and host-system pollution. |
| **Agent Principals** | Assigning non-person entity (NPE) identities to each agent session. | Ensure "Least Privilege" access to enterprise data sources. |
| **Flight Recorder** | Asynchronous logging of the agent's reasoning chain and tool outputs. | Provide forensic auditability for autonomous decisions. |
| **Agency Perimeters** | Metadata-defined boundaries for what a plugin is authorized to do. | Prevent a plugin from performing high-risk actions outside its scope. |
