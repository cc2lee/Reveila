Generic Dynamic UI Configuration System. Each table in your application can now have its own independent layout defined in a JSON file.

Key Features of the Generic System:
Per-Table Configuration: UI layout files are now stored in system-home/standard/configs/ui/.
The Audit table is configured by: governance_audit.json.
Future tables can be added simply by creating a JSON file matching their tableId.
Strict Data & View Control:
displayColumns: Only columns in this list will be displayed, in the exact order specified.
excludeColumns: These columns are hidden from the UI entirely.
Intelligent Fallbacks: If a configuration file for a specific table is missing, the system automatically falls back to a safe default view (showing Risk Score and Timestamp) to prevent crashes or blank pages.
Current Audit Table Config (governance_audit.json):
{
    "table": {
        "displayColumns": ["riskScore", "timestamp", "agentId", "proposedAction", "status", "policyTriggered", "modelUsed", "id"],
        "excludeColumns": ["sessionId", "metadata", "password", "secret_key"]
    }
}