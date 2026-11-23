package com.reveila.aegin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AeginThemeConfigTest {

    @Test
    fun parseHexColor_handlesRgbRrggbbAarrggbbAndFallbacks() {
        val fallback = Color(0xFF123456)
        assertEquals(Color(0xFFFF00AA), AeginThemeConfigLoader.parseHexColor("#F0A", fallback))
        assertEquals(Color(0xFF2563EB), AeginThemeConfigLoader.parseHexColor("#2563EB", fallback))
        assertEquals(Color(0x802563EB), AeginThemeConfigLoader.parseHexColor("#802563eb", fallback))
        assertEquals(Color(0xFF10B981), AeginThemeConfigLoader.parseHexColor("10B981", fallback))
        assertEquals(fallback, AeginThemeConfigLoader.parseHexColor("not-a-color", fallback))
        assertEquals(fallback, AeginThemeConfigLoader.parseHexColor(null, fallback))
        assertEquals(fallback, AeginThemeConfigLoader.parseHexColor("", fallback))
    }

    @Test
    fun parseJson_loadsMultiplePresetsWithLightAndDarkModes() {
        val sampleJson = """
            {
              "schemaVersion": 1,
              "defaultPresetId": "acme-corp",
              "defaultMode": "light",
              "presets": [
                {
                  "id": "aegin-default",
                  "displayName": "Aegin Default"
                },
                {
                  "id": "acme-corp",
                  "displayName": "Acme Corp",
                  "description": "Acme corporate standard",
                  "typography": {
                    "fontFamily": "serif",
                    "titleWeight": "semibold",
                    "bodySizeSp": 15,
                    "labelSizeSp": 12
                  },
                  "pillButton": {
                    "cornerRadiusDp": 24,
                    "borderWidthDp": 1.5,
                    "horizontalPaddingDp": 14,
                    "verticalPaddingDp": 8
                  },
                  "dark": {
                    "background": "#0A1020",
                    "textPrimary": "#F5F7FA",
                    "primary": "#1E40AF",
                    "pillButton": {
                      "enabledBackground": "#16203A",
                      "enabledText": "#FFFFFF",
                      "disabledBackground": "#6616203A",
                      "disabledText": "#6694A3B8"
                    }
                  },
                  "light": {
                    "background": "#F9FAFB",
                    "textPrimary": "#111827",
                    "primary": "#1D4ED8",
                    "pillButton": {
                      "enabledBackground": "#FFFFFF",
                      "enabledText": "#111827",
                      "disabledBackground": "#E5E7EB",
                      "disabledText": "#9CA3AF"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val catalog = AeginThemeConfigLoader.parseJson(sampleJson)
        assertEquals("acme-corp", catalog.defaultPresetId)
        assertFalse(catalog.defaultIsDark)
        assertEquals(2, catalog.presets.size)

        val lightTheme = catalog.resolve(presetId = "acme-corp", isDark = false)
        assertEquals("Acme Corp", lightTheme.displayName)
        assertFalse(lightTheme.isDark)
        assertEquals(Color(0xFFF9FAFB), lightTheme.colors.bg)
        assertEquals(Color(0xFF111827), lightTheme.colors.text)
        assertEquals(Color(0xFF1D4ED8), lightTheme.colors.primary)
        assertEquals(Color(0xFFFFFFFF), lightTheme.pillButton.enabledBackground)
        assertEquals(Color(0xFFE5E7EB), lightTheme.pillButton.disabledBackground)
        assertEquals(Color(0xFF9CA3AF), lightTheme.pillButton.disabledText)
        assertEquals(24.dp, lightTheme.pillButton.cornerRadius)
        assertEquals(1.5.dp, lightTheme.pillButton.borderWidth)
        assertEquals(FontFamily.Serif, lightTheme.typography.fontFamily)
        assertEquals(FontWeight.SemiBold, lightTheme.typography.titleWeight)
        assertEquals(15.sp, lightTheme.typography.bodySize)
        assertEquals(12.sp, lightTheme.typography.labelSize)

        val darkTheme = catalog.resolve(presetId = "acme-corp", isDark = true)
        assertTrue(darkTheme.isDark)
        assertEquals(Color(0xFF0A1020), darkTheme.colors.bg)
        assertEquals(Color(0xFF16203A), darkTheme.pillButton.enabledBackground)
        assertEquals(Color(0x6616203A), darkTheme.pillButton.disabledBackground)
    }

    @Test
    fun parseJson_partialEnterpriseOverrideInheritsSafeDefaults() {
        val minimalOverride = """
            {
              "defaultPresetId": "minimal-brand",
              "presets": [
                {
                  "id": "minimal-brand",
                  "dark": {
                    "background": "#001122",
                    "primary": "#00AA88"
                  }
                }
              ]
            }
        """.trimIndent()

        val catalog = AeginThemeConfigLoader.parseJson(minimalOverride)
        val resolved = catalog.resolve(presetId = "minimal-brand", isDark = true)

        assertEquals(Color(0xFF001122), resolved.colors.bg)
        assertEquals(Color(0xFF00AA88), resolved.colors.primary)
        // Unspecified fields safely inherit DarkAeginColors defaults
        assertEquals(DarkAeginColors.text, resolved.colors.text)
        assertEquals(DarkAeginColors.card, resolved.colors.card)
        assertEquals(FontFamily.SansSerif, resolved.typography.fontFamily)
    }

    @Test
    fun parseJson_malformedOrEmptyInputFallsBackToDefaultCatalog() {
        val malformed = AeginThemeConfigLoader.parseJson("{ invalid json")
        assertEquals(DEFAULT_PRESET_ID, malformed.defaultPresetId)
        assertTrue(malformed.defaultIsDark)
        assertEquals(DarkAeginColors.bg, malformed.resolve(isDark = true).colors.bg)
        assertEquals(LightAeginColors.bg, malformed.resolve(isDark = false).colors.bg)
    }
}
