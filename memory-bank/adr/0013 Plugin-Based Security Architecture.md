## ADR 0013: Plugin-Based Security Architecture

**Status:** Approved
**Contact:** Enterprise Architect (**Reveila-Suite**)
**Deciders:** AI Development Team, Security Lead
**Date:** 2026-03-20

---

### 1. Context and Problem Statement
To enable the AI Agent to perform actions, it must invoke system components via APIs. However, allowing an LLM-driven agent direct access to core system components (`android.json`, `spring.json`, `default.json`) poses a critical security risk, including potential privilege escalation and unauthorized system-wide changes. We need a mechanism to restrict the agent's "agency" to a safe, audited, and authorized subset of functionality.

### 2. Decision Drivers
* **Security:** Prevent AI Agents from calling sensitive core system methods directly.
* **Portability:** The solution must work on Android (non-Spring environments) and server-side.
* **Granularity:** Authorization must be enforced at the method level, not just the component level.
* **Extensibility:** New capabilities should be added via plugins without modifying the core security engine.

### 3. Proposed Architecture
We will implement a **Broker Pattern** enforced by the existing `Proxy.invoke(...)` and `Reveila.invoke(...)` entry points.

#### A. The "DMZ" (Plugin Layer)
* **Core Restriction:** All components defined in `${system.home}/configs/components` are **reserved for local system use only**.
* Add method Proxy.getManifest() that returns a Manifest object. The Manifest class should implement getComponentType() that returns either "system" / "component" or "plugin".
* SystemContext.getProxy(String name) will enforce this rule based on the Proxy's Manefest, explicitly blocking any external/Agent request targeting these components.
* **Plugin Brokerage:** All Agent interactions must be brokered through "Plugin" components.
* **Extended Manifest:** Each plugin must include an `exposed_methods` list in its manifest, defining exactly which functions are available to the AI.
* By default, all plugins runs with a default role, but can be granted higher or system wide role by the Admin user using a "run-as" configuration.

#### B. The Authorization Matrix
* A separate `access-control.config` will map **User Roles** (from OAuth 2 tokens) to specific **Plugin + Method** combinations. This configuration should be accessible and changes through the settings page.
* The `Proxy` guard will validate the incoming JWT, identify the role, and check the mapping before allowing the call to proceed to `Reveila.invoke()`.

### 4. Technical Specifications

#### **Plugin Manifest Schema**
```json
{
    "plugin": {
        "name": "Auto Generated Unique Name",
        "displayName": "Human Readable Name",
        "version": "1.0.0",
        "description": "This example plugin manifest serves as a template for creating new plugins.",
        "author": "Publisher Name",
        "class": "com.domain.full.ClassName",
        "hot-deploy": true,
        "arguments": [
            {
                "name": "MethodNameWithoutSet",
                "type": "FullClassName",
                "value": "ValueToSet"
            }
        ],
        "methods": [
            {
                "name": "methodName",
                "description": "Describe what this method does. It helps AI Agents understand the purpose of the method.",
                "parameters": [
                    {
                        "name": "parameterName",
                        "description": "What is the purpose of this parameter",
                        "type": "java.lang.String",
                        "isRequired": true,
                        "isSecret": true
                    }
                ],
                "return": "boolean",
                "required-roles": [
                    "admin"
                ]
            }
        ]
    }
}
```

#### **Proxy Guard Logic (Pseudo-code)**
1.  **Intercept** call: `invoke(component, method, params, token)`
2.  **Check Core:** If `component` is a system component, **REJECT** (Local Only).
3.  **Validate Plugin:** Check if `component` is a registered Plugin and if `method` is in its `exposed_methods` list.
4.  **Authorize User:** Extract Role from `token`. Check `access-control.config` if `Role` is permitted for `Plugin.method`.
5.  **Execute:** If all pass, call `Proxy.invoke()`. Otherwise, throw com.reveila.error.SecurityException, or return `403 Forbidden` for HTTP.
