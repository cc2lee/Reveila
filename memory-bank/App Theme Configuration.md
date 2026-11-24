# App Theme Configuration Guide (White-Label & Enterprise Branding)

This guide explains how to configure, override, and extend the JSON-driven theme system used in Kotlin Multiplatform Compose apps (starting with **Aegin**) so enterprise corporate branding can be applied at speed and scale without modifying UI code.

---

## 1. Source of Truth

- **Config file:** `apps/Aegin/src/commonMain/composeResources/files/app_theme.json`
- **Loader & models:** `apps/Aegin/src/commonMain/kotlin/com/reveila/aegin/ui/theme/AeginThemeConfig.kt`
- **Core palette & Compose locals:** `apps/Aegin/src/commonMain/kotlin/com/reveila/aegin/ui/theme/AeginTheme.kt`
- **Themed pill button:** `apps/Aegin/src/commonMain/kotlin/com/reveila/aegin/ui/theme/AeginPillButton.kt`
- **App wiring:** `apps/Aegin/src/commonMain/kotlin/com/reveila/aegin/ui/AeginApp.kt`

Because the file lives in Compose Multiplatform `composeResources/files/`, the same single JSON file works across **Android, Desktop (Windows/macOS), iOS, and Web (WasmJS)**.

---

## 2. Out-of-the-Box Brand Presets

`app_theme.json` ships with 4 presets, each with full **dark** and **light** mode palettes:

| Preset ID | Display Name | Font Family | Best For |
|---|---|---|---|
| `aegin-default` | Aegin Default | `sans-serif` | Default Reveila obsidian & ember style |
| `enterprise-cobalt` | Enterprise Cobalt | `sans-serif` | Corporate B2B, banking, consulting |
| `emerald-fintech` | Emerald Trust | `sans-serif` | Fintech, healthcare, compliance |
| `executive-serif` | Executive Serif | `serif` | Legal, executive, editorial |

Users can switch presets and toggle Dark/Light mode live under **Settings → Appearance & Brand Theme**, which also displays a live preview of pill button states (*Enabled*, *Selected*, *Disabled*, *Primary*).

---

## 3. JSON Schema & Token Reference

All fields are optional and fall back safely to the built-in `DarkAeginColors` / `LightAeginColors` defaults. Hex colors support `#RGB`, `#RRGGBB`, and `#AARRGGBB` (case-insensitive, with or without `#`).

```json
{
  "schemaVersion": 1,
  "defaultPresetId": "acme-corp",
  "defaultMode": "dark",
  "presets": [
    {
      "id": "acme-corp",
      "displayName": "Acme Corp",
      "description": "Acme corporate brand standard",
      "typography": {
        "fontFamily": "sans-serif",
        "monoFontFamily": "monospace",
        "titleWeight": "semibold",
        "bodySizeSp": 14,
        "labelSizeSp": 11
      },
      "pillButton": {
        "cornerRadiusDp": 999,
        "borderWidthDp": 1,
        "horizontalPaddingDp": 12,
        "verticalPaddingDp": 6
      },
      "dark": {
        "background": "#08111F",
        "surface": "#0F1D33",
        "surfaceAlt": "#162742",
        "surfaceElevated": "#1E3354",
        "headerBackground": "#08111F",
        "tabBarBackground": "#0C182B",
        "navBarBackground": "#0A1424",
        "inputBackground": "#0D1A2E",
        "border": "#1F3659",
        "glassBorder": "#2960A5FA",
        "textPrimary": "#F1F5F9",
        "textSecondary": "#93B4D8",
        "textMuted": "#6482A3",
        "primary": "#2563EB",
        "primaryEnd": "#3B82F6",
        "onPrimary": "#FFFFFF",
        "accent": "#0EA5E9",
        "accentEnd": "#38BDF8",
        "success": "#10B981",
        "warning": "#F59E0B",
        "danger": "#EF4444",
        "violet": "#6366F1",
        "cyan": "#38BDF8",
        "pillButton": {
          "enabledBackground": "#0F1D33",
          "enabledText": "#F1F5F9",
          "enabledIcon": "#93B4D8",
          "enabledBorder": "#3360A5FA",
          "selectedBackground": "#332563EB",
          "selectedText": "#FFFFFF",
          "selectedIcon": "#60A5FA",
          "selectedBorder": "#3B82F6",
          "disabledBackground": "#800F1D33",
          "disabledText": "#6693B4D8",
          "disabledIcon": "#6693B4D8",
          "disabledBorder": "#1A60A5FA",
          "primaryEnabledBackground": "#2563EB",
          "primaryEnabledBackgroundEnd": "#3B82F6",
          "primaryEnabledText": "#FFFFFF",
          "primaryDisabledBackground": "#661E3354",
          "primaryDisabledText": "#6693B4D8"
        }
      },
      "light": {
        "background": "#F4F7FB",
        "surface": "#FFFFFF",
        "textPrimary": "#0F1E36",
        "primary": "#1D4ED8"
      }
    }
  ]
}
```

### Supported Font & Mascot Values
- `fontFamily` / `monoFontFamily`: `"sans-serif"` (or `"sans"`), `"serif"`, `"monospace"` (or `"mono"`), `"cursive"`, `"default"`
- `titleWeight`: `"light"`, `"normal"`, `"medium"`, `"semibold"`, `"bold"`, `"extrabold"`, `"black"`
- `mascotStyle`:
  - `"robot_dog"` *(default)*: Full Aegin robot-dog animations, paw-pad glow, and ear/chest precision overlays.
  - `"corporate_logo"`: Disables dog-specific overlays and head-tilt hops, rendering only a clean centered outer thinking halo around the swapped brand icon (`src/commonMain/composeResources/drawable/app_icon.png`).

---

## 4. Enterprise White-Label Workflows

### Option A: Build-Time / CI Swap (Recommended for App Manufacturing)
Replace `src/commonMain/composeResources/files/app_theme.json` during CI/packaging for each customer build. A minimal customer file only needs the tokens that differ from defaults:

```json
{
  "defaultPresetId": "customer-brand",
  "defaultMode": "light",
  "presets": [
    {
      "id": "customer-brand",
      "displayName": "Customer Brand",
      "typography": { "fontFamily": "sans-serif" },
      "light": {
        "background": "#FFFFFF",
        "primary": "#0052CC",
        "pillButton": {
          "enabledBackground": "#F4F5F7",
          "enabledText": "#172B4D",
          "disabledBackground": "#EBECF0",
          "disabledText": "#A5ADBA"
        }
      },
      "dark": {
        "background": "#0E1624",
        "primary": "#4C9AFF"
      }
    }
  ]
}
```

### Option B: Runtime Injection
Pass `themeJsonOverride` or `initialPresetId` directly to `AeginApp`:

```kotlin
AeginApp(
    themeJsonOverride = customerThemeJsonString,
    initialPresetId = "customer-brand"
)
```

---

## 5. Consuming Theme Tokens in Compose UI

- **Colors (backward-compatible):** `val colors = LocalAeginColors.current`
- **Full resolved theme (colors + pill button + typography + preset metadata):** `val resolved = LocalAeginTheme.current`
- **Pill buttons:** Use `AeginPillButton` so enabled, selected, disabled, and primary states automatically follow `app_theme.json`:

```kotlin
AeginPillButton(
    text = "Summarize",
    leadingIcon = AeginIconType.SPARKLES,
    enabled = isActionEnabled,
    selected = isSelected,
    variant = AeginPillButtonVariant.SURFACE, // or AeginPillButtonVariant.PRIMARY
    onClick = { /* action */ }
)
```

---

## 6. Verification

Run the KMP theme unit tests after changing schema or defaults:

```powershell
.\gradlew.bat :apps:Aegin:desktopTest --tests "com.reveila.aegin.ui.theme.AeginThemeConfigTest"
```
