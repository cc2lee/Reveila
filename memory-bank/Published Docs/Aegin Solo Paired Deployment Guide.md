# Aegin Cluster & Paired Deployment Guide
## Unified Cluster Runtime: Desktop / Home Lab / Cloud VM Lead + Mobile Edge Citizen

This guide walks users through connecting **Aegin Mobile** (Android) as a lightweight **`LOW`-power `EDGE_CLIENT`** node to a **Cluster Lead** — whether that Lead is **Aegin Desktop** on home Wi-Fi, a dedicated **GPU Tower / Mac Mini** in a Home AI Lab, a rented **Cloud VM** running headless `Reveila Server` (Spring Boot), or an **Enterprise Fleet**.

With Cluster Deployment:
- **Your phone stays light & battery-friendly** — no need to store or run a 4 GB+ local LLM on mobile.
- **Your Desktop / Server does the heavy lifting** — GPU/VRAM-validated 7B–14B on-device GGUF inference, private **Safety Guard AI**, full **Knowledge Vault** indexing, proactive **Slack / Teams / Email** alerts, and scheduled background **Tasks**.
- **No static IP required** — automatic zero-config discovery on home Wi-Fi, plus optional seamless away-from-home connectivity over a private mesh VPN (Tailscale/WireGuard) or HTTPS Cloud Server (`node.runtime=server`).

---

## 1. Architecture Overview

```
┌───────────────────────────────────────────────┐
│        Aegin Desktop (Cluster LEAD)           │
│  ComputePower: MEDIUM / HIGH                  │
│  • Embedded HTTP Invoke Server (:8080)        │
│  • UDP LAN Discovery Responder (:41920)       │
│  • 7B–14B Local GGUF + Private Safety Guard   │
│  • Full Personal Knowledge Vault & Tasks      │
└──────────────────────▲────────────────────────┘
                       │
      1. Home Wi-Fi: Zero-Config UDP LAN Discovery (0 ms LAN)
      2. Away / 5G : Automatic Failover to Tailscale / Relay URL
      3. Auth      : X-Reveila-Cluster-Token (256-bit shared secret)
                       │
┌──────────────────────┴────────────────────────┐
│     Aegin Mobile (Citizen EDGE_CLIENT)        │
│  ComputePower: LOW | canElectLeader: false    │
│  • Thin UI + RemoteService -> Desktop Lead    │
│  • Graceful local/cloud fallback if Desktop   │
│    is asleep (never causes split-brain)       │
└───────────────────────────────────────────────┘
```

### How It Works Under the Hood
1. **Preset Lead Self-Detection**: When `system.standalone=false` and `cluster.leader.url` matches Desktop's own IP/port, Desktop immediately starts as `NodeRole.LEAD` without leader lookup.
2. **Compute-Aware Dispatching & Profiles**: Mobile registers via `RemoteService` (`remote.reveila`) declaring `computePower=LOW`, `clusterRole=EDGE_CLIENT`, and `canElectLeader=false`. Desktop records Mobile in its node reference table and serves the `LOW` compute profile (`configs/cluster/profiles/low.json`).
3. **Pre-Flight Library Verification**: Before starting any dynamically assigned component with `"required-libraries"`, the node verifies local JARs against `download.base.url` (checking semantic `version`, `publishedAt` / HTTP `HEAD` `Last-Modified`, and `sha256`) and downloads only if missing or newer.
4. **Mobile Sleep & Offline Safety**: Because `canElectLeader=false`, Android Doze or losing Wi-Fi **never** triggers spurious leader elections or split-brain. Mobile falls back cleanly to local/cloud mode and automatically reconnects when Desktop returns.

---

## 2. Networking Options (No Static IP Needed)

### Scenario A — Same Home / Office Wi-Fi (Zero Setup)
- Aegin Desktop runs a built-in UDP LAN discovery responder on port `41920`.
- When you tap **Scan Wi-Fi** (or reconnect after your router assigns Desktop a new DHCP IP), Aegin Mobile broadcasts `AEGIN_DISCOVER_LEAD` and automatically resolves Desktop's current LAN IP.

### Scenario B — Away From Home on 5G / External Wi-Fi (Recommended: Tailscale)
Home routers sit behind NAT/CGNAT, so exposing port `8080` directly to the public internet is both fragile and insecure. Instead:
1. Install **Tailscale** (free for personal solo use) on both your Desktop PC and Android phone and sign into the same Tailscale account.
2. Note your Desktop's stable private Tailscale IP or MagicDNS name (for example, `http://100.x.y.z:8080` or `http://my-desktop.tailnet:8080`).
3. Enter that URL in Aegin Desktop's **Optional Remote URL (Tailscale / Relay)** field before enabling Desktop Lead Mode.
4. Aegin Mobile will always try **Home Wi-Fi LAN first** for lowest latency and automatically fail over to your **Tailscale URL** whenever you are away on 5G.

---

## 3. Step-by-Step Setup

### Step 1: Host Cluster Lead (Aegin Desktop or Headless Server)
1. Launch **Aegin Desktop** (`.\gradlew :apps:Aegin:run` or `Aegin.bat`) — or deploy `Reveila Server` (`node.runtime=server`, Spring Boot/Docker) on an always-on GPU tower, Mac Mini, or Cloud VM.
2. Open **Settings → Cluster & Node Connection**.
3. *(Optional, for away-from-home 5G access)* Enter your Tailscale, Relay, or HTTPS Cloud URL in **Optional Remote URL (Tailscale / Relay)** (e.g., `http://100.x.y.z:8080`).
4. Click **Host Cluster Lead**.
   - Aegin Desktop starts its embedded HTTP cluster server on port `8080`, starts the LAN discovery responder on UDP `41920`, generates a 256-bit `cluster.secret`, and updates `configs/reveila.properties`.
5. Click **Copy** next to the generated **Cluster Join Code** (or highlight and copy the JSON directly).

#### Windows Firewall Note (One-Time)
If Windows Defender Firewall blocks incoming LAN connections from your phone, allow TCP `8080` and UDP `41920` on your **Private** network profile (run PowerShell as Administrator):
```powershell
New-NetFirewallRule -DisplayName "Aegin Desktop Cluster HTTP" -Direction Inbound -Protocol TCP -LocalPort 8080 -Profile Private -Action Allow
New-NetFirewallRule -DisplayName "Aegin Desktop LAN Discovery" -Direction Inbound -Protocol UDP -LocalPort 41920 -Profile Private -Action Allow
```

### Step 2: Join Cluster from Aegin Mobile (Android Phone / Tablet)
1. Open **Aegin Mobile** on the same Wi-Fi network as your Desktop (or on 5G with Tailscale / Cloud Server URL).
2. Go to **Settings → Cluster & Node Connection**.
3. Paste the **Cluster Join Code** copied from Desktop/Server into **Cluster Leader URL or Join Code** *(or tap **Scan Wi-Fi** to auto-discover Desktop's LAN URL)*.
4. Tap **Join Cluster**.
   - Aegin Mobile saves `system.standalone=false`, `cluster.role=EDGE_CLIENT`, `cluster.can.elect.leader=false`, `cluster.node.compute.power=LOW`, and registers with the Cluster Lead via `RemoteService`.
   - Status updates to **CLUSTER ACTIVE**.

### Step 3: Disconnecting (Returning to Standalone)
- On **Mobile**: Open **Settings → Cluster & Node Connection** and tap **Disconnect (Standalone)**.
- On **Desktop**: Tap **Leave Cluster** to stop the embedded HTTP listener and return to `system.standalone=true`.

---

## 4. Configuration Reference (`reveila.properties`)

When Cluster Mode is enabled, Aegin manages these keys automatically in `configs/reveila.properties`:

| Property | Desktop / Server Lead | Mobile Citizen (`EDGE_CLIENT`) |
| --- | --- | --- |
| `app.edition` | `individual` \| `enterprise` | `individual` \| `enterprise` |
| `node.runtime` | `desktop` (embedded HTTP) \| `server` (Spring Boot) | `mobile` |
| `system.standalone` | `false` *(safe default `true` before joining)* | `false` *(safe default `true` before joining)* |
| `cluster.leader.url` | `http://<lead-lan-ip>:8080` | `http://<lead-lan-ip>:8080` |
| `cluster.leader.fallback.urls` | *(empty)* | `http://<tailscale-or-cloud-url>:8080` *(optional)* |
| `cluster.role` | `GENERAL` / `LEADER` | `EDGE_CLIENT` |
| `cluster.can.elect.leader` | `true` | `false` |
| `cluster.node.compute.power` | `AUTO` *(derived from RAM + detected GPU/VRAM)* | `LOW` |
| `cluster.secret` | `<256-bit shared token>` | `<same 256-bit shared token>` |
| `download.base.url` | `http://192.168.1.219:8000` | `http://192.168.1.219:8000` |

---

## 5. Security & Troubleshooting

- **Authentication**: Every cluster RPC (`POST /api/components/{name}/invoke`) requires the `X-Reveila-Cluster-Token` header matching `cluster.secret`. Requests without a valid token are rejected with HTTP `403`.
- **Never expose raw HTTP `8080` to the public internet**: Always keep `8080` restricted to your private LAN or encrypted inside Tailscale / WireGuard / TLS relay.
- **Scan Wi-Fi finds nothing**:
  - Confirm both devices are on the same Wi-Fi subnet (guest Wi-Fi networks often block client-to-client UDP broadcasts).
  - Verify the Windows Firewall rules for TCP `8080` and UDP `41920` are enabled for the **Private** network profile.
- **Desktop is asleep / turned off**:
  - Aegin Mobile automatically detects the Lead Node is unreachable, keeps running in local/cloud fallback mode without errors, and reconnects automatically when Desktop wakes up.
