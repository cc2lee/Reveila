# ADR 0010: Scaling Plugins via Sovereign Registry

## Status
**ACCEPTED** | March 15, 2026

## Context
As the Reveila AI Fabric moves from a single-node prototype to a distributed cluster environment, the original method of manual JSON configuration for plugins and settings has become a bottleneck. We need a way to dynamically register agents, manage their lifecycle across multiple nodes, and ensure real-time configuration consistency without system restarts.

## Decision
We have implemented a **Sovereign Plugin Registry** architecture that moves the source of truth from local files to a centralized PostgreSQL ledger, supported by a reactive synchronization layer.

### 🛠️ Key Architectural Components:

#### 1. Manifest-First Discovery
Plugins are now self-describing. Every agent (JAR or folder) must contain a `reveila-plugin.json` manifest.
- **Service:** [`PluginScannerService.java`](../../reveila/core/src/main/java/com/reveila/system/PluginScannerService.java)
- **Benefit:** Allows for "Plug-and-Play" agent deployment. Creators bundle their own definitions (version, capabilities, main class), which are automatically ingested by the Fabric.

#### 2. The Sovereign Ledger (PostgreSQL)
Instead of local files, authorized plugins and global settings are stored in the database.
- **Tables:** `plugin_registry` and `global_settings` (Defined in [`schema.sql`](../../system-home/standard/bin/sql/schema.sql)).
- **Benefit:** Ensures all nodes in the cluster have a unified view of what agents are allowed to run and what the current governance policies are.

#### 3. Reactive Cluster Synchronization (The "Pulse")
To ensure sub-second consistency across the cluster, we utilized the PostgreSQL **NOTIFY/LISTEN** mechanism.
- **The Trigger:** Database-level triggers broadcast a "Pulse" on the `reveila_config_updates` channel whenever a registry or setting table is modified.
- **The Listener:** [`ClusterSyncService.java`](../../spring/core/src/main/java/com/reveila/spring/service/ClusterSyncService.java) maintains a persistent connection and listens for these pulses.
- **The Action:** Upon receiving a pulse, every node in the cluster automatically triggers a `reloadProperties()` event, pulling the latest state from the database.

#### 4. Unified Dashboard Management
The CISO Dashboard has been upgraded with a dynamic Settings interface.
- **Localization:** UI strings are externalized in [`text.en.properties`](../../system-home/standard/resources/ui/en/text.en.properties), allowing for user-friendly configuration.
- **Real-time Control:** Saving a setting in the UI instantly propagates the change to all running instances in the fabric.

## Strategic Advantages
- **Hot-Loading:** New agents can be registered and loaded without a system restart.
- **Version Control:** Supports hosting multiple versions of the same agent on different nodes via targeted cluster roles.
- **Security:** Every plugin must be registered in the Sovereign Ledger with a valid checksum, preventing the execution of unauthorized or tampered binaries.
- **Elasticity:** New nodes joining the cluster automatically sync with the latest global state upon startup.

## Implementation Notes
- **Local Development:** The "Hybrid Mode" allows developers to run the Fabric locally while using the same Dockerized Postgres registry used in production.
- **Storage:** Plugin JARs are stored in a centralized repository (defined by `plugin.repository.path`) and unpacked into a local execution folder upon ingestion.
