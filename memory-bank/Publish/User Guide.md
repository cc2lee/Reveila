# 📘 Reveila-Suite & Aegin App — User Guide

Welcome to **Reveila-Suite** and the **Aegin App**, your sovereign AI assistant and autonomous task platform. This guide walks you through initial setup, pairing Mobile & Desktop/Server devices, everyday Assistant chat workflows, standing instructions, reminders & recurring tasks, private document analysis, and connecting communication apps (Microsoft Teams, WhatsApp, Slack, Email).

---

## 1. Core Concepts at a Glance

| Feature | What It Does |
| :--- | :--- |
| **Assistant Chat** | Your main natural-language interface. Ask questions, manage reminders/tasks, save standing rules, analyze files, or run tools—all in plain English. |
| **Spaces** | Separate workspaces (e.g., *Main Space*, *Work*, *Finance*, *Personal*). Each Space keeps its own conversation history and optional Space-specific instructions. |
| **Standing Instructions** | Persistent rules the AI remembers on **every turn** until you change or remove them (e.g., *"Never include adult sexual topics"*, *"Always format tasks as a numbered list"*). |
| **Tasks & Reminders** | Background recurring tasks (daily, weekly, hourly, every 15 min) and exact date/time reminders that deliver proactive notifications. |
| **Knowledge Vault** | Private on-device/sovereign document index (PDFs, notes, bills, invoices, reports) that the AI searches and analyzes without leaking your files. |
| **Connected Channels** | Bidirectional integration with **Microsoft Teams**, **WhatsApp**, **Slack**, and **Email**—chat with Reveila from those apps and receive reminders/summaries right back. |
| **Multi-Device Cluster** | Pair your mobile phone(s) with a Desktop or Server **Lead Node** so standing instructions and heavy compute workloads stay in sync while working offline-first. |

---

## 2. Deployment & Setup Options

Reveila uses one unified engine across all setups—start small and grow anytime without migrating data:

### Option A: Standalone (Single Phone or Desktop)
* **Best for:** Getting started immediately on one device with maximum privacy.
* **Default behavior:** `system.standalone=true` (zero LAN broadcasts, zero open ports—safe on public Wi-Fi).
* **Setup:**
  1. Launch the **Aegin** app (Desktop or Android).
  2. Open **Settings → AI Models** to select **On-Device Model** (local GGUF, works offline), **Gemini**, **OpenAI**, or **Auto** (smart routing between local & cloud).
  3. Start chatting in the **Assistant** tab.

### Option B: Multi-Device Cluster (Desktop / Home Lab / Cloud VM Lead + Mobile Edge)
* **Best for:** Keeping your phone light & battery-friendly while offloading 7B–14B local GGUF inference, Knowledge Vault indexing, proactive alerts, and background tasks to a Desktop PC, GPU Tower/Mac Mini, or Cloud VM—while syncing standing instructions across all your devices.

```
┌───────────────────────────────────────────────┐
│        Aegin Desktop / Server (LEAD)          │
│  ComputePower: MEDIUM / HIGH / SUPER          │
│  • Embedded HTTP Invoke Server (:8080)        │
│  • UDP LAN Discovery Responder (:41920)       │
│  • 7B–14B Local GGUF + Private Safety Guard   │
│  • Full Personal Knowledge Vault & Tasks      │
│  • Authoritative Standing-Instruction Hub     │
└──────────────────────▲────────────────────────┘
                       │
      1. Home Wi-Fi: Zero-Config UDP LAN Discovery (0 ms LAN)
      2. Away / 5G : Automatic Failover to Tailscale / Cloud URL
      3. Auth      : X-Reveila-Cluster-Token (256-bit shared secret)
                       │
┌──────────────────────┴────────────────────────┐
│     Aegin Mobile (Citizen EDGE_CLIENT)        │
│  ComputePower: LOW | canElectLeader: false    │
│  • Local DB + Offline-First Standing Rules    │
│  • Graceful local/cloud fallback if Lead is   │
│    asleep (never causes split-brain)          │
└───────────────────────────────────────────────┘
```

#### Networking Options (No Static IP Needed)
* **Same Home / Office Wi-Fi (Zero Setup):** Aegin Desktop runs a built-in UDP LAN discovery responder on port `41920`. When you tap **Scan Wi-Fi** (or reconnect after a DHCP IP change), Aegin Mobile broadcasts `AEGIN_DISCOVER_LEAD` and automatically resolves Desktop's current LAN IP.
* **Away From Home on 5G / External Wi-Fi (Recommended: Tailscale):**
  1. Install **Tailscale** (free for personal use) on both your Desktop PC and Android phone under the same account.
  2. Note your Desktop's private Tailscale IP or MagicDNS name (e.g., `http://100.x.y.z:8080` or `http://my-desktop.tailnet:8080`).
  3. Enter that URL in Aegin Desktop's **Optional Remote URL (Tailscale / Relay)** field before enabling Desktop Lead Mode.
  4. Aegin Mobile tries **Home Wi-Fi LAN first** for lowest latency and automatically fails over to your **Tailscale URL** whenever you are away on 5G.

#### Step-by-Step Cluster Pairing
1. **Host Cluster Lead (Aegin Desktop or Headless Server):**
   * Launch **Aegin Desktop** (`.\gradlew :apps:Aegin:run` or `Aegin.bat`)—or deploy `Reveila Server` (`node.runtime=server`, Spring Boot/Docker) on an always-on machine.
   * Open **Settings → Cluster & Node Connection**.
   * *(Optional, for away-from-home 5G access)* Enter your Tailscale, Relay, or HTTPS Cloud URL in **Optional Remote URL (Tailscale / Relay)**.
   * Click **Host Cluster Lead**. Desktop starts its HTTP server on port `8080`, starts UDP LAN discovery on `41920`, generates a 256-bit `cluster.secret`, and updates `configs/reveila.properties`.
   * Click **Copy** next to the generated **Cluster Join Code**.
   * **Windows Firewall Note (One-Time):** If Windows Defender Firewall blocks incoming LAN connections from your phone, run PowerShell as Administrator to allow TCP `8080` and UDP `41920` on your **Private** network profile:
     ```powershell
     New-NetFirewallRule -DisplayName "Aegin Desktop Cluster HTTP" -Direction Inbound -Protocol TCP -LocalPort 8080 -Profile Private -Action Allow
     New-NetFirewallRule -DisplayName "Aegin Desktop LAN Discovery" -Direction Inbound -Protocol UDP -LocalPort 41920 -Profile Private -Action Allow
     ```
2. **Join Cluster from Aegin Mobile (Android Phone / Tablet):**
   * Open **Aegin Mobile** on the same Wi-Fi network (or on 5G with Tailscale / Cloud URL).
   * Go to **Settings → Cluster & Node Connection**.
   * Paste the **Cluster Join Code** into **Cluster Leader URL or Join Code** *(or tap **Scan Wi-Fi** to auto-discover Desktop's LAN URL)*.
   * Tap **Join Cluster**. Mobile registers as `EDGE_CLIENT` (`canElectLeader=false`, `computePower=LOW`) and status updates to **CLUSTER ACTIVE**.
3. **Disconnecting (Returning to Standalone):**
   * **Mobile:** Open **Settings → Cluster & Node Connection** → tap **Disconnect (Standalone)**.
   * **Desktop:** Tap **Leave Cluster** to stop the embedded HTTP listener and return to `system.standalone=true`.

### Option C: 24/7 Headless Server (Home Lab, Cloud VM, or Enterprise Fleet)
* **Best for:** Always-on availability, public HTTPS webhooks (WhatsApp/Teams/Slack), multi-user/multi-tenant teams, and PostgreSQL storage.
* Configure `system-home/standard/configs/reveila.properties` and launch via Docker (`system-home/standard/infrastructure/docker-compose.standalone.yml`) or Spring Boot (`:spring:core:bootJar`).

#### Cluster Configuration Reference (`configs/reveila.properties`)
When Cluster Mode is enabled, Aegin manages these keys automatically:

| Property | Desktop / Server Lead | Mobile Citizen (`EDGE_CLIENT`) |
| :--- | :--- | :--- |
| `app.edition` | `individual` \| `enterprise` | `individual` \| `enterprise` |
| `node.runtime` | `desktop` (embedded HTTP) \| `server` (Spring Boot) | `mobile` |
| `system.standalone` | `false` *(safe default `true` before joining)* | `false` *(safe default `true` before joining)* |
| `cluster.leader.url` | `http://<lead-lan-ip>:8080` | `http://<lead-lan-ip>:8080` |
| `cluster.leader.fallback.urls` | *(empty)* | `http://<tailscale-or-cloud-url>:8080` *(optional)* |
| `cluster.role` | `GENERAL` / `LEADER` | `EDGE_CLIENT` |
| `cluster.can.elect.leader` | `true` | `false` |
| `cluster.node.compute.power` | `AUTO` *(derived from RAM + detected GPU/VRAM)* | `LOW` |
| `cluster.secret` | `<256-bit shared token>` | `<same 256-bit shared token>` |
| `reveila.tenant.id` | `default-tenant` *(or org tenant ID)* | `<same tenant ID>` |

---

## 3. Using the Assistant Chat

The **Assistant** tab is your universal control plane. You do not need to learn special commands—just write naturally.

### Choosing an AI Mode
* **Auto (Recommended):** Routes private/lightweight queries to your **On-Device Model** and complex reasoning or live web lookups to your configured Cloud LLM.
* **On-Device Model:** 100% local, offline inference.
* **Cloud Provider (Gemini / OpenAI):** Always uses the selected cloud model while still enforcing your local governance and safety rules.

### Organizing with Spaces
* Create Spaces for different areas of your life or work (e.g., *Work*, *Health*, *Taxes*).
* Conversations in *Work* never bleed into *Personal*.
* You can attach Space-specific standing instructions so the agent behaves differently in each Space (e.g., concise bullet points in *Work*, casual tone in *Personal*).

---

## 4. Standing Instructions (Persistent Agent Memory)

Standing instructions stay active across turns, sessions, Spaces, and synced devices until you edit or remove them.

### Managing Instructions in Natural Language
Just tell the Assistant directly—it uses the built-in `manage_instructions` tool:
* **Safety rules:**
  > *"Remember to never include adult sexual topics in your response."*
  > *(Safety rules are enforced both in the system prompt and deterministically by the response validator.)*
* **Formatting & style rules:**
  > *"From now on, always format your response in a numbered list if they're tasks."*
  > *"Remember to keep summaries under 5 bullet points."*
* **View or remove rules:**
  > *"Show me my standing instructions."*
  > *"Forget the numbered list instruction."*

### Scope Hierarchy & Multi-Device Sync
Instructions are strictly isolated per **Tenant** and per **User**, applied in this priority order:
1. **Tenant Rules (`TENANT`)** — Organization-wide compliance/safety policies (can be `locked` by a tenant admin so end-users cannot disable them).
2. **Personal Global Rules (`USER_GLOBAL`)** — Your personal standing rules across all Spaces and devices.
3. **Space Rules (`SPACE`)** — Rules active only inside a specific Space.

When paired in a cluster, adding, editing, or deleting an instruction on any device saves locally immediately and syncs through the **Lead Node** (`POST /api/cluster/instructions/sync` + Cluster Pulse) using Last-Write-Wins + delete tombstones so all your other devices stay up to date.

---

## 5. Recurring Tasks & Exact Date/Time Reminders

You can schedule, inspect, pause, resume, or cancel both **exact date/time reminders** and **recurring autonomous tasks** directly from the Assistant chat (or from connected channels like Teams/WhatsApp).

### Examples You Can Type
* **One-time reminders (exact date/time or relative):**
  * *"Remind me to call Dr. Smith on December 3 at 2:30 PM."*
  * *"Remind me tomorrow at 9 AM to submit my Q4 expense report."*
  * *"Remind me in 30 minutes to check the deployment."*
* **Recurring tasks:**
  * *"Every morning at 8:00 AM, check the latest AI security news and send me a summary."*
  * *"Create a weekly task every Friday at 9 AM to summarize all invoices in my expenses folder."*
  * *"Every hour, monitor my server status."*
* **Managing existing tasks & reminders:**
  * *"List my scheduled tasks and reminders."*
  * *"Pause the daily AI news task."*
  * *"Resume the weekly invoice summary."*
  * *"Cancel the dentist reminder."*

### How Reminders vs. Autonomous Tasks Work
* **Reminders (`REMINDER`):** Wait until the exact scheduled date/time (`nextRunAtMs`) and deliver a proactive alert directly to the Assistant, OS push notifications, and your originating channel (e.g., WhatsApp or Teams)—without wasting an LLM reasoning loop. One-time reminders automatically mark themselves `COMPLETED` after firing.
* **Autonomous Tasks (`AGENT_TASK`):** Run in an isolated sandbox at the scheduled interval, can use approved tools (Knowledge Vault, Web Browser, Enterprise Notify), and pause for **Human-in-the-Loop (HITL)** approval if the task involves a high-risk action.

---

## 6. Knowledge Vault (Private Document Analysis)

The **Knowledge Vault** indexes your local documents so the AI can answer questions, compare files, and perform folder-wide analysis (like expense breakdowns or timelines).

1. Place or import documents into your configured Vault directory (or organize them into subfolders like `expenses/`, `contracts/`, `notes/`).
2. Ask natural-language questions:
   * *"What were my top 5 expenses across all files in my expenses folder?"*
   * *"Compare the renewal terms in `contracts/vendor-a.pdf` and `contracts/vendor-b.pdf`."*
   * *"Save this meeting summary to my Vault under `notes/q4-kickoff.md`."*

---

## 7. Connecting Communication Channels (Teams, WhatsApp, Slack, Email)

Connect your messaging apps so you can **chat with Reveila from anywhere** and receive **reminders, task results, and alerts** right inside those apps.

### Step 1: Configure Outbound Delivery

#### Personal Edition (`app.edition=individual`) — In-App Settings UI
Individual users do **not** edit configuration files directly; all channel settings and tokens are managed inside the Aegin app UI:

1. Open **Settings → Channels & Notifications** in the Aegin app.
2. Select the channel you want to connect (**WhatsApp**, **Microsoft Teams**, **Slack**, **Email**, or **Custom Webhook**) and toggle it **Enabled**.
3. Enter the required fields in the UI:
   * **WhatsApp:** Cloud API Endpoint URL, Access Token (automatically stored encrypted in your local Secret Vault), and your recipient phone number.
   * **Microsoft Teams:** Incoming Webhook URL (stored securely in your Secret Vault).
   * **Slack:** Incoming Webhook URL or Bot Token + target channel (e.g., `#alerts`).
   * **Email:** Email gateway endpoint, API Key, sender address, and recipient email(s).
   * **Minimum Severity:** Choose `INFO` to receive all reminders and task updates, or `WARNING` / `ACTION_REQUIRED` for urgent alerts only.
4. Click **Save**, then click **Test Connection** to confirm delivery immediately.

#### Enterprise Edition (`app.edition=enterprise`) — Admin Config & Secret Vault
In Enterprise deployments, outbound channels are centrally provisioned and locked by IT/Security Administrators (arbitrary `CUSTOM_WEBHOOK` endpoints are restricted to enterprise-approved channels):

1. Provision tokens and webhook URLs in **SecretManager** (never store raw tokens in plaintext config files):
   * `WHATSAPP_ACCESS_TOKEN`
   * `TEAMS_WEBHOOK_URL`
   * `SLACK_WEBHOOK_URL`
   * `EMAIL_GATEWAY_API_KEY`
2. Configure approved channels in `system-home/standard/configs/settings/channels.json`:
   ```json
   {
     "channels": [
       {
         "Id": "whatsapp",
         "Type": "WHATSAPP",
         "Enabled": true,
         "Endpoint": "https://graph.facebook.com/v20.0/<PHONE_NUMBER_ID>/messages",
         "Api.Key": "REF:WHATSAPP_ACCESS_TOKEN",
         "Channel": "<DEFAULT_RECIPIENT_PHONE>",
         "Min.Severity": "INFO"
       },
       {
         "Id": "teams-ops",
         "Type": "MICROSOFT_TEAMS",
         "Enabled": true,
         "Endpoint": "REF:TEAMS_WEBHOOK_URL",
         "Min.Severity": "INFO"
       },
       {
         "Id": "slack-ops",
         "Type": "SLACK",
         "Enabled": true,
         "Endpoint": "REF:SLACK_WEBHOOK_URL",
         "Channel": "#reveila-alerts",
         "Min.Severity": "INFO"
       },
       {
         "Id": "email-digest",
         "Type": "EMAIL",
         "Enabled": false,
         "Endpoint": "https://api.resend.com/emails",
         "Api.Key": "REF:EMAIL_GATEWAY_API_KEY",
         "From": "reveila-alerts@example.com",
         "To": ["ops@example.com"],
         "Min.Severity": "WARNING"
       }
     ]
   }
   ```
3. Verify delivery via **Test Connection** in the admin UI or `AutonomousAgent.testChannel("<channelId>")`.

### Step 2: Connect Inbound Webhooks (Talk to Reveila from External Apps)
Point your app's webhook/bot configuration to your Reveila Lead Node's HTTPS URL (Spring Boot Server or tunnelled/Tailscale Desktop Lead):

| App | How to Connect | Webhook URL |
| :--- | :--- | :--- |
| **Microsoft Teams** | Create a Teams **Outgoing Webhook** (or Bot Framework messaging endpoint) | `POST https://<host>/api/channels/teams/webhook` |
| **WhatsApp Cloud API** | 1. Add `channels.whatsapp.verify.token=<your-token>` to `reveila.properties`<br>2. In Meta Developer Console → WhatsApp → Configuration, enter the URL and verify token | `GET` & `POST https://<host>/api/channels/whatsapp/webhook?autoReply=true` |
| **Slack** | In Slack App → **Event Subscriptions**, enter the Request URL and subscribe to `app_mention` / `message.im` | `POST https://<host>/api/channels/slack/webhook?autoReply=true` |
| **Custom App / Bot** | POST JSON `{"text": "...", "userId": "...", "tenantId": "...", "spaceId": "..."}` | `POST https://<host>/api/channels/custom/webhook` |

> **Tip:** When you create a reminder from WhatsApp or Teams (e.g., *"Remind me at 4 PM to join the standup"*), Reveila automatically remembers the originating channel and conversation so the reminder delivers right back to that same app.

---

## 8. Privacy, Security & Governance Controls

* **Cluster Authentication:** Every cluster RPC requires the `X-Reveila-Cluster-Token` header matching `cluster.secret` (invalid requests are rejected with HTTP `403`). Never expose raw HTTP `8080` directly to the public internet—keep it on your private LAN, Tailscale/WireGuard, or behind TLS.
* **Zero-Trust Secret Vault (`SecretManager`):** API keys, passwords, and channel tokens are encrypted at rest and never exposed in plain text to LLMs.
* **Human-in-the-Loop (HITL) Approvals:** Sensitive tool calls (terminal commands, credential logins, high-risk tasks) pause and display a clear approval card where you can **Approve Once**, **Approve Always**, or **Deny**.
* **Per-Task & Global Kill Switch:** Immediately halt any running autonomous sandbox—or every active task globally—from the Tasks tab or CISO controls.
* **Governance Policy (`governance-policy.json`):** Enforces prompt-injection defense, Data Loss Prevention (DLP) redaction (SSNs, credit cards, private keys, JWTs), and optional dual-model auditing.
* **Flight Recorder:** Maintains a tamper-evident audit trail of prompts, tool executions, approvals, and reminder deliveries.

---

## 9. Quick Troubleshooting

| Issue | What to Check |
| :--- | :--- |
| **Agent didn't follow a rule I mentioned earlier in chat** | Regular chat messages age out of the sliding window. Save it as a **Standing Instruction** (*"Remember to always..."*) so it is injected on every turn. |
| **Reminder didn't fire at the expected time** | Verify the device/server clock and timezone, and confirm the reminder is `ACTIVE` (not `PAUSED`) by asking *"List my reminders"*. |
| **Scan Wi-Fi finds no Desktop Lead** | Confirm both devices are on the same Wi-Fi subnet (guest Wi-Fi often blocks UDP broadcasts) and that Windows Firewall allows TCP `8080` and UDP `41920` on the **Private** network profile. |
| **Desktop Lead is asleep or turned off** | Aegin Mobile automatically detects the Lead is unreachable, runs cleanly in local/cloud fallback mode with its own local DB, and reconnects automatically when Desktop wakes up. |
| **Mobile isn't syncing instructions with Desktop** | Confirm both devices use the same `tenantId` & `userId`, `system.standalone=false`, matching `cluster.secret`, and that the Lead Node is reachable on Wi-Fi or Tailscale. |
| **WhatsApp webhook verification fails (403)** | Ensure the WhatsApp Verify Token configured in **Settings → Channels** (Personal) or `channels.whatsapp.verify.token` in `reveila.properties` (Enterprise) matches the Verify Token in the Meta App Dashboard. |
| **Outbound Teams/Slack/WhatsApp alert didn't arrive** | **Personal:** Open **Settings → Channels & Notifications**, confirm the channel is toggled **Enabled**, check Minimum Severity, and click **Test Connection**.<br>**Enterprise:** Verify `"Enabled": true` in `channels.json`, check `Min.Severity`, and confirm the referenced `REF:<KEY>` exists in `SecretManager`. |
