# Hexagonal Modularization

## The Reveila Suite is modularized into the following modules:

* "Core Engine": The core runtime environment is self contained and can run on different platform/OS, compatible with Java 17 (for Android).
* "Platform Adapter": The core engine is platform/OS agnostic, as it runs on top of an abstraction lay - the Platform Adapter (PlatforAdapter.java).
* "Platform Services": The system uses a component configuration file (spring.json, android.json, etc., stored in the ${system.home}/configs/ directory) to define what system components should be loaded for the specific platform. This configuration serves the purpose of the hexagonal architecture, where the implementation class can be swapped based on the needs and compatibility with the target platform. This configuration file is not a place to specify and load "beans", it's designed for extending and linking "capabilities" to the core engine.
* "Plugins": Plugins, whose definitions are stored in ${system.home}/plugins/, are similar to "system components" in the way, they extend the capabilities of the system, however, plugins are often provided by third parties. For security reasons, plugins are given less previledge at runtime, and by default, they are executed in a isolated sandbox. Plugins can be promoted to run as "component" with elevated previledge.


## UI portability and Engine portability

* Front end: Expo / React Native as "Single Source of Truth" for the UI (Web, Desktop, Mobile).
* Back end: Java / Kotlin Multiplatform (KMP), which compiles Java/Kotlin into native binaries for iOS (Objective-C/Swift framework), Windows (C++), and Web (WebAssembly).

| Platform | Frontend (UI) | Backend/Engine (Reveila) |
| :--- | :--- | :--- |
| **Android** | Expo / React Native | Native Java (Current) |
| **iOS** | Expo / React Native | KMP Compiled Framework (Native) |
| **Desktop** | Expo (via Electron/Desktop) | KMP Native Binary |
| **Web** | React (Expo Web) | KMP Wasm / Server-side Java |

---

