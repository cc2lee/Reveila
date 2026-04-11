# Android

- Compile your plugin's Java/Kotlin code to standard .class files.
- Run Android's d8 (or r8) tool to convert those .class files into a classes.dex file.
- Zip that classes.dex file into an archive named [plugin-name].jar.

# Plugin Registration API

To register a new plugin dynamically through the API, you must send a `POST` request to `/api/settings/plugins`.

The endpoint requires a single composite JSON payload containing both the database registry metadata (`metadata` object) and the underlying engine configuration (`manifest` object). 

### Security Perimeter Injection
During registration, the backend server intercepts the `manifest` payload and automatically overwrites any security perimeters with the system's active governance policy (`global-perimeter.json`). The resulting secured manifest is immediately written to the active `configs/plugins` directory and loaded into the `GuardedRuntime`.

### 2. The Plugin Configuration Sandbox

In the Reveila architecture, there are two distinct ways to pass configuration to a plugin, depending on whether the configuration is static or dynamic.

#### Static Configuration (The Plugin Manifest)
If the configuration is intrinsic to how the plugin operates (like a default model name or a hardcoded fallback URL), it belongs directly in the plugin's JSON manifest under the **`arguments`** array.

When the Engine boots, it automatically performs Setter Injection.
**Example (`GeminiProvider.json`):**
```json
{
  "name": "GeminiProvider",
  "class": "com.reveila.ai.GeminiProvider",
  "arguments": [
    { "name": "model", "value": "gemini-1.5-pro" },
    { "name": "temperature", "value": 0.1, "type": "double" }
  ]
}
```
*(The engine automatically calls `setModel("gemini-1.5-pro")` and `setTemperature(0.1)` on your Java class during boot).*

#### Dynamic Configuration (The Plugin Context Properties)
The `PluginContext` is a secure properties sandbox designed for dynamic, admin-controlled settings that flow into your plugin at runtime.

Administrators add these settings to the Global Settings UI (e.g., inside `llm.properties` or `reveila.properties`). To prevent violating the Principle of Least Privilege, a plugin never receives the entire system properties file. Instead, the Engine scrapes the global settings, filters out anything not explicitly prefixed with your plugin's name, strips the prefix, and securely injects the filtered map into the `PluginContext`.

**Example Global Settings:**
- `plugin.GeminiProvider.apiKey = REF:SECRET_123`
- `plugin.GeminiProvider.maxRetries = 3`

**Example Plugin Usage:**
```java
// Inside your plugin's onStart() or execute() method:
String apiKey = this.context.getProperties().getProperty("apiKey"); // "REF:SECRET_123"
String retries = this.context.getProperties().getProperty("maxRetries"); // "3"
String mode = this.context.getProperties().getProperty("system.mode"); // "production" (Safe global property)
```

**Rule of Thumb:**
> Use the manifest `arguments` for static wiring and defaults. Use the Global Settings UI (with the `plugin.[Name].` prefix) for dynamic, admin-controlled properties that flow securely into the `PluginContext`!

### Example Registration Payload

```json
{
  "metadata": {
    "pluginId": "MyCustomPlugin",
    "version": "1.0.0",
    "checksum": "SHA256:...",
    "storagePath": "/path/to/repo/MyCustomPlugin.jar",
    "status": "ACTIVE",
    "targetClusterRole": "WORKER"
  },
  "manifest": {
    "name": "MyCustomPlugin",
    "class": "com.example.plugins.MyCustomPlugin",
    "description": "An example plugin configuration payload",
    "version": "1.0.0",
    "author": "Example Author",
    "thread-safe": true,
    "hot-deploy": true,
    "auto-start": true,
    "security-perimeter": {
      "isolation": true
    },
    "methods": [
      {
        "name": "execute",
        "description": "Executes the main logic",
        "return": "java.lang.String",
        "required-roles": ["admin"],
        "parameters": [
          {
            "name": "data",
            "description": "Input data",
            "type": "java.lang.String",
            "isRequired": true
          }
        ]
      }
    ]
  }
}
```
