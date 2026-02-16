Reveila Initialization and Execution Architecture

1. Bootstrapping & Infrastructure
Configuration First: The Reveila class must initialize by loading reveila.properties and receiving a PlatformAdapter (e.g., Spring or Android).

Platform Abstraction: All interactions with the underlying host (file system, network, or Spring beans) must occur through the PlatformAdapter interface.

2. Component & Plugin Lifecycle
Metadata Discovery: During boot, Reveila parses all component JSON files in REVEILA_HOME to generate MetaObject maps.

Proxy-Based Invocations: Every component must be wrapped in a Proxy. If a component is a Plugin, the Proxy is responsible for loadPlugin()—creating a dedicated ClassLoader for that plugin's specific libraries.

Registry Access: All active proxies are stored in the SystemContext. Invocations should happen either via the global Reveila.invoke() or by fetching the named proxy from the context.

3. Integration of the Docker Guarded Runtime
The Docker implementation should not be a separate execution logic but a Specialized Platform Adapter behavior:

The Container is the Proxy: Instead of the Proxy executing code in the local JVM, the Proxy for a "Guarded Component" triggers the DockerGuardedRuntime.

The Mini-Reveila Runtime: The Docker image should contain a "Reveila Worker Agent" (a minimal Java app). When a container spawns, it receives the Proxy method and arguments via an entry point, executes them in total isolation, and returns the result to the main Reveila fabric.