# Reveila-Suite Developer Guide & Hands-On Tutorials

This guide covers repository structure, local build verification, core runtime concepts, and step-by-step tutorials for embedding Reveila, exposing existing plugins as governed AI tools through JSON configuration, writing custom `ReveilaTool` implementations, and integrating legacy systems over REST.

**Architecture Context:** For platform positioning, security architecture, and comparisons with LangChain and AutoGen, see [Reveila Platform Overview & Architecture](./Reveila%20Platform%20Overview%20%26%20Architecture.md).

---

## 1. Repository Map

| Module / Directory | Role |
| :--- | :--- |
| [reveila/core](../../reveila/core/build.gradle.kts) | Pure Kotlin Multiplatform engine: `Reveila`, `SystemComponent`, `SystemContext`, `PlatformAdapter`, `DataService`, `MetadataRegistry`, `SecurityPerimeter`, `SecretManager`, `ManagedInvocation`, `JsonSchemaEnforcer`. |
| `reveila/ext` | Sovereign AI layer: `AgenticFabric`, `AutonomousAgent`, `TaskCapabilityRouter`, `Prompt`, `PromptTemplateRegistry`, `KnowledgeVault`, `DynamicToolProvider`, `ReveilaToolCoordinator`, LLM providers (`OpenAiLlmProvider`, `GeminiLlmProvider`, `LocalLlamaProvider`), and core tools (`VirtualBrowserTool`, `TerminalTool`, `ResumableDownloadTool`). |
| [reveila/rt](../../reveila/rt/src/main/java/com/reveila/rt/DockerGuardedRuntime.java) | Guarded container runtime (`DockerGuardedRuntime`) using Docker + gVisor (`runsc`). |
| `spring/core` & `spring/admin` | Spring Boot adapters (`ApiController` at `/api/components/{componentName}/invoke`, RBAC/Oversight interceptors, Spring Boot Admin server). |
| `apps/java-test` | Standalone JVM host ([ReveilaTestApp.java](../../apps/java-test/src/main/java/com/reveila/test/ReveilaTestApp.java)) demonstrating Java-hosted `reveila:core` boot, config linting, and component startup. |
| `apps/Aegin` | Sovereign Edge AI client application (Compose UI, on-device GGUF inference, Knowledge Vault directory scoping, live tool takeover & approval UI). |
| `system-home/standard` | Default declarative configuration root (`reveila.properties`, `configs/shared/standard.json`, `configs/plugins/*.json`, `configs/capabilities.json`, `configs/governance-policy.json`, `configs/templates/prompts/`, `tasks/*.json`). |

---

## 2. Prerequisites & Quick Verification

**Requirements:**
* **JDK 17+** (Gradle toolchain targets JVM 17)
* Gradle Wrapper included (`./gradlew` on macOS/Linux/Git Bash, `gradlew.bat` on Windows)
* Optional: Android SDK (for `apps/Aegin`), Docker Engine with `runsc` (for `DockerGuardedRuntime`)

Run the core and AI extension unit tests to confirm your environment is healthy:

```bash
./gradlew :reveila:core:jvmTest :reveila:ext:jvmTest --no-daemon
```

Run the standalone Java verification app ([ReveilaTestApp](../../apps/java-test/src/main/java/com/reveila/test/ReveilaTestApp.java)):

```bash
./gradlew :apps:java-test:run --no-daemon
```

---

## 3. Core Concepts in 5 Minutes

1. **`Reveila` & `SystemContext`:** The `Reveila` engine reads `reveila.properties` and component manifests from `system.home` (`configs/shared/standard.json` + active platform config such as `configs/java/java.json`), validates dependencies with `ConfigurationLinter`, wraps components in `SystemProxy`, and boots them in dependency order.
2. **`SystemComponent`:** Extend [SystemComponent.kt](../../reveila/core/src/commonMain/kotlin/com/reveila/system/SystemComponent.kt) and implement `onStart()` / `onStop()`. Obtain collaborators cleanly through `getComponent("MetadataRegistry")` or `context?.getProxy("SecretManager")`.
3. **Configuration Overlays (`system-home`):**
   * `reveila.properties` controls operational mode (`system.standalone`, `platform`, `ai.llm.model.name`, `plugin.local.dir`).
   * `configs/global-perimeter.json` sets system-wide CPU, memory, timeout, and network caps.
   * [governance-policy.json](../../system-home/standard/configs/governance-policy.json) configures prompt-injection blocking, prohibited commands, DLP redaction regexes, and `dualModelGovernance`.
4. **Plugin ≠ AI Tool:**
   * **Plugins** (`configs/plugins/*.json`) register services and `SecurityPerimeter` policies in `MetadataRegistry`.
   * **AI Tools** are an explicit opt-in subset exported by `MetadataRegistry.exportToMCP()`, filtered per target LLM by `ReveilaToolCoordinator.isManifestAllowedForTarget()`, and optionally narrowed by semantic **Tool RAG** (`DynamicToolProvider`).

---

## 4. Tutorial 1: Booting Reveila in Embedded Java & Invoking Components

You can embed Reveila inside any existing JVM service with just a few lines of Java (following [ReveilaTestApp.java](../../apps/java-test/src/main/java/com/reveila/test/ReveilaTestApp.java)):

```java
import com.reveila.system.Reveila;
import com.reveila.system.Subject;

public class EmbeddedDemo {
    public static void main(String[] args) throws Exception {
        Reveila reveila = new Reveila();
        reveila.start(); // Loads system-home configs, lints, starts DAG of SystemComponents

        try {
            // Invoke any registered component/plugin through the universal proxy bus
            Object reply = reveila.invoke(
                "EchoService",
                "echo",
                new Object[] { "Hello from Reveila" },
                "127.0.0.1",
                (Subject) null
            );
            System.out.println("Component returned: " + reply);

            // Invoke the Agentic AI Fabric directly (requires :reveila:ext on the classpath
            // alongside :reveila:core, e.g. in Spring Boot or Aegin hosts)
            Object agentReply = reveila.invoke(
                "AgenticFabric",
                "askAgent",
                new Object[] { "Give me a brief AI security news update", "session-1", null },
                "127.0.0.1",
                (Subject) null
            );
            System.out.println("Agent returned: " + agentReply);
        } finally {
            reveila.shutdown();
        }
    }
}
```

**What `reveila.start()` performs automatically:**
1. Loads `configs/shared/standard.json` + active platform overlay (`configs/java/java.json` or `spring/spring.json`).
2. Starts core infrastructure (`DataService`, `MetadataRegistry`, `SecretManager`, `ManagedInvocation`, `AgenticFabric`, `AutonomousAgent`, etc.).
3. Scans `configs/plugins/*.json` and populates [MetadataRegistry](../../reveila/core/src/commonMain/kotlin/com/reveila/safety/MetadataRegistry.kt).

---

## 5. Tutorial 2: Exposing an Existing Plugin as a Governed AI Tool (Configuration Only)

Suppose you already have a plugin deployed — take [EchoService.json](../../system-home/standard/configs/plugins/EchoService.json) as an example. By default, `EchoService.json` defines `"methods"` and `"agency_perimeter"`, **without `"tool_definitions"` or `"expose_as_tool"`**, so `MetadataRegistry.exportToMCP()` keeps it hidden from AI agents.

To turn an existing plugin into a selectively exposed AI tool **without modifying Java or Kotlin code**, update its JSON manifest in `<system.home>/configs/plugins/<Plugin>.json`:

```json
{
  "plugin": {
    "name": "EchoService",
    "displayName": "Echo Service",
    "version": "1.0.0",
    "description": "Echoes and transforms test input strings inside the runtime.",
    "class": "com.reveila.service.EchoService",
    "expose_as_tool": true,
    "tier": "Tier 3",
    "cloud_only": false,
    "min_parameter_size_b": 1.5,
    "allowed_models": [],
    "routing_keywords": ["echo back", "repeat after me"],
    "methods": [
      {
        "name": "echo",
        "description": "Returns the input string.",
        "parameters": [
          {
            "name": "InputString",
            "description": "The text string to echo back",
            "type": "java.lang.String",
            "isRequired": true,
            "isSecret": false
          }
        ]
      }
    ],
    "agency_perimeter": {
      "accessScopes": ["tool:execute"],
      "allowedDomains": [],
      "internetAccessBlocked": true,
      "maxMemoryMb": 256,
      "maxCpuCores": 1,
      "maxExecutionSec": 10,
      "delegationAllowed": false
    }
  }
}
```

### How Each Field Governs Behavior
* **`expose_as_tool` (`true` / `false`)**: Explicit opt-in/opt-out flag. Setting `"expose_as_tool": false` hides a plugin from every LLM immediately without disabling the plugin itself.
* **`description` + `methods`**: `MetadataRegistry` forwards rich descriptions, method signatures (`echo(InputString)`), and auto-generated JSON schemas into both the system prompt and OpenAI/Gemini `"tools"` arrays.
* **Named parameter binding**: Even if an LLM emits JSON keys out of order (`{"InputString": "hi", "method": "echo"}`), [ManagedInvocation](../../reveila/core/src/commonMain/kotlin/com/reveila/safety/ManagedInvocation.kt) binds values to the declared parameter order in `methods` before invoking the plugin proxy.
* **`cloud_only`, `min_parameter_size_b`, `allowed_models`**: Controls which target LLMs can see the tool (e.g., withholding complex enterprise tools from small `<= 2B` edge models while exposing them to Cloud or `>= 7B` local models).
* **`routing_keywords`**: Dynamically extends [ReveilaToolCoordinator.shouldRouteToAgenticLoop()](../../reveila/ext/src/commonMain/kotlin/com/reveila/ai/ReveilaToolCoordinator.kt) so local models enter the agentic loop when users phrase matching requests.

---

## 6. Tutorial 3: Writing & Registering a Custom `ReveilaTool` in Kotlin

Implement [ReveilaTool](../../reveila/ext/src/commonMain/kotlin/com/reveila/ai/ReveilaTool.kt) when building first-class local tools that integrate with `ReveilaToolCoordinator`'s interactive `Approve Once` / `Always Allow` prompts, `SecretManager` domain scopes, or `ToolActivityHub`.

### Step 1: Implement `ReveilaTool`

```kotlin
package com.example.tools

import com.reveila.ai.ReveilaTool

class WeatherTool : ReveilaTool {
    override fun getName(): String = "weather_lookup"

    override fun getDescription(): String = """
        Retrieves local weather status for a city.
        Commands:
        - forecast: { "action": "forecast", "city": "Austin, TX" }
    """.trimIndent()

    override fun execute(args: Map<String, Any?>): String {
        val action = (args["action"] as? String)?.lowercase() ?: "forecast"
        if (action != "forecast") return "Error: unsupported action '$action'."
        val city = (args["city"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return "Error: 'city' parameter is required."
        // Replace with your real HTTP/service call (or delegate to VirtualBrowser / API client)
        return "Weather for $city: 74°F, Sunny."
    }
}
```

### Step 2: Register Dynamically with `ReveilaToolCoordinator`

```kotlin
val fabric = reveila.systemContext?.getProxy("AgenticFabric")?.getInstance() as? com.reveila.ai.AgenticFabric
val registry = reveila.systemContext?.getProxy("MetadataRegistry")?.getInstance() as? com.reveila.safety.MetadataRegistry

fabric?.getToolCoordinator()?.registerTool(WeatherTool(), registry)
```

Calling `registerTool(tool, registry)` automatically:
1. Makes `weather_lookup` resolvable inside [AgenticFabric](../../reveila/ext/src/commonMain/kotlin/com/reveila/ai/AgenticFabric.kt) tool loops.
2. Publishes a `Tier 2` capability manifest into `MetadataRegistry` (`exposeAsTool = true`) for Cloud and eligible local models.
3. Enforces `SecretManager` permissions and HITL authorization cards through `ReveilaToolCoordinator.authorizeAndExecute()`.

---

## 7. Tutorial 4: Legacy System Integration via the Unified REST Gateway

When hosted in Spring Boot (`spring/core`), [ApiController](../../spring/core/src/main/java/com/reveila/spring/system/ApiController.java) exposes every component and plugin through a single version-guarded endpoint:

```http
POST /api/components/AgenticFabric/invoke
Content-Type: application/json

{
  "version": "1.0.0",
  "methodName": "askAgent",
  "args": [
    "Summarize Q4 customer escalations and check our status endpoint",
    "crm-session-42",
    null
  ]
}
```

* **Zero boilerplate controllers:** New components and plugins become remotely invocable immediately with `OversightInterceptor` governance, `MethodDTO` version validation, and Spring `SecurityContext` RBAC (`Subject` / `RolePrincipal`).
* **Sandboxed Docker callbacks:** Isolated plugins executing via `DockerGuardedRuntime` (`reveila:rt`) call host components back on this same endpoint using `ReveilaClient` and short-lived `JIT` tokens from `SecretManager`.

---

## 8. Configuring Hybrid Routing, Prompts & Scheduled Tasks

### 8.1 Edge vs. Cloud Routing (`configs/capabilities.json`)
Edit [capabilities.json](../../system-home/standard/configs/capabilities.json) to classify intents across the 6 routing categories (`LOCAL_TEXT_TRANSFORM`, `LOCAL_STRUCTURED_EXTRACT`, `LOCAL_DEVICE_ACTION`, `CLOUD_LIVE_RETRIEVAL`, `CLOUD_DEEP_REASONING`, `CLOUD_MULTI_STEP_AGENT`). In `Auto` mode, local summarization stays private on-device while intents like `news.search` escalate to Cloud automatically.

### 8.2 Model-Specific Prompt Templates (`configs/templates/prompts/`)
`PromptTemplateRegistry` resolves markdown templates in priority order:
1. Exact model file: `configs/templates/prompts/models/<model>.md` (e.g., [gemma-2-2b-it.md](../../system-home/standard/configs/templates/prompts/models/gemma-2-2b-it.md))
2. Family file: `configs/templates/prompts/families/<family>.md` (`qwen2.5.md`, `llama3.md`, `gemma.md`)
3. Generic defaults: `configs/templates/prompts/default.md` (`AGENTIC_JSON`) or `conversational.md` (`CONVERSATIONAL`)

YAML frontmatter controls `chat_format`, `stop_token`, `max_history_messages`, `parameter_size_b`, and `response_mode`.

### 8.3 Scheduled Autonomous Tasks (`tasks/*.json`)
Place task files in `${REVEILA_HOME}/tasks/` (see [daily-news.json](../../system-home/standard/tasks/daily-news.json)) so `AutonomousAgent.doTask` executes them in isolated `TaskExecutionSandbox` workers:

```json
{
  "taskId": "daily-news-alert",
  "title": "Daily AI Security Briefing",
  "intent": "news.search",
  "prompt": "Find the latest news about AI security and provide a summary.",
  "frequency": "daily",
  "scheduleTime": "08:00",
  "executionTarget": "auto",
  "requiresApproval": false,
  "enabled": true,
  "status": "ACTIVE"
}
```

---

## 9. Quick Troubleshooting Checklist

| Symptom | What to Check |
| :--- | :--- |
| LLM never sees a newly added plugin tool | Confirm `"expose_as_tool": true` (or non-empty `"tool_definitions"`) in `configs/plugins/*.json`, and check `"cloud_only"`, `"min_parameter_size_b"`, and `"allowed_models"` against the active provider/model. |
| On-Device model replies in plain text instead of calling your tool | Add trigger phrases to `"routing_keywords"` in the plugin manifest, or switch to a larger model (`> 2.2B`) / `Auto` / Cloud mode. |
| Multi-parameter plugin method receives values in wrong order | List `"parameters"` in `"methods"` inside `configs/plugins/*.json` in the exact JVM parameter declaration order so `ManagedInvocation` aligns JSON keys cleanly. |
| Execution pauses with `ESCALATE` (`APPROVAL_REQUIRED`) | Expected HITL guardrail on first non-read-only action; approve once or persistently (`APPROVE_ALWAYS`) via `ToolActivityHub` / UI prompt or pre-grant the scope in `SecretManager`. |
