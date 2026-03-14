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
