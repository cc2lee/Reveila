-- 1. Enable pgvector if you plan to do 'Memory' search later
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Core Governance Audit Table
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

-- 3. Indexes for the "Sandbox" Dashboard performance
CREATE INDEX IF NOT EXISTS idx_audit_session ON governance_audit(session_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON governance_audit(timestamp DESC);
