# 🧠 Memory Bank: System Map
**Status:** Active | **Architecture:** Hexagonal / Monorepo

## 🗺️ System Topology (Mermaid)

```mermaid
graph TB
    subgraph Clients ["Client Layer (Monorepo)"]
        AndroidApp[Android App<br/>'Java/Kotlin']
        WebApp[Web App<br/>'Vue/TS']
    end

    subgraph Backend ["Server Layer (Monorepo)"]
        JavaAPI[Java Backend<br/>'Spring Boot']
        DB[(PostgreSQL/H2)]
    end

    subgraph Plugins ["Extension Layer"]
        PluginA[Plugin A<br/>'DEX JAR']
        PluginB[Plugin B<br/>'DEX JAR']
    end

    %% Relationships
    AndroidApp -- "Universal Invocation" --> JavaAPI
    WebApp -- "Universal Invocation" --> JavaAPI
    JavaAPI -- "JPA / JDBC (Multi-tenant)" --> DB

    %% Dynamic Loading
    AndroidApp -- "DexClassLoader" --> PluginA
    AndroidApp -- "DexClassLoader" --> PluginB

    %% Styling
    style AndroidApp fill:#3DDC84,stroke:#333,stroke-width:2px
    style WebApp fill:#42b883,stroke:#333,stroke-width:2px
    style JavaAPI fill:#6DB33F,stroke:#333,stroke-width:2px
    style DB fill:#336791,stroke:#333,stroke-width:2px
```

## 🏗️ Architectural Core
The system is anchored by **Reveila-Core**, a platform-agnostic engine that resides within the Backend and Android modules via shared library linkage.

- **Hexagonal Pattern:** Business logic is isolated in the Domain layer, while infrastructure (Spring Boot, Android, Standalone) is injected via the `PlatformAdapter` interface.
- **Universal Invocation Model:** Clients interact with components through a proxy mechanism rather than hardcoded endpoints. This ensures the Core logic is exposed consistently across all platforms.
- **Dynamic Component Loading:** Components are discovered, sequenced by priority, and validated via JSON metadata and the system's internal `ConfigurationLinter` at startup.

## 📦 Project Structure (Monorepo)
The suite is organized into specialized modules to maintain strict separation of concerns:

| Module | Description |
| :--- | :--- |
| `/reveila` | **Core Logic.** Shared Java classes, utilities, and the platform-agnostic system engine. |
| `/spring` | **Backend.** Spring Boot 3.5+ implementation providing JPA persistence and Security. |
| `/android` | **Mobile Infrastructure.** Android-specific adapters and DEX loading logic. |
| `/web` | **Web Frontend.** Vue.js based administrative and user interfaces. |
| `/interfaces` | **External Bridges.** Official home for the Universal SDK, framework-agnostic bridges, and specialized oversight UIs (like the CISO Dashboard). |

## 🔌 Communication & Invocation Path

### 1. Web/Mobile to Backend
Utilizes the **Universal Invocation Endpoint**: `/api/components/{componentName}/invoke`. 
This architectural choice allows the frontend to call backend services dynamically without needing unique controller mappings for every new business method.

### 2. Android to Plugins
Leverages a **DEX Loading Strategy** via `DexClassLoader`. 
This allows the mobile client to extend its functionality at runtime by loading external JARs, which are "shadowed" to prevent classpath conflicts with the host application.

## 🚦 System Boundaries

- **Boundary A (Web/Mobile -> Spring):** Handled via REST/JSON. Protected by CORS and Spring Security.
- **Boundary B (Spring -> Core):** Handled via the `PlatformAdapter` interface. No network overhead; communication happens within the local JVM.
- **Boundary C (Android -> Core):** Handled via the Android Service bridge, supporting local-first execution of core logic.
