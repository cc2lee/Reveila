# ADR 0015: Agent Registration and Identity Management

## Status
Accepted

## Context
As Reveila expands its "Dual-Model Governance" architecture, the concept of "identity" has evolved beyond traditional Java `SystemComponent`s and `PluginComponent`s. We now have various forms of **AI Agents** interacting with the system:
1. **Internal Java Plugins**: Hardcoded plugins that act as agents or tools.
2. **Core System Agents**: Built-in callers like `ui-client` or autonomous background workers.
3. **External Persona Agents**: Persona-only agents defined entirely in JSON (without backing Java code), loaded via flat files or a database.

Previously, `ManagedInvocation` relied on a `MetadataRegistry` that only knew about hardcoded Java plugins via `SystemProxy`. This resulted in `IllegalArgumentException`s when non-Java agents (like `ui-client`) attempted to invoke tools, because they lacked a registered manifest.

## Decision
We establish the `MetadataRegistry.PluginManifest` as the universal **Security Profile and Identity** for *all* agents in the system, regardless of how they are implemented. 

Every caller (Agent) invoking the `ManagedInvocation` bridge MUST have a registered manifest. The `MetadataRegistry` will act as a multi-tier aggregator to ensure all agent types are discovered and enforced.

### Aggregation Tiers
1. **L1 (Memory Cache)**: A fast `ConcurrentHashMap` for real-time `ManagedInvocation` lookups.
2. **L2 (File System)**: Discovered JSON profiles loaded from `configs/agents/*.json` during startup.
3. **L3 (Database Repository)**: Dynamic lookup using the `agent_manifest` database repository for external API-key based agents onboarded at runtime.

### JSON Manifest Format Definition
When defining an agent outside of Java (in `configs/agents/*.json` or the database), the JSON structure must adhere to the following format. This schema maps directly to the `MetadataRegistry.PluginManifest` record.

```json
{
  "plugin_id": "external-support-agent",
  "name": "Customer Support Agent",
  "version": "1.0.0",
  "agency_perimeter": {
    "accessScopes": ["read:kb", "write:ticket"],
    "allowedDomains": ["api.zendesk.com"],
    "internetAccessBlocked": false,
    "maxMemoryMb": 512,
    "maxCpuCores": 1,
    "maxExecutionSec": 30,
    "delegationAllowed": true
  },
  "secret_parameters": ["api_key", "customer_ssn"],
  "masked_parameters": ["customer_ssn", "credit_card_number"],
  "tool_definitions": {
    "type": "object",
    "properties": {
       "create_ticket": {
         "type": "object",
         "description": "Creates a new support ticket",
         "properties": {
           "title": { "type": "string" },
           "description": { "type": "string" }
         }
       }
    }
  }
}
```

## Consequences
- **Positive:** Uniform security enforcement. The bridge (`ManagedInvocation`) no longer cares *how* an agent is implemented; it only cares about the guardrails defined in the `PluginManifest`.
- **Positive:** Enables "No-Code" agent creation. Administrators can drop JSON files into `configs/agents` or insert records into the database to spawn secure, restricted AI personas.
- **Negative:** Slightly increased latency on the first invocation of a database-backed agent (L3 cache miss), though subsequent calls are O(1) from the L1 cache.