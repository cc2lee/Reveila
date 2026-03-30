# 🎬 Reveila-Suite Demo Workflow

This guide provides a professional, "One-Click" workflow for demonstrating the **Reveila AI Fabric**. It is designed to keep your screen recording clean and focused on high-impact events.

## How to Prepare for the Demo:
Stop Stack: docker compose -f docker-compose.standalone.yml down
Build: ./gradlew.bat :web:vue-project:build :spring:core:clean bootJar
Launch: docker compose -f docker-compose.standalone.yml up -d --build --force-recreate
Verify: docker logs reveila-fabric-standalone 2>&1 | Select-String "Ollama"

## Quick Reference:

Run from Reveila-Suite, build the new Jar:
* ./gradlew.bat clean bootJar

For Docker commands, run from `infrastructure` directory:
* Start Docker Desktop
* Build: docker compose -f docker-compose.standalone.yml build --no-cache
* Launch: docker compose -f docker-compose.standalone.yml up -d
* Build & Launch: docker compose -f docker-compose.standalone.yml up -d --build
* Force Recreate: docker compose -f docker-compose.standalone.yml up -d --build --force-recreate
* Verify: docker logs reveila-fabric-standalone 2>&1 | Select-String "Ollama"
* Watch Logs: docker logs -f --tail 0 reveila-fabric-standalone
* View Full Logs: docker logs -f reveila-fabric-standalone
* View Running Docker Containers: docker ps
* Shutdown: docker compose -f docker-compose.standalone.yml down
---

## 🛠️ Phase 1: Preparation (The Director's Stage)

Add these aliases to your PowerShell session to manage the demo with minimal typing.

### 1. Define PowerShell Aliases
Copy and paste this block into your terminal:

```powershell
cd C:\IDE\Projects\Reveila-Suite\system-home\standard\infrastructure

# Reset the Ledger: Wipe audit data for a clean start
function Reset-Reveila { 
    docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "TRUNCATE TABLE governance_audit;"
    Write-Host "Sovereign Ledger Cleared." -ForegroundColor Green
}

# Clear the Stage: Visual reset showing healthy infrastructure
function Clear-Stage {
    Clear-Host
    docker compose -f docker-compose.standalone.yml ps
}

# Watch the Fabric: Live stream of logs filtered for interesting AI events
# Uses --tail 0 to ignore old history for a clean demo start
function Watch-Fabric {
    docker logs -f --tail 0 reveila-fabric-standalone | Select-String "Audit", "Ollama", "Risk", "Sovereign"
}

# Shutdown everything (Full Stack)
function Stop-Reveila {
    docker compose -f docker-compose.standalone.yml down
}
```

### 2. The Director's Checklist
| Stage | Command | Purpose |
| :--- | :--- | :--- |
| **Clean Slate** | `Reset-Reveila` | Removes "ghost" data from previous tests. |
| **Visual Reset** | `Clear-Stage` | Resets the screen to show "Healthy" status. |
| **Live Proof** | `Watch-Fabric` | Keeps a focused stream of events visible. |

---

## 🔍 Phase 2: Environment Verification

Before starting the demo, ensure all components are fully operational.

### 1. Build Reveila JAR (run from project root `Reveila-Suite`):
```powershell
./gradlew.bat clean bootJar
```

### 2. Build & Launch Docker Image (run from `infrastructure` folder):
```powershell
docker compose -f docker-compose.standalone.yml up -d --build
```
### 2. Verify reveila-fabric-standalone is Running
docker ps -a --filter "name=reveila-fabric-standalone"

### 3. Verify Database is Running
Confirm that Hibernate has synchronized the schema:
```powershell
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "\dt"
```
*Look for: `governance_audit` and `audit_log` tables.*

### 4. Verify Ollama AI Service is Running
To see exactly what is happening during startup, run the command without any filters: `docker logs --tail 50 reveila-fabric-standalone`
```powershell
docker logs reveila-fabric-standalone 2>&1 | Select-String "Ollama"
```
*Success: `Started component: OllamaProvider`*

---

## 🚀 Phase 3: The Live Demo Execution

Follow this sequence for a high-impact presentation.

### 1. Setup the Screen
*   **Window A:** PowerShell running `Watch-Fabric`.
*   **Window B:** Blank PowerShell window for triggering requests.

### 2. Trigger a "Sovereign" Audit Request
Simulate an agent attempting a high-risk action.

**Option A: Using curl.exe (Recommended for consistency)**
```powershell
curl.exe -X POST http://localhost:8080/api/governance/audit `
  -H "Content-Type: application/json" `
  -d '{
    "agentId": "gtc-demo-agent",
    "sessionId": "7a3b1c9e-550e-41d4-a716-446655440000",
    "proposedAction": "QUERY_TENANT_ENCRYPTION_KEYS",
    "innerMonologue": "Attempting to access low-level encryption keys to map network topology.",
    "riskScore": 0.98,
    "metadata": "{\"protocol_version\": \"1.2\", \"source\": \"gtc-demo\"}"
  }'
```

**Option B: Using Native PowerShell (Invoke-RestMethod)**
```powershell
$body = @{
    agentId = "gtc-demo-agent"
    sessionId = "7a3b1c9e-550e-41d4-a716-446655440000"
    proposedAction = "QUERY_TENANT_ENCRYPTION_KEYS"
    innerMonologue = "Attempting to access low-level encryption keys to map network topology."
    riskScore = 0.98
    metadata = '{"protocol_version": "1.2", "source": "gtc-demo"}'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/governance/audit" -ContentType "application/json" -Body $body
```

### 3. Observe the Intervention
Switch to **Window A**. You should see the `Risk` and `Audit` events captured in real-time as the Fabric analyzes the request.

---

## 📊 Phase 4: The "Executive View" (Impact Analysis)

After the execution, show the results in the permanent ledger. This proves the system isn't just a chatbot, but a governing infrastructure.

### 1. The Intervention Proof
Show that the request was captured and assigned a high risk score:
```powershell
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "SELECT agent_id, proposed_action, status, risk_score FROM governance_audit ORDER BY timestamp DESC LIMIT 1;"
```

### 2. High-Impact Dashboard Query
Show only high-risk interventions sorted by severity:
```powershell
docker exec -it reveila-db-prod psql -U admin -d reveila_db -c "SELECT timestamp, agent_id, proposed_action, risk_score FROM governance_audit WHERE risk_score > 0.80 ORDER BY risk_score DESC;"
```

---

## 🛠️ Appendix: Manual Maintenance

### Resetting for the Next Scene
If you need to re-record a scene, use the "Director" aliases:
```powershell
Reset-Reveila
Clear-Stage
```

### 4. Making the Aliases Permanent
If you want these commands to persist across reboots, add them to your PowerShell profile:
1.  Run `notepad $PROFILE`.
2.  Paste the function definitions from Phase 1.
3.  Save and restart PowerShell.

---

## ⚙️ Appendix: UI Column Mapping Guide

If you wish to reorder or hide columns, edit the configuration file at:
`system-home/standard/configs/ui/governance_audit.json`

Use the **JSON Key** in the `displayColumns` or `excludeColumns` array.

| UI Display Name | JSON Key |
| :--- | :--- |
| **Risk Score** | `riskScore` |
| **Timestamp** | `timestamp` |
| **Agent ID** | `agentId` |
| **Trace ID** | `traceId` |
| **Session ID** | `sessionId` |
| **Proposed Action** | `proposedAction` |
| **Status** | `status` |
| **Policy Triggered** | `policyTriggered` |
| **Model Used** | `modelUsed` |
| **Inner Monologue** | `innerMonologue` |
| **Actual Output** | `actualOutput` |
| **Record ID** | `id` |
| **Metadata** | `metadata` |





## 🛠️ Phase 4: Shut Down
To shut down the complete demo environment started with the standalone image, run this command from the system-home/standard/infrastructure directory:
```powershell
docker compose -f docker-compose.standalone.yml down
```