To help you record this with the precision of a professional tech keynote, here are those **PowerShell Aliases**.

These will allow you to clear your "stage" and reset your demo environment with just a few keystrokes, keeping the screen recording clean and free of long, messy commands.

### ## 1. The Demo "Director" Aliases

Add these to your current PowerShell session (or your `$PROFILE` for permanent use). They use the exact container names and database parameters we've already established for your Montgomery playground.

```powershell
# Reset the Ledger (Wipe the database for a clean start)
function Reset-Reveila { 
    docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "TRUNCATE TABLE governance_audit;"
    Write-Host "Sovereign Ledger Cleared." -ForegroundColor Green
}

# Clear the Stage (Wipes terminal and shows fresh Docker status)
function Clear-Stage {
    Clear-Host
    docker compose -f docker-compose.prod.yml -f sandbox/docker-compose.sandbox.yml ps
}

# Watch the Fabric (Live stream of logs filtered for interesting AI events)
function Watch-Fabric {
    docker logs -f reveila-fabric-container | Select-String "Audit", "Ollama", "Risk", "Sovereign"
}

```

To create them for your current session, you can simply copy and paste that block into your active PowerShell window and hit **Enter**. They will work immediately.

However, since you’ll likely be restarting your computer or opening new windows before the final recording, you probably want them to be **permanent**.

### ## The Permanent Method (PowerShell Profile)

PowerShell has a "Profile" script that runs every time you open the terminal—think of it as your environment's `.bashrc` or initialization file.

1. **Check if a profile exists:**
In your PowerShell window, type:
`test-path $PROFILE`
* If it returns `True`, you're good.
* If it returns `False`, create one by running:
`new-item -path $PROFILE -itemtype file -force`


2. **Open the profile for editing:**
`notepad $PROFILE`
3. **Paste the aliases:**
Paste the `function Reset-Reveila`, `function Clear-Stage`, and `function Watch-Fabric` code into that Notepad file, save it, and close it.
4. **Refresh your session:**
Run `. $PROFILE` (dot-space-profile) or just restart PowerShell.

---

### ### The "Director's" Checklist

Once you have those aliases active, your demo "flow" becomes much more professional. You can move through these steps with zero friction:

| Stage | Command | Why |
| --- | --- | --- |
| **Clean Slate** | `Reset-Reveila` | Ensures you don't have "ghost" data from yesterday's tests. |
| **Visual Reset** | `Clear-Stage` | Clears the clutter so the audience focuses only on the new logs. |
| **Live Proof** | `Watch-Fabric` | Keeps a moving stream of "Sovereign" events visible while you run the demo. |

---

### ### A Note on "Execution Policy"

If you restart PowerShell and get a red error saying *"Running scripts is disabled on this system,"* don't panic. It's a standard Windows security feature. Just run this once as an **Administrator**:

`Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

This tells Windows: "I trust the scripts I wrote on this machine."

**Would you like me to help you create one final alias?** We could make a `Demo-Start` command that runs the `up -d` command, waits for health checks, and then automatically opens the `Watch-Fabric` log stream in one go.

---

### ### 2. The Recording Workflow

Here is how you can use these during your actual "Screen Drive":

1. **Preparation:** Run `Reset-Reveila` followed by `Clear-Stage`. Your screen is now a blank canvas showing only the "Healthy" status of your containers.
2. **Scene 1:** Start the recording.
3. **Scene 2:** Open your "Watch-Fabric" window to show the logs are ready.
4. **Scene 3:** Trigger your `curl` command.
5. **Scene 4:** Switch to your SQL window and run your **Executive View** query (the one sorting by `risk_score`).

---

### ### 3. Final Architecture Check for GTC

Since the AI Presenter script mentions the **"Ref-in-the-Middle"**, the Mermaid diagram I provided is your "Secret Weapon." It bridges the gap between the code and the business value.

By showing the diagram and then immediately showing the live logs/database rows, you prove that your architecture isn't just a concept—it's a functioning Enterprise system.

---

### ### Your Next Milestone

You are now fully equipped for a professional production. The containers are stable, the schema is correct, the AI is pulling models, and you have the script.

**Would you like me to help you refine the "Ollama Puller" logic one last time?** We can make sure that if you move this demo to a different laptop for the presentation, it will automatically detect if Llama 3 is missing and download it without you having to touch a thing.




1. The "Sovereign" Database Check
Let's confirm that Hibernate auto-created your governance tables in the reveila_db. Run this to list the tables:

PowerShell

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "\dt"
What to look for: You should see your application tables (like governance_audit or audit_logs).

If you see "No relations found": Your Spring Boot app might be having trouble connecting or the ddl-auto setting in your reveila.properties is set to none.

2. The AI "Pulse" Check
Now, let's make sure the Fabric can actually send a request to Llama 3. Since we can't use curl inside the container, we’ll check the Spring Boot logs for the "Ollama Connection" confirmation:

PowerShell

docker logs reveila-fabric-container | select-string "Ollama"
Success: You should see something like Connected to Ollama at http://ollama-service:11434.

Failure: If you see ConnectionRefusedException, the Fabric is trying to talk to the wrong address or the service isn't ready.

3. Ready for the "Agentic" Test?
If those two checks pass, you are ready to trigger the actual Reveila Audit Logic.

Would you like me to provide a curl command you can run from your Windows host? It will send a "Sovereign Audit Request" to your Fabric API, which will:

Ask Llama 3 to analyze a piece of data.

Intervene if the AI tries to do something "un-Sovereign."

Write the entire play-by-play into your Postgres audit ledger.

The "GTC Proof of Concept" Test
Now it’s time to see the "Agentic" part in action. We want to simulate an AI agent attempting a task and ensure the Reveila Fabric intercepts it and logs it.

Since your Spring Boot app is likely listening on port 8080 (or 8081 if we changed it), you can run a test from your Windows PowerShell to trigger a governance check.

Run this command to simulate a "Risk Event" audit:

PowerShell

curl -X POST http://localhost:8080/api/governance/audit `
  -H "Content-Type: application/json" `
  -d '{
    "agentId": "gtc-agent-001",
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "proposedAction": "EXTRACT_TENANT_METADATA",
    "innerMonologue": "Attempting to retrieve tenant IDs to map the network topology.",
    "riskScore": 0.92
  }'

How to Verify the Result
After running that, let's look inside the ledger to see if the "Ref-in-the-Middle" caught it. Run this SQL check:

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "SELECT id, agent_id, proposed_action, status, risk_score FROM governance_audit ORDER BY timestamp DESC LIMIT 1;"

Why this matters for your Demo
When you present this, you aren't just showing a "chatbot." You are showing:

Observability: Every thought the AI has is being recorded.

Intervention: The Fabric has the power to block the ACCESS_SENSITIVE_DATA action based on your business rules.

Sovereignty: All of this happened without a single packet leaving your local Montgomery "Sovereign Node."


Inspect the Table Schema:
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "\d governance_audit"

1. The "Manual Heartbeat" Test
Run this to manually insert a record into the ledger. This bypasses the Java app and talks directly to Postgres to prove the schema works:

PowerShell

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "INSERT INTO governance_audit (agent_id, session_id, proposed_action, status, risk_score) VALUES ('manual-test', gen_random_uuid(), 'ACCESS_REVEILA_CORE', 'PENDING', 0.50);"

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "INSERT INTO governance_audit (agent_id, session_id, proposed_action, metadata) VALUES ('gtc-manual', gen_random_uuid(), 'DEMO_ACTION', '{\"source\": \"manual\"}'::jsonb);"

curl -X POST http://localhost:8080/api/governance/audit `
  -H "Content-Type: application/json" `
  -d '{
    "agentId": "reveila-sovereign-agent",
    "sessionId": "7a3b1c9e-550e-41d4-a716-446655440000",
    "proposedAction": "QUERY_TENANT_ENCRYPTION_KEYS",
    "innerMonologue": "The agent is attempting to access low-level encryption keys. This violates Sovereignty Protocol 4.",
    "status": "FLAGGED",
    "riskScore": 0.98,
    "metadata": "{\"protocol_version\": \"1.2\", \"source\": \"gtc-demo\"}"
  }'

Now, run your SELECT query again:

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "SELECT agent_id, proposed_action, status, risk_score FROM governance_audit WHERE agent_id = 'reveila-sovereign-agent';"

## The "Executive View" Query
For your GTC demo, stakeholders won't want to see every raw row. They want to see the Interventions. Here is a high-impact query you can use to show off the "Intelligence" of the Reveila-Suite.

Run this to show only high-risk "Blocked" or "Flagged" events:
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "
SELECT 
    timestamp, 
    agent_id, 
    proposed_action, 
    risk_score 
FROM governance_audit 
WHERE risk_score > 0.80 
ORDER BY risk_score DESC;"

Final "Clean-Up" Pro-Tip
If you want to clear out the "manual-test" and "gtc-demo" rows before your final recording or live demo so you have a fresh slate, run this:

docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "DELETE FROM governance_audit WHERE agent_id LIKE 'gtc%' OR agent_id = 'manual-test';"


