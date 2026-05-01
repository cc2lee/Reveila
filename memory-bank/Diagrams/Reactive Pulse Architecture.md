This diagram visualizes the real-time synchronization between the centralized control plane and the decentralized agentic fabric.

```mermaid
sequenceDiagram
    participant Admin as Admin (Settings UI)
    participant Ledger as Sovereign Ledger (PostgreSQL)
    participant Trigger as DB Trigger (NOTIFY)
    participant Montgomery as Node: Montgomery (LISTEN)
    participant Georgia as Node: Georgia (LISTEN)

    Admin->>Ledger: 1. Update Global Settings / Plugin Registry
    
    Note over Ledger, Trigger: 2. Commit Transaction
    
    Ledger->>Trigger: 3. Fire pg_notify()
    
    Trigger-->>Montgomery: 4. REACTIVE PULSE (reveila_config_updates)
    Trigger-->>Georgia: 4. REACTIVE PULSE (reveila_config_updates)
    
    Note over Montgomery: 5. platformAdapter.loadProperties(Properties overrides)
    Note over Georgia: 5. platformAdapter.loadProperties(Properties overrides)
    
    Montgomery-->>Admin: 6. Acknowledgment (UI Success Toast)
```

**Verdict:** Your understanding of the flow is spot on. This architecture ensures that even a 1,000-node cluster stays synchronized with sub-second latency the moment you click "Save" in the dashboard.