# Modularization

## The hexagonal architecture

* "Core Engine": The core engine is a self-contained Kotlin Multiplatform (KMP) runtime environment, which is compiled into supported native binaries that run on different platform/OS.
* "Platform Adapter": An abstraction layer (PlatforAdapter.kt) through which the core engine accesses the underlying platform/OS functions.
* "Platform Services": The system uses a component configuration file (spring.json, android.json, etc., stored in the ${system.home}/configs/ directory) to define what system components should be loaded for the specific platform. This configuration serves the purpose of the hexagonal architecture, where the implementation class can be swapped based on the needs and compatibility with the target platform. This configuration file is not a place to specify and load "beans", it's designed for extending and linking "capabilities" to the core engine.
* "Plugins": Plugins, whose definitions are stored in ${system.home}/plugins/, are similar to "system components" in the way, they extend the capabilities of the system, however, plugins are often provided by third parties. For security reasons, plugins are given less previledge at runtime, and by default, they are executed in an isolated sandbox. Plugins can be promoted to run as "component" with elevated previledge.
