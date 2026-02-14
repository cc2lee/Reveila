# 🏛️ Agentic AI Roadmap: The Agentic AI Execution Framework

The following roadmap defines our core strategic pillars.

## 1. Secure Execution & Guarded Runtime
The bedrock of autonomous agency is ensuring that third-party code cannot compromise the host system.

*   **Isolated Sandbox Environments:** Execute third-party plugins in containerized or virtualized "secure playgrounds" (e.g., Docker, Firecracker, or gRPC runtimes) to prevent code escaping.
*   **Deterministic Guardrails:** Implement "Agency Perimeters" that validate model-generated tool calls against predefined safety policies before execution.
*   **Resource Quotas:** Enforce limits on compute, memory, and network egress for each plugin to prevent resource exhaustion or "denial of service" attacks from within.

## 2. Advanced Agentic Orchestration
Creating a seamless bridge between model reasoning and concrete action.

*   **Universal Invocation Bridge:** A unified interface translating model-generated intents into structured, schema-validated task invocations.
*   **Dynamic Plugin Discovery:** A metadata registry where agents can "search" for capabilities (plugins) based on their current goal, adhering to standards like the Model Context Protocol (MCP).
*   **Persistent Memory & State:** Provide agents with "Episodic Memory" to retain context across different sessions and complex, multi-step tasks.

## 3. Governance & Identity
Ensuring every autonomous action is identified, authorized, and audited.

*   **Machine Identity & RBAC:** Assign a unique **Agent Principal** identity to every session. Agents inherit scoped access needed for their task, avoiding broad user permissions.
*   **"Flight Recorder" Observability:** Centralized logging capturing output, internal reasoning paths, and data source interactions for full auditability.
*   **Human-in-the-Loop (HITL) Triggers:** Thresholds that pause execution for high-risk actions (e.g., database writes, financial trades) requiring explicit human approval.

## 📊 Feature Roadmap for Reveila

| Phase | Feature | Status | Objective |
| :--- | :--- | :--- | :--- |
| **Phase I** | **Guarded Runtime** | ✅ | Isolate third-party plugin execution from the core platform. |
| **Phase II** | **Agent Principals** | ✅ | Establish non-person identities and scoped RBAC for agents. |
| **Phase III** | **Universal Invocation** | ✅ | Standardize how models trigger plugins via the Bridge. |
| **Phase IV** | **Flight Recorder** | ✅ | Capture reasoning traces and tool outputs for compliance. |
| **Phase V** | **Agentic Fabric** | ✅ | Orchestrate multi-agent workflows and Manager-Worker delegation. |

---

## 🏛️ Phase 1: The Guarded Runtime (Secure Core)
**Goal:** Isolate third-party code to prevent host contamination and resource exhaustion.

### Implementation Specs
*   **Isolation Layer:** Deploy plugins within gVisor or Firecracker micro-VMs.
*   **ClassLoader Shadowing:** Isolate plugin dependencies using a custom `Parent-Last ClassLoader`.
*   **Resource Quotas:** Map `cpuQuotaUs` and `maxMemoryBytes` from the `AgencyPerimeter` directly to container constraints.

---

## 🗺️ Phase 2: Universal Discovery & Invocation
**Goal:** Create a standardized "handshake" between the AI agent and the plugins.

### Implementation Specs
*   **Metadata Registry:** A catalog where plugins register capabilities using MCP schemas.
*   **Schema Enforcement:** Strict JSON schema validation before passing to the Guarded Runtime.

---

## 🛡️ Phase 3: Agency Perimeters (Governance)
**Goal:** Implement "Least Privilege" security and Human-in-the-Loop (HITL) oversight.

### Implementation Specs
*   **Perimeter Metadata:** Define `access_scopes` and `delegation_allowed` in manifests.
*   **JIT Credential Injection:** Inject short-lived OAuth tokens into the sandbox at runtime.
*   **HITL Triggers:** Intercept high-risk actions and pause for human approval.

---

## 📜 Phase 4: The Flight Recorder (Observability)
**Goal:** Capture the agent's reasoning and actions for compliance and debugging.

### Implementation Specs
*   **Reasoning Trace:** Log the LLM’s "Thought" process alongside the tool call.
*   **Immutable Logs:** Stream JSON audit logs to append-only storage.

---

## 🚀 Phase 5: The Agentic Fabric & Orchestration
**Goal:** Orchestrate multi-agent workflows and complex "Manager-Worker" patterns.

### Implementation Specs

#### 1. The Multi-Agent Handshake (AgentSession)
Instead of stateless calls, Phase 5 introduces the **AgentSession**. This allows context persistence across delegated sub-tasks.

| Component | Responsibility | Impact |
| :--- | :--- | :--- |
| **Session Manager** | In-memory/Redis store of sessions. | Memory persistence across delegation. |
| **Recursive Bridge** | Support for plugins calling the Bridge. | Enables Agents to invoke other Agents as tools. |
| **Trace Propagation** | Passing `trace_id` through nested calls. | Maintains a tree-structured audit log. |

#### 2. Orchestration Logic: The "Manager" Pattern
Implementation of a delegation mechanism within the `UniversalInvocationBridge`. When a task requires multiple specialized skills, the high-level agent delegates to worker plugins.

**Example Workflow:**
1.  **Manager Agent:** Receives high-level request (e.g., "Audit Q4 claims").
2.  **Delegation:** Manager calls the Bridge to invoke a `SQL-Worker`.
3.  **Isolation:** Worker runs in its own Guarded Runtime with restricted database read-access.
4.  **Re-Aggregation:** Worker returns data to Manager, who then delegates to a `Compliance-Checker`.

---

## ⚙️ Resource Quota Enforcement
Resource quotas prevent a third-party plugin from consuming the host's resources.

| Resource Category | Constraint Type | Proposed Default |
| :--- | :--- | :--- |
| **Compute** | CPU Shares | 0.5 vCPU |
| **Memory** | RAM Limit | 512 MB |
| **Storage** | Ephemeral Disk | 100 MB (Read-Only) |
| **Network** | Egress Rules | Whitelist Only |
| **Concurrency** | Thread Limit | 10 Threads |
