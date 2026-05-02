This ADR documents the pivot from isolated monitoring to a **Sovereign Observability Fabric**. In the 2026 enterprise landscape, observability is no longer just "logs and metrics"; it is the **Evidence Layer** required for agentic autonomy.

For the **Reveila Suite**, ADR 0018 formalizes the integration of the **MiniMax-driven i18n Auditor**, the **Biometric Kill Switch**, and the **VS Code Multi-Target Debugger** into a single governance control plane.

---

## ADR 0018: Unified Observability and Governance Fabric

**Status:** Accepted  
**Date:** May 2, 2026  
**Deciders:** Charles Lee (Principal Architect)  
**Consulted:** Reveila Engineering Team, InfoSec Stakeholders  

### 1. Context and Problem Statement
As the Reveila Suite transitions from assistive AI to **Autonomous Agentic Workflows**, we face "The Visibility Gap." Traditional observability tools monitor system health (CPU/RAM) but fail to capture the **Reasoning Provenance**—the "Why" behind an agent's decision. 

Furthermore, local execution on Android (S23 Ultra) introduces risks of **Inference Drift** and **Hallucinated Configuration**, where AI-generated localization strings could inadvertently bypass security intent or misguide the user.

### 2. Decision Drivers
* **Sovereignty:** We must maintain absolute authority over agent actions without relying on third-party cloud monitoring.
* **Integrity:** Every localized string and configuration key must be verifiable against the English Master Core.
* **Security:** High-stakes actions (M&A integration, system shutdown) must be gated by a hardware-backed handshake.
* **Observability:** Developers need "Silicon-to-Software" visibility, tracing Java services down to the C++ native inference layer.

### 3. Proposed Solution: The Unified Fabric
We will implement a three-layer Observability and Governance Fabric integrated directly into the SDLC.

#### **Layer 1: The Build-Time Auditor (Governance)**
* **MiniMax-Powered Auditor:** Automatically audits `.properties` files during the build process to detect "Orphaned" or "Hallucinated" keys.
* **Audit Reports:** Generates `TRANSLATION_AUDIT.md` for human review before any deployment.
* **Human-in-the-Loop (HITL):** Introduces the `@human-verified` tag to protect manually tuned strings from AI overwrites.

#### **Layer 2: The Runtime Guardrail (Security)**
* **Biometric Kill Switch:** Physical-layer fingerprint/face authentication required for high-risk agentic tool calls.
* **Native Watchdog:** A synchronized Java-to-C++ watchdog monitoring the `llama-server` process to prevent zombie states or unauthorized memory access.

#### **Layer 3: The Developer Control Plane (Observability)**
* **Multi-Target Debugging:** Unified VS Code environment for concurrent Java and C++ (LLDB) debugging.
* **IDE-Native Problems:** Direct piping of audit flags into the VS Code Problems tab for zero-latency remediation.

### 4. Consequences
* **Positive:** Established a "Single Pane of Glass" for both security and performance.
* **Positive:** Reduced "Key Drift" in localized versions of the app by 100% via automated build-time gates.
* **Positive:** High-integrity evidence trails for future M&A due diligence (showing exact provenance of agent decisions).
* **Negative:** Increased initial build time by ~5-8 seconds due to the AI-driven audit phase.
* **Negative:** Requires developers to maintain LLVM/LLDB tooling on local machines for native debugging.

---

### 5. Compliance and Standards Mapping
| Standard | Reveila Implementation |
| :--- | :--- |
| **EU AI Act** | Decision Traceability via `TRANSLATION_AUDIT.md`. |
| **NIST AI RMF** | Continuous Measurement via Runtime Watchdog. |
| **ISO 42001** | Accountable HITL through `@human-verified` metadata. |

---

### 📢 Milestone Update: May 2, 2026
This ADR represents the "Final Hardening" of the **Reveila v1.0 Architecture**. We have successfully moved governance from a manual checklist to an automated, IDE-native dependency.
