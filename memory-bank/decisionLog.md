# Decision Log

## 2026-11-22 — JSON-Driven Multi-Brand App Theme Configuration (KMP)
- **Context:** Manufacturing enterprise white-label apps at speed and scale requires aligning app visuals (backgrounds, pill buttons in enabled/selected/disabled states, fonts, and text/brand colors) to each customer's corporate standards without editing UI code.
- **Decision:** Store theme presets and light/dark palettes in a shared Compose Multiplatform resource (`composeResources/files/app_theme.json`), parsed via `kotlinx.serialization` with safe fallback defaults (`AeginThemeConfigLoader`), exposed via `LocalAeginTheme` + `LocalAeginColors` + Material 3 `ColorScheme`/`Typography`, and consumed by reusable components such as `AeginPillButton`.
- **Reference:** `memory-bank/App Theme Configuration.md`


