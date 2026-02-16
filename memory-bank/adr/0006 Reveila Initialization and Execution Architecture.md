Reveila Initialization and Execution Architecture

🏛️ The Execution Flow (Architectural Guardrails)
Discovery: During boot, Reveila parses the JSON. If it sees a component entry, it creates a MetaObject.

Proxy Wrap: Reveila creates a Proxy for that MetaObject.

Lifecycle Hook: When Reveila starts, it iterates through Proxies. If the configuration includes a "plugin" section:

The Proxy.onStart() triggers loadPlugin(Path).

The Child-First Class Loader isolates those specific libraries (crucial for having different AI providers or healthcare connectors with conflicting dependencies).

Service State: Since the class extends AbstractService, it inherits standardized start()/stop() hooks, making the entire fabric predictable for the CISO Kill Switch we discussed for the pitch deck.

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


Sovereign Architecture Re-Alignment:

Refactor ReveilaConfiguration: Remove all beans except Reveila and ApplicationRunner. The Spring configuration should ONLY handle the SpringPlatformAdapter.

Internalize Components: Move the instantiation of UniversalInvocationBridge, MetadataRegistry, OrchestrationService, and the LlmProviders into the Reveila Core initialization.

JSON-Driven Discovery: These components must be defined in system-components.json. During boot, the Reveila class must create MetaObject instances for them and register their Proxies in the SystemContext.

Platform Abstraction: If a core component (like the UniversalInvocationBridge) needs a platform-specific feature (like a Spring-managed Data Source), it must request it through the PlatformAdapter interface.

Proxy-First Invocation: Ensure that the AgenticFabric retrieves the UniversalInvocationBridge by name from the SystemContext via its Proxy, rather than having it @Autowired by Spring.

Service Start-up and Stop: For any component that needs to run as a Service, its Java class must extend com.reveila.system.AbstractService, and add a configuration section in the ${REVEILA_HOME}/configs/components/system-components.json, or in a separate JSON file in the same directory. If a component's configuration contains a "plugin" section, the Proxy's onStart() method will call loadPlugin(Path) which will use a child-first class loader to isolate the plugin's class instances.