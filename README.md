# 🏛️ Reveila-Suite
**The Hexagonal Multi-Tenant Application Platform**

Reveila-Suite is a high-extensibility, cross-platform distributed application platform. Engineered using **Hexagonal Architecture** (Ports and Adapters), it maintains a strict separation between core business logic and infrastructure-specific implementations, ensuring consistency across Backend, Web, and Mobile environments.



## 🏗️ Architectural Core

The system is anchored by **Reveila-Core**, a platform-agnostic engine that manages the lifecycle of components and configuration without being tied to a specific framework.

* **Clean Architecture:** Business logic is isolated in the Domain layer, while infrastructure (Spring Boot, Android, Standalone) is injected via the `PlatformAdapter` interface.
* **Dependency Inversion:** Core logic depends on abstractions; infrastructure implements them.
* **Universal Invocation Model:** Rather than traditional hardcoded REST mappings, Reveila uses a dynamic proxy mechanism to invoke component methods via a universal endpoint:
    * `POST /api/components/{componentName}/invoke`
* **Dynamic Component Loading:** Components are discovered, sequenced by priority, and validated via a JSON-based metadata system and a dedicated **Configuration Linter** at startup.

## 📦 Project Structure (Monorepo)

Managed as a monorepo to ensure API contracts and shared logic are synchronized across all layers:

| Module | Description |
| :--- | :--- |
| **[`/reveila`](reveila/README.MD)** | **Core Logic.** Shared Java classes, utilities, and the system engine. |
| **[`/spring`](spring/core/src/main/resources/application.properties)** | **Backend Implementation.** Spring Boot 3.5+ wrappers for the Core engine. |
| **[`/android`](android/readme.md)** | **Mobile Infrastructure.** Android-specific adapters and DEX loading logic. |
| **[`/apps`](apps/settings.gradle.kts)** | **Client Applications.** Expo/React Native and native Android projects. |
| **[`/connectors`](connectors/js/readme.md)** | **Integration Bridges.** JavaScript and Database adapters for external systems. |
| **[`/web`](web/vue-project/README.md)** | **Web Frontend.** Vue.js based administrative or user interfaces. |



## 🚀 Key Features

* **Dynamic Multi-Tenancy:** Automated data isolation at the repository layer, ensuring zero data leakage between organizations.
* **Advanced Mobile Strategy:** Supports dynamic plugin loading via `DexClassLoader` and library shadowing to prevent classpath conflicts in Android environments.
* **Universal Configuration:** A robust loader capable of resolving properties from local files, classpath resources, or remote URLs (`http`/`file`) seamlessly.
* **Modern Security Integration:** Integrated Spring Security 6.x+ with a custom **Role Hierarchy** (`ADMIN > USER > GUEST`) and secure BCrypt hashing.
* **Performance Tracking:** Intelligent routing that determines if a component call should be handled locally or distributed across a cluster for optimal execution.

## 🛠️ Tech Stack

* **Backend:** Java 21, Spring Boot 3.5, Hibernate/JPA, H2/PostgreSQL/MongoDB.
* **Mobile:** Kotlin/Java (Native), React Native (Expo).
* **Web:** Vue.js, TypeScript.
* **Build System:** Gradle (Kotlin DSL) and Maven.

## 🚦 Getting Started

### Prerequisites
* **JDK 21** or higher
* **Maven 3.9+** / **Gradle 8+**

### Quick Start
1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/yourusername/reveila-suite.git](https://github.com/yourusername/reveila-suite.git)
    ```
2.  **Environment Setup:** Ensure your `SYSTEM_HOME` environment variable points to your local configuration directory.
3.  **Run the Spring Backend:**
    ```bash
    mvn spring-boot:run -pl spring
    ```
4.  **Verification:** Access the H2 console at `/h2-console` to view the auto-initialized `admin` user and multi-tenant schema.

---

## 👨‍💻 Author
**Charles Lee** *Enterprise Architect specializing in scalable, multi-tenant software systems and cross-platform infrastructure.*