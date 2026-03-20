# 🏛️ Reveila-Suite

Reveila-Suite is a cross-platform distributed **Agentic AI Execution Fabric** designed for autonomous discovery and execution of complex business logic. Built on a foundation of **Clean Architecture** and a **Universal Invocation Model**, it provides a secure, observable, and multi-tenant environment for deploying AI agents that can interact with legacy systems and modern cloud services with deterministic control.

---

## 🏛️ Landing Page: The "Sovereign Mode" Experience

### **Your AI Doesn’t Need a Passport. Or a Signal.**
*Introducing Reveila Sovereign Mode. The first high-performance AI agent that runs 100% on-device. No cloud. No latency. Total Sovereignty.*

### ✈️ The "Flight Mode" Paradox Solved
Most AI "assistants" turn into expensive paperweights the moment you hit 30,000 feet. While others are waiting for the slow, expensive plane Wi-Fi to load a single prompt, Reveila is already working.
*   **Offline Reasoning:** Use the full power of our 4B-parameter "Sovereign Core" model directly on your Android flagship.
*   **Zero-Latency Memory:** Access your entire "Artificial Memory" (SQLite-vec) without a single millisecond of network lag.
*   **The Vault:** Your data never leaves the device. What you brainstorm in the air stays on your hardware.

### 🛡️ Features for the High-Trust Professional
| Feature | The Cloud Way | The Reveila Sovereign Way |
| :--- | :--- | :--- |
| **Privacy** | Data is logged, trained on, and stored in a data center. | **Data is Yours.** 100% local processing. |
| **Speed** | 500ms–2s "Round Trip" delay. | **Instant.** 5ms–20ms local response. |
| **Reliability** | "Server Down" = Work Stops. | **Always On.** Works in subways, planes, and rural areas. |
| **Security** | Software-only toggles. | **Biometric Gated.** Fingerprint required for any system action. |

### 📱 Hardware-Aware Performance
Reveila doesn't just "run" on your phone; it tunes itself to your silicon.
*   **NPU Acceleration:** We offload the heavy lifting to your phone’s Snapdragon or Tensor chip, giving you desktop-class speed with 8x more battery efficiency.
*   **Adaptive Intelligence:** 16GB RAM? Enjoy our high-fidelity 4B model. 8GB RAM? We’ll swap in our "Lite" engine so you never feel a stutter.

### 🚀 Strategic "Call to Action"
*"Ready to take back your digital sovereignty?"*

**[Download Reveila Personal (APK)]**  |  **[Explore the Nexus Playground]**

---

## 🏛️ Two Editions. One Architecture.

### 1. Reveila Personal Edition (The Sovereign Standalone)
Stop renting your brain. Reveila is the zero-ops, local-first alternative to cloud-locked assistants. Own your memory. Own your privacy.

**Quick Start (Choose your path):**
*   **Option A: The Mobile Watchdog (Android Standalone)**
    *   *Best for:* Privacy-first voice assistance and biometric safety.
    *   *Download:* the `Reveila-Personal.apk` from our Releases.
    *   *Launch:* The app will automatically detect your hardware.
    *   *One-Tap Model Load:* Select "Gemma-3-1B (Optimized)" to download a high-speed, on-device model.
    *   *Secure:* Set up your Biometric Kill Switch.
*   **Option B: The Home Base (PC/Mac + Android)**
    *   *Best for:* Enterprise-grade reasoning and M&A research.
    *   *Install the Daemon:* `brew install reveila-suite` (or download the Windows `.exe`).
    *   *Zero-Config:* Reveila ships with an embedded SQLite database. No Postgres setup required.
    *   *Link Phone:* Scan the QR code on your desktop with the Reveila Android app. Your phone is now the biometric key for your PC's high-power AI.

**Personal Edition System Requirements:**
| Tier | Device Profile | Recommended Hardware | Core Capability |
| :--- | :--- | :--- | :--- |
| **Watcher** | Mid-range Android | 6GB–8GB RAM, ARM64 | Thin Client: Remotes into a Home PC/Server. Handles Biometrics & Notifications locally. |
| **Sovereign** | Flagship Android | 12GB–16GB RAM, Snapdragon 8 Gen 3+ | Standalone: Runs Gemma 3 4B or Phi-4 Mini natively. Fully offline "Artificial Memory." |
| **Power** | PC / Mac Mini | 32GB RAM, RTX 50-series (16GB+ VRAM) | Local Hub: Runs Llama 3.3 70B or Qwen 2.5 32B. Serves as the high-power brain for all your devices. |

### 2. Reveila Enterprise Edition (The Postgres Cloud Fabric)
When you're ready to scale to a team, just point the daemon to a PostgreSQL instance. The schema is identical. Enjoy multi-agent orchestration, dynamic multi-tenancy, and advanced forensic traceability (Flight Recorder) via Spring Boot.

---

## 🤖 The Architecture Roadmap

### ✅ Phase 1: The Remote Guard (Now)
*   **Concept:** Android App acts as a Biometric Key + Remote UI for your existing Spring Boot/Postgres server.
*   **Focus:** Getting users "aware" of the Reveila interface.

### ✅ Phase 2: The Local Memory (Next 30 Days)
*   **Concept:** Implement the Sovereign Memory (`SQLite-vec`) on Android.
*   **Focus:** User's chat history is now local and "Sovereign" even if the server is offline.

### ✅ Phase 3: The Standalone Brain (Goal)
*   **Concept:** Integrate `LiteRT-LM` for full on-device inference on flagship phones.
*   **Focus:** "Incognito Mode" (Full offline reasoning with zero data leaving the device).

---

## 🏗️ Architectural Core

The system is anchored by **Reveila-Core**, a platform-agnostic engine that manages the lifecycle of components and configuration.

*   **Hexagonal Architecture:** Business logic is isolated in the Domain layer, while infrastructure (Spring Boot, Android, Standalone) is injected via the `PlatformAdapter` interface.
*   **Guarded Runtime:** A secure execution environment that isolates third-party plugins using containerization (Docker) and ClassLoader shadowing to prevent host contamination.
*   **Universal Invocation Bridge:** A unified interface translating model-generated intents into structured, schema-validated task invocations, aligned with the **Model Context Protocol (MCP)**.
*   **Agency Perimeters:** Policy-driven guardrails that validate model-generated tool calls against predefined safety and security policies before execution.

---

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

## 🛠️ Tech Stack

*   **Backend:** Java 21, Spring Boot 3.5, Hibernate/JPA, H2/PostgreSQL/MongoDB.
*   **Mobile:** Kotlin/Java (Native), React Native (Expo).
*   **Web:** Vue.js, TypeScript.
*   **Build System:** Gradle (Kotlin DSL).

---

## 🚦 Getting Started (Developer Setup)

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

*“Inspired by the freedom of OpenClaw. Hardened by the standards of the Enterprise. Built for you.”*
