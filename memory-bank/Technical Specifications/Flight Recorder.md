# 🏛️ Technical Specification: Reveila Flight Recorder

The **Flight Recorder** ensures that every autonomous action is traceable, providing the transparency required for M&A due diligence, healthcare compliance, and forensic auditing.

## 1. Audit Log Schema (`flight-log.json`)

This schema captures the context of the agentic "thought process" before a tool is invoked via the **Universal Invocation Bridge**.

| Field | Type | Description |
| :--- | :--- | :--- |
| `timestamp` | ISO8601 | Precise time of the event. |
| `agent_principal_id` | UUID | The identity of the agent session. |
| `intent` | String | The model-generated intent mapped to the action. |
| `reasoning_trace` | Text | The LLM's internal monologue/thought leading to the action. |
| `tool_call` | Object | The specific plugin and arguments used. |
| `outcome` | Enum | `SUCCESS`, `PERIMETER_REJECTED`, or `EXECUTION_ERROR`. |

## JSON Schema

```
{
  "trace_id": "rev-99283-abc",
  "timestamp": "2026-02-10T18:05:00Z",
  "agent_id": "agent-alpha-ops",
  "reasoning_trace": {
    "goal": "Generate quarterly report for Healthfirst region 4.",
    "thought": "I need to access the analytics database to aggregate Q4 claims data.",
    "chosen_tool": "reveila.analytics.sql_runner"
  },
  "invocation_details": {
    "component": "sql_runner",
    "method": "execute_query",
    "parameters_masked": { "query": "SELECT * FROM claims..." }
  },
  "perimeter_check": {
    "status": "ALLOWED",
    "matched_policy": "reveila.analytics.sql_runner.policy",
    "token_issued": "jit-tok-882"
  },
  "outcome": {
    "status": "SUCCESS",
    "execution_time_ms": 450,
    "resource_usage": { "cpu_pct": 12, "mem_mb": 64 }
  }
}
```

## 🛠️ Key Capabilities of the Flight Recorder

### Reasoning Capture
Unlike standard application logs, the Flight Recorder stores the LLM's internal monologue (the "Thought") that led to an action. This is vital for debugging "hallucinations" where an agent attempts to access data or perform actions it shouldn't.

### Asynchronous Streaming
To prevent an agent or a malicious plugin from deleting its own trail, logs are streamed asynchronously to a write-only, immutable data store.
*   **Storage Options:** AWS S3 with Object Lock, Azure Immutable Storage, or a secure, write-only ELK stack.
*   **Security:** Once written, logs cannot be modified or deleted by the execution environment.

### Perimeter Violation Alerts
If the **Universal Invocation Bridge** rejects a call based on **Agency Perimeter** metadata, the Flight Recorder triggers high-priority security events.
*   **Target Audience:** CISO, Enterprise Architects, and Security Operations Center (SOC).
*   **Action:** Immediate notification for review of potential unauthorized autonomous behavior.
