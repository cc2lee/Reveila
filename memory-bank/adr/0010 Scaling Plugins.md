# Scaling Plugins

### **The "Discovery" Hybrid Approach**

#### **1. Move Metadata into the Jar/Plugin**

Instead of a central JSON file, have the plugin "self-describe."

* Include a `reveila-manifest.json` inside the plugin's JAR or folder.
* When the instance kicks off, it scans the `/plugins` directory, reads these manifests, and registers the agents.
* **Why:** This allows the "Open Platform" creators to bundle their own definitions. You don't have to touch a central file every time someone uploads a new agent.

#### **2. Use the JSON Files for "Overrides" Only**

Keep the current JSON merging logic, but use it as a **Governance Layer**.

* **Default Behavior:** Scan `/plugins` and start everything.
* **The JSON File:** Only contains entries for plugins that need *special* treatment (e.g., "This specific agent needs 4GB of RAM" or "This agent is temporarily disabled").
* **Result:** Your `components.json` stays tiny, even with 10,000 plugins.

---

### **Clustering & State: The Registry Pattern**

Since you are running in clusters, "Scanning a Folder" has one major risk: **Desynchronization.**
If Node A has Plugin X, but Node B doesn't, your "Agent-to-Agent" interaction will fail.

#### **The "Sovereign Registry" Solution**

Instead of just scanning local files, have your instances sync with your **PostgreSQL** database:

1. **Deployment:** A user uploads a plugin.
2. **Persistence:** The plugin metadata is saved to a `plugin_registry` table in the DB.
3. **Bootstrap:** When a Reveila instance starts, it queries the DB: *"Which plugins are assigned to me?"*
4. **Download:** The instance pulls the necessary binaries from a shared volume (or S3-style storage) and starts them.

---

### **Comparison: Configuration vs. Scanning**

| Feature | Centralized JSON (Current) | Automated Scanning (Proposed) |
| --- | --- | --- |
| **Maintenance** | High (Manual updates) | Low (Plug-and-play) |
| **Governance** | Strict (You approve every line) | Dynamic (Needs automated validation) |
| **Clustering** | Difficult (Syncing files) | Easier (If using a central Registry/DB) |
| **Developer UX** | Friction-heavy | Seamless |

---

### **"Convention over Configuration"**

For an open platform, **default to Scanning.** It removes the friction for creators.

However, to keep the "Sovereign" control, implement a **Plugin Validator**. Before a scanned plugin is allowed to start, the Fabric should:

1. Verify the digital signature of the plugin.
2. Check the manifest against your **Sovereign Policy** (e.g., "Does this agent ask for unauthorized network access?").


### The Plugin Scanner

Build a PluginScanner to shift from "Manual Configuration" to "Autonomous Discovery", which turns your Reveila Fabric into a micro-kernel that can dynamically expand its capabilities.

To implement this without breaking the existing system, we’ll use a "Manifest-First" approach. Each plugin will carry its own identity, and the Java service will "ingest" it into the memory space the current components JSON files occupy.

1. The Plugin Manifest (reveila-plugin.json)
Every plugin (whether it's a JAR or a folder) should contain this file in its root. This allows the plugin creator to define their own metadata.

{
  "id": "weather-agent-001",
  "name": "WeatherInsight",
  "version": "1.0.0",
  "mainClass": "com.reveila.plugins.weather.WeatherAgent",
  "capabilities": ["REST_API", "DATA_STORAGE"],
  "defaultConfig": {
    "updateFrequency": "1h"
  }
}

2. The Java PluginScannerService
This service will run during the Bootstrap phase of the Fabric. It mimics the logic of your current JSON loader but sources the data from the filesystem.

@Service
public class PluginScannerService {

    private final String pluginDir = "system-home/standard/plugins";
    private final ComponentRegistry registry; // Your existing registry

    public void scanAndRegister() {
        File folder = new File(pluginDir);
        File[] plugins = folder.listFiles((dir, name) -> name.endsWith(".jar") || new File(dir, name).isDirectory());

        if (plugins != null) {
            for (File plugin : plugins) {
                ComponentDefinition def = loadManifest(plugin);
                if (isValid(def)) {
                    registry.register(def);
                    log.info("Sovereign Plugin Discovered: {} v{}", def.getName(), def.getVersion());
                }
            }
        }
    }

    private ComponentDefinition loadManifest(File plugin) {
        // Logic to extract reveila-plugin.json from JAR or folder
        // and map it to your existing ComponentDefinition class
    }
}

## The Sovereign Plugin Registry Pattern
Instead of the instances being the "source of truth," we move that responsibility to your PostgreSQL database.

1. The plugin_registry Table
This table acts as the master manifest for the entire cluster.

CREATE TABLE plugin_registry (
    plugin_id VARCHAR(100) PRIMARY KEY,
    version VARCHAR(20) NOT NULL,
    checksum TEXT NOT NULL,          -- To ensure integrity across nodes
    storage_path TEXT NOT NULL,      -- Location in your shared volume or S3
    status VARCHAR(20) DEFAULT 'ACTIVE',
    target_cluster_role VARCHAR(50)  -- e.g., 'high-cpu-node' or 'audit-node'
);

2. The Cluster Synchronization Flow
*   Registration: When a new agent is uploaded to the platform, the metadata is written to plugin_registry.
*   Heartbeat/Sync: Each Reveila instance periodically polls the DB (or listens via a Postgres NOTIFY) for changes.
*   Download & Load: If a node sees a new plugin_id assigned to its role, it downloads the binary from the storage_path, verifies the checksum, and calls the PluginScannerService to hot-load it.

## Strategic Advantages for a "Cluster" Environment
By moving to this model, you solve several scaling issues:

Hot-Loading: You can implement a WatchService that monitors the /plugins folder. When a new file is dropped in, the Fabric can load it without restarting the cluster node.

Version Pinning: Since the manifest is inside the plugin, you can host multiple versions of the same agent (e.g., WeatherInsight v1 and v2) on different nodes of the cluster without configuration conflicts.

Decentralized Maintenance: As an Enterprise Architect, you no longer need to manage a 50,000-line components.json. Your job shifts to managing the Plugin Lifecycle State in the database (e.g., "Active", "Deprecated", "Quarantined").

### Why this scales to thousands of agents
Resource Optimization: You don't have to load all 1,000 plugins on every node. You can use the target_cluster_role to tell your Montgomery cluster to only load "Audit Agents," while your Georgia cluster loads "Processing Agents."

Version Control: You can perform "Canary Deploys" by telling only 5% of your instances to load v2.0 of a plugin while the rest stay on v1.5.

Security: Because every plugin must be in the plugin_registry with a valid checksum, an attacker can't just drop a malicious JAR into a node's folder—the Fabric will refuse to load anything not registered in the Sovereign Ledger.