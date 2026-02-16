# [ADR-0004]: Plugin Metadata and Manifest Standard

* **Status:** Accepted
* **Deciders:** Charles Lee
* **Date:** 2026-01-18
* **Tags:** #architecture #manifest #json #metadata

## 1. Context and Problem Statement
As the number of plugins in the Reveila-Suite grows, the system needs a way to identify plugin capabilities (e.g., "Does this plugin have a UI?", "What version of the Common-API does it require?") without instantiating the classes. Hardcoding this into the `Proxy` is not scalable.

## 2. Decision Drivers
* **Discovery:** The system should be able to list available plugins and their descriptions in a dashboard.
* **Compatibility:** Prevent loading a plugin if its required API version is higher than the host's version.
* **Flexibility:** Support for custom attributes like `author`, `license`, and `category`.

## 3. Decision Outcome
Chosen option: **JSON-based Manifest (`plugin.json`)**. Every plugin JAR or directory must include a `plugin.json` file at the root.

### 3.1 Standard Schema
```json
{
  "id": "com.reveila.plugin.reporting",
  "version": "1.2.0",
  "api-version": "2.0",
  "displayName": "Enterprise Reporting Tool",
  "description": "Generates PDF reports for system metrics.",
  "author": "Charles Lee",
  "mainClass": "com.reveila.plugin.ReportingImpl",
  "capabilities": ["UI", "NETWORK", "FILESYSTEM"]
}