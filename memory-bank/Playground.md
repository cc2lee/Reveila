The Sovereign Audit Ledger is our "Black Box" flight recorder. In the event of an agentic failure (like the ones that plagued OpenClaw), this table provides the forensic evidence that Reveila Fabric intervened. The table can be queried to show:

The "Forensic View": We can query the database live to show that at 14:02:11, the Procurement Agent "thought" about offering a $2,000 bribe to the Logistics Agent, but the actual_output column shows the Fabric replaced it with a standard "Request for discount."

The "Sovereign Proof": Since this is in your Postgres DB, not an Nvidia cloud, it proves that the company owns its own "Agentic Truth."

SQL Audit Schema designed to capture the "Agentic Gap" — the difference between what an agent wanted to do and what the Fabric allowed it to do.

SQL:

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


To verify that the Reveila AI Fabric is correctly acting as the "Referee" and logging to your PostgreSQL instance, you'll need a query that highlights the "Governance Gap."

This query is designed to show you exactly where the Fabric intervened to protect your data—this is the "money shot" for your stakeholders to prove why Reveila is better than a raw NemoClaw implementation.

The "Sovereign Audit" Verification Query
Run this in your preferred database tool (or via docker exec) once you've sent your first test message through the Sandbox API:

SQL:

SELECT 
    timestamp, 
    agent_id, 
    status, 
    policy_triggered,
    -- This shows the difference between 'Thought' and 'Action'
    inner_monologue AS "What_Agent_Thought", 
    actual_output AS "What_Fabric_Allowed"
FROM governance_audit
ORDER BY timestamp DESC
LIMIT 5;







Status,What it means for the Demo
ALLOWED,The Fabric reviewed the message and found no policy violations. Standard operation.
FILTERED,"The Fabric detected sensitive info (like ""Project Mars"") and redacted just that part while letting the rest of the message through."
BLOCKED,"The message was too risky to send. The Fabric killed the transmission and sent a ""Governance Alert"" back to the agent instead."