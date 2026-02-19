# Configuration Requirements & Instructions

This document outlines the strict validation rules and structural requirements for the Reveila Suite configuration, as enforced by the `ConfigurationLinter`.

## 1. Plugin Configuration Rules

When defining a component with a `plugin` block in the configuration files (e.g., `core.json`), the following rules apply:

### 1.1 Directory Pathing
- **Prefix Requirement:** The `directory` property MUST start with `plugins/`.
- **Relative Paths:** All plugin paths MUST be relative to the `system.home` directory.
- **Security Isolation:** Paths are validated to ensure they do not escape the `system.home` directory (e.g., via `..` or absolute paths).
- **Existence Check:** The specified directory MUST exist on the filesystem at startup.

### 1.2 Web Content (`web/` directory)
The `web/` directory within `system.home` is the primary location for serving static web content (e.g., the built Vue.js application). The system prioritizes this location over development-time source paths.

### 1.3 Plugin Manifest (`plugin-manifest.json`)
Every plugin directory MUST contain a `plugin-manifest.json` file at its root. This file is the source of truth for the plugin's capabilities and security perimeter.

#### Standard Manifest Schema (snake_case):
```json
{
  "plugin_id": "unique.plugin.identifier",
  "name": "Display Name",
  "version": "1.0.0",
  "tool_definitions": {
    "toolName": {
      "description": "Description of what the tool does.",
      "parameters": {
        "type": "object",
        "properties": {
          "param1": { "type": "string" }
        },
        "required": ["param1"]
      }
    }
  },
  "agency_perimeter": {
    "access_scopes": ["scope1", "scope2"],
    "allowed_domains": ["api.example.com"],
    "internet_access_blocked": boolean,
    "max_memory_mb": 512,
    "max_cpu_cores": 1,
    "max_execution_sec": 30,
    "delegation_allowed": false
  },
  "secret_parameters": ["param_to_encrypt"],
  "masked_parameters": ["param_to_hide_in_logs"]
}
```

## 2. Component Configuration (`core.json`)

- **Component Naming:** Every component must have a unique `name`.
- **Class Mapping:** The `class` property must point to a valid implementation class.
- **Priority Management:** `start-priority` determines the boot order (lower numbers start first).
- **Dependency Validation:** 
    - All declared `dependencies` must exist.
    - A component cannot start before its dependencies (priority mismatch check).
    - Circular dependencies are strictly forbidden and caught by the `DependencyValidator`.

## 3. Enforcement Mechanism

These rules are programmatically enforced by the `ConfigurationLinter` class during the Reveila boot sequence. If any requirement is not met, a `ConfigurationException` is thrown, and if `strict-mode` is enabled, the system will roll back and fail to start.

## 4. Future Updates

When introducing new configuration properties or architectural constraints, the `ConfigurationLinter` must be updated, and those rules must be documented here to maintain system integrity.
