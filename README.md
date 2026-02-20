# 🏛️ Reveila-Suite

Reveila-Suite is a cross-platform distributed **Agentic AI Execution Fabric** designed for autonomous discovery and execution of complex business logic. Built on a foundation of **Clean Architecture** and a **Universal Invocation Model**, it provides a secure, observable, and multi-tenant environment for deploying AI agents that can interact with legacy systems and modern cloud services with deterministic control.

## 🏗️ Architectural Core

The system is anchored by **Reveila-Core**, a platform-agnostic engine that manages the lifecycle of components and configuration.

*   **Hexagonal Architecture:** Business logic is isolated in the Domain layer, while infrastructure (Spring Boot, Android, Standalone) is injected via the `PlatformAdapter` interface.
*   **Guarded Runtime:** A secure execution environment that isolates third-party plugins using containerization (Docker) and ClassLoader shadowing to prevent host contamination.
*   **Universal Invocation Bridge:** A unified interface translating model-generated intents into structured, schema-validated task invocations, aligned with the **Model Context Protocol (MCP)**.
*   **Agency Perimeters:** Policy-driven guardrails that validate model-generated tool calls against predefined safety and security policies before execution.

## 🤖 Agentic AI Capabilities

We have successfully implemented the first five phases of our Agentic Roadmap:

1.  **Guarded Runtime (Phase I):** Full isolation of third-party JAR/DEX plugins.
2.  **Agent Principals (Phase II):** Machine identities and scoped RBAC for autonomous agents.
3.  **Universal Invocation (Phase III):** Standardized handshake between LLMs and the plugin ecosystem.
4.  **Flight Recorder (Phase IV):** Immutable audit logs capturing agent reasoning paths and tool outputs.
5.  **Agentic Fabric (Phase V):** Multi-agent orchestration supporting "Manager-Worker" patterns and recursive delegation.

## 📦 Project Structure (Monorepo)

Managed as a monorepo to ensure API contracts and shared logic are synchronized across all layers:

| Module | Description |
| :--- | :--- |
| **[`/reveila`](reveila/README.MD)** | **Core Logic.** Shared Java classes, utilities, and the platform-agnostic engine. |
| **[`/spring`](spring/core/src/main/resources/application.properties)** | **Backend Implementation.** Spring Boot 3.5+ wrappers for the Core engine. |
| **[`/android`](android/readme.md)** | **Mobile Infrastructure.** Android-specific adapters and DEX loading logic. |
| **[`/apps`](apps/settings.gradle.kts)** | **Client Applications.** Expo/React Native and native Android projects. |
| **[`/interfaces`](interfaces/README.md)** | **External Bridges.** Universal SDKs and framework-agnostic bridges. |
| **[`/web`](web/vue-project/README.md)** | **Web Frontend.** Vue.js based administrative and user interfaces. |

## 🚀 Key Features

*   **Manager-Worker Orchestration:** Support for complex delegated workflows where a Manager agent coordinates specialized workers.
*   **Forensic Traceability:** Every autonomous action is linked back to an original intent via a persistent `trace_id` and the **Flight Recorder**.
*   **Dynamic Multi-Tenancy:** Automated data isolation at the repository layer, ensuring zero data leakage between organizations.
*   **Advanced Mobile Strategy:** Supports dynamic plugin loading via `DexClassLoader` and library shadowing to prevent classpath conflicts.
*   **Universal Configuration:** A robust loader capable of resolving properties from local files, classpath resources, or remote URLs (`http`/`file`) seamlessly.
*   **Modern Security Integration:** Integrated Spring Security 6.x+ with a custom **Role Hierarchy** (`ADMIN > USER > GUEST`) and machine-specific **Agent Principals**.

## 🛠️ Tech Stack

*   **Backend:** Java 21, Spring Boot 3.5, Hibernate/JPA, H2/PostgreSQL/MongoDB.
*   **Mobile:** Kotlin/Java (Native), React Native (Expo).
*   **Web:** Vue.js, TypeScript.
*   **Build System:** Gradle (Kotlin DSL).

## 🚦 Getting Started

### Prerequisites
* **JDK 21** or higher
* **Gradle 8+**

### Quick Start
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/yourusername/reveila-suite.git
    ```
2.  **Environment Setup:** Ensure your `SYSTEM_HOME` environment variable points to your local configuration directory.
3.  **Run the Spring Backend:**
    ```bash
    ./gradlew :spring:core:bootRun
    ```
4.  **Verification:** Access the health check endpoint or Swagger UI (if configured) to verify the system status.

---

## 👨‍💻 Author
**Charles Lee**

I am a senior technology executive and Fractional CTO specializing in enterprise transformation, AI strategy, and platform modernization in complex, regulated environments.

My work focuses on helping executive teams and boards move from fragmented systems and experimental AI initiatives to scalable, production grade platforms that support long term growth, operational resilience, and measurable business outcomes. I advise organizations on how to align architecture, governance, and investment decisions so technology becomes an enabler of strategy rather than a source of risk.

Across financial services, healthcare, transportation, energy, and the public sector, I have led and advised large scale modernization initiatives involving cloud migration, enterprise architecture redesign, and data driven operating models. I have managed technology portfolios exceeding $300M annually and contributed to multi billion dollar transformation programs in highly regulated environments.

A sizable portion of my work involves M&A and transformation scenarios, including technology due diligence, integration planning, and post acquisition platform rationalization. I help investors and leadership teams identify architectural risk, scalability constraints, and hidden technical debt, while defining pragmatic roadmaps that accelerate value realization after closing.

As a practitioner in applied AI, I have architected agentic AI platforms that enable autonomous execution across complex enterprise systems while maintaining governance, control, and extensibility. I am known for translating advanced technical concepts into clear, actionable guidance for non technical executives and board members.

I currently advise organizations as a Fractional CTO and strategic technology partner.
