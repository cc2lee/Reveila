# Decision Log

## 2026-11-22 — JSON-Driven Multi-Brand App Theme Configuration (KMP)
- **Context:** Manufacturing enterprise white-label apps at speed and scale requires aligning app visuals (backgrounds, pill buttons in enabled/selected/disabled states, fonts, and text/brand colors) to each customer's corporate standards without editing UI code.
- **Decision:** Store theme presets and light/dark palettes in a shared Compose Multiplatform resource (`composeResources/files/app_theme.json`), parsed via `kotlinx.serialization` with safe fallback defaults (`AeginThemeConfigLoader`), exposed via `LocalAeginTheme` + `LocalAeginColors` + Material 3 `ColorScheme`/`Typography`, and consumed by reusable components such as `AeginPillButton`.
- **Reference:** `memory-bank/App Theme Configuration.md`

## 2026-11-23 — Unified Cluster Deployment Architecture (Companion, Home Lab, Cloud VM & Enterprise)
- **Context:** Mobile–desktop companion pairing, home Wi-Fi AI labs (GPU towers / Mac Minis), rented personal cloud VMs, and enterprise server fleets all share the same underlying leader/worker/edge coordination problem.
- **Decision:** Unify all topologies under one Cluster Runtime protocol with two independent axes: `app.edition` (`individual` | `enterprise`) for governance/feature policy and `node.runtime` (`mobile` | `desktop` | `server`) for host packaging (lightweight embedded `DesktopClusterHttpServer` on Desktop vs. Spring Boot / Docker for Internet-facing headless servers). Keep `system.standalone=true` as the safe unpaired default.
- **Reference:** `memory-bank/Unified Cluster Deployment Architecture.md`


