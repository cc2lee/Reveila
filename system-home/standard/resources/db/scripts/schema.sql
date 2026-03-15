-- Enable pgvector for 'Memory' search
CREATE EXTENSION IF NOT EXISTS vector;

-- Organizations Table
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

-- Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    s3_folder_id VARCHAR(255),
    org_id UUID REFERENCES organizations(id)
);

-- Governance Audit Table
CREATE TABLE IF NOT EXISTS governance_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    -- Metadata
    agent_id VARCHAR(50) NOT NULL,
    session_id UUID NOT NULL,
    model_used VARCHAR(50), -- e.g., 'llama3', 'gpt-4o'
    
    -- The "Action" Data
    inner_monologue TEXT,    -- The agent's raw 'thought' before filtering
    proposed_action TEXT,    -- The JSON or text the agent tried to send
    actual_output TEXT,      -- What was actually delivered (filtered or blocked)
    
    -- Governance Results
    status VARCHAR(20),      -- 'ALLOWED', 'FILTERED', 'BLOCKED'
    policy_triggered VARCHAR(100), -- Name of the Reveila policy (e.g., 'Data_Leak_Mars')
    risk_score DECIMAL(3,2), -- 0.00 to 1.00 risk assessment
    
    -- Context
    metadata JSONB           -- Flexible field for system stats (tokens, latency, etc.)
);

-- Centralized Plugin Registry
CREATE TABLE IF NOT EXISTS plugin_registry (
    plugin_id VARCHAR(100) PRIMARY KEY,
    version VARCHAR(20) NOT NULL,
    checksum TEXT NOT NULL,          -- To ensure integrity across nodes
    storage_path TEXT NOT NULL,      -- Location in your shared volume or S3
    status VARCHAR(20) DEFAULT 'ACTIVE',
    target_cluster_role VARCHAR(50)  -- e.g., 'high-cpu-node' or 'audit-node'
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_audit_session ON governance_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON governance_audit(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_plugin_registry_status ON plugin_registry(status);
CREATE INDEX IF NOT EXISTS idx_users_org ON users(org_id);
