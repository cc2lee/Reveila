package com.reveila.aegin.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import reveila_suite.apps.aegin.generated.resources.Res

/**
 * Root JSON schema for `composeResources/files/app_theme.json`.
 * All fields are optional with safe built-in defaults so enterprise white-label builds
 * can override only the tokens required by a customer's corporate brand guide.
 */
@Serializable
data class AppThemeConfigDto(
    val schemaVersion: Int = 1,
    val defaultPresetId: String = DEFAULT_PRESET_ID,
    val defaultMode: String = "dark",
    val presets: List<BrandThemePresetDto> = emptyList()
)

@Serializable
data class BrandThemePresetDto(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val typography: ThemeTypographyDto? = null,
    val pillButton: PillButtonShapeDto? = null,
    val dark: ThemePaletteDto? = null,
    val light: ThemePaletteDto? = null
)

@Serializable
data class ThemeTypographyDto(
    val fontFamily: String? = null,
    val monoFontFamily: String? = null,
    val titleWeight: String? = null,
    val bodySizeSp: Float? = null,
    val labelSizeSp: Float? = null
)

@Serializable
data class PillButtonShapeDto(
    val cornerRadiusDp: Float? = null,
    val borderWidthDp: Float? = null,
    val horizontalPaddingDp: Float? = null,
    val verticalPaddingDp: Float? = null
)

@Serializable
data class ThemePaletteDto(
    val background: String? = null,
    val surface: String? = null,
    val surfaceAlt: String? = null,
    val surfaceElevated: String? = null,
    val headerBackground: String? = null,
    val tabBarBackground: String? = null,
    val navBarBackground: String? = null,
    val inputBackground: String? = null,
    val border: String? = null,
    val glassBorder: String? = null,
    val textPrimary: String? = null,
    val textSecondary: String? = null,
    val textMuted: String? = null,
    val primary: String? = null,
    val primaryEnd: String? = null,
    val onPrimary: String? = null,
    val accent: String? = null,
    val accentEnd: String? = null,
    val success: String? = null,
    val warning: String? = null,
    val danger: String? = null,
    val violet: String? = null,
    val cyan: String? = null,
    val pillButton: PillButtonPaletteDto? = null
)

@Serializable
data class PillButtonPaletteDto(
    val enabledBackground: String? = null,
    val enabledText: String? = null,
    val enabledIcon: String? = null,
    val enabledBorder: String? = null,
    val selectedBackground: String? = null,
    val selectedText: String? = null,
    val selectedIcon: String? = null,
    val selectedBorder: String? = null,
    val disabledBackground: String? = null,
    val disabledText: String? = null,
    val disabledIcon: String? = null,
    val disabledBorder: String? = null,
    val primaryEnabledBackground: String? = null,
    val primaryEnabledBackgroundEnd: String? = null,
    val primaryEnabledText: String? = null,
    val primaryDisabledBackground: String? = null,
    val primaryDisabledText: String? = null
)

@Immutable
data class AeginPillButtonStyle(
    val enabledBackground: Color,
    val enabledText: Color,
    val enabledIcon: Color,
    val enabledBorder: Color,
    val selectedBackground: Color,
    val selectedText: Color,
    val selectedIcon: Color,
    val selectedBorder: Color,
    val disabledBackground: Color,
    val disabledText: Color,
    val disabledIcon: Color,
    val disabledBorder: Color,
    val primaryEnabledBackground: Color,
    val primaryEnabledBackgroundEnd: Color,
    val primaryEnabledText: Color,
    val primaryDisabledBackground: Color,
    val primaryDisabledText: Color,
    val cornerRadius: Dp = 999.dp,
    val borderWidth: Dp = 1.dp,
    val horizontalPadding: Dp = 10.dp,
    val verticalPadding: Dp = 6.dp
) {
    val primaryEnabledBrush: Brush
        get() = Brush.linearGradient(listOf(primaryEnabledBackground, primaryEnabledBackgroundEnd))
}

@Immutable
data class AeginTypographyTokens(
    val fontFamilyName: String = "sans-serif",
    val fontFamily: FontFamily = FontFamily.SansSerif,
    val monoFontFamily: FontFamily = FontFamily.Monospace,
    val titleWeight: FontWeight = FontWeight.Bold,
    val bodySize: TextUnit = 14.sp,
    val labelSize: TextUnit = 11.sp
) {
    fun toMaterialTypography(): Typography {
        val base = Typography()
        val bodyScale = (bodySize.value / 14f).coerceIn(0.8f, 1.35f)
        fun TextStyle.scaled(weightOverride: FontWeight? = null): TextStyle = copy(
            fontFamily = this@AeginTypographyTokens.fontFamily,
            fontWeight = weightOverride ?: fontWeight,
            fontSize = if (fontSize.isSp) (fontSize.value * bodyScale).sp else fontSize,
            lineHeight = if (lineHeight.isSp) (lineHeight.value * bodyScale).sp else lineHeight
        )

        return Typography(
            displayLarge = base.displayLarge.scaled(titleWeight),
            displayMedium = base.displayMedium.scaled(titleWeight),
            displaySmall = base.displaySmall.scaled(titleWeight),
            headlineLarge = base.headlineLarge.scaled(titleWeight),
            headlineMedium = base.headlineMedium.scaled(titleWeight),
            headlineSmall = base.headlineSmall.scaled(titleWeight),
            titleLarge = base.titleLarge.scaled(titleWeight),
            titleMedium = base.titleMedium.scaled(titleWeight),
            titleSmall = base.titleSmall.scaled(titleWeight),
            bodyLarge = base.bodyLarge.scaled(),
            bodyMedium = base.bodyMedium.scaled(),
            bodySmall = base.bodySmall.scaled(),
            labelLarge = base.labelLarge.scaled(),
            labelMedium = base.labelMedium.scaled(),
            labelSmall = base.labelSmall.copy(
                fontFamily = fontFamily,
                fontSize = labelSize
            )
        )
    }
}

@Immutable
data class ResolvedAeginTheme(
    val presetId: String,
    val displayName: String,
    val description: String,
    val isDark: Boolean,
    val colors: AeginColors,
    val pillButton: AeginPillButtonStyle,
    val typography: AeginTypographyTokens
) {
    fun toMaterialColorScheme(): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                secondary = colors.accent,
                onSecondary = Color.White,
                tertiary = colors.cyan,
                background = colors.bg,
                onBackground = colors.text,
                surface = colors.card,
                onSurface = colors.text,
                surfaceVariant = colors.cardAlt,
                onSurfaceVariant = colors.subText,
                outline = colors.border,
                error = colors.danger,
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                secondary = colors.accent,
                onSecondary = Color.White,
                tertiary = colors.cyan,
                background = colors.bg,
                onBackground = colors.text,
                surface = colors.card,
                onSurface = colors.text,
                surfaceVariant = colors.cardAlt,
                onSurfaceVariant = colors.subText,
                outline = colors.border,
                error = colors.danger,
                onError = Color.White
            )
        }
    }
}

@Immutable
data class AeginBrandPreset(
    val id: String,
    val displayName: String,
    val description: String,
    val dark: ResolvedAeginTheme,
    val light: ResolvedAeginTheme
) {
    fun forMode(isDark: Boolean): ResolvedAeginTheme = if (isDark) dark else light
}

@Immutable
data class AeginThemeCatalog(
    val defaultPresetId: String,
    val defaultIsDark: Boolean,
    val presets: List<AeginBrandPreset>
) {
    fun findPreset(presetId: String?): AeginBrandPreset {
        if (!presetId.isNullOrBlank()) {
            presets.firstOrNull { it.id.equals(presetId, ignoreCase = true) }?.let { return it }
        }
        return presets.firstOrNull { it.id.equals(defaultPresetId, ignoreCase = true) }
            ?: presets.first()
    }

    fun resolve(presetId: String? = defaultPresetId, isDark: Boolean = defaultIsDark): ResolvedAeginTheme {
        return findPreset(presetId).forMode(isDark)
    }
}

const val DEFAULT_PRESET_ID = "aegin-default"
const val APP_THEME_RESOURCE_PATH = "files/app_theme.json"

val LocalAeginTheme = staticCompositionLocalOf {
    AeginThemeConfigLoader.defaultCatalog().resolve(isDark = true)
}

object AeginThemeConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun defaultCatalog(): AeginThemeCatalog {
        val defaultPreset = AeginBrandPreset(
            id = DEFAULT_PRESET_ID,
            displayName = "Aegin Default",
            description = "Signature Reveila obsidian & ember palette",
            dark = ResolvedAeginTheme(
                presetId = DEFAULT_PRESET_ID,
                displayName = "Aegin Default",
                description = "Signature Reveila obsidian & ember palette",
                isDark = true,
                colors = DarkAeginColors,
                pillButton = defaultPillButtonStyle(DarkAeginColors),
                typography = AeginTypographyTokens()
            ),
            light = ResolvedAeginTheme(
                presetId = DEFAULT_PRESET_ID,
                displayName = "Aegin Default",
                description = "Signature Reveila obsidian & ember palette",
                isDark = false,
                colors = LightAeginColors,
                pillButton = defaultPillButtonStyle(LightAeginColors),
                typography = AeginTypographyTokens()
            )
        )
        return AeginThemeCatalog(
            defaultPresetId = DEFAULT_PRESET_ID,
            defaultIsDark = true,
            presets = listOf(defaultPreset)
        )
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadFromResources(resourcePath: String = APP_THEME_RESOURCE_PATH): AeginThemeCatalog {
        return try {
            val rawJson = Res.readBytes(resourcePath).decodeToString()
            parseJson(rawJson)
        } catch (_: Exception) {
            defaultCatalog()
        }
    }

    fun parseJson(rawJson: String): AeginThemeCatalog {
        if (rawJson.isBlank()) return defaultCatalog()
        val dto = try {
            json.decodeFromString(AppThemeConfigDto.serializer(), rawJson)
        } catch (_: Exception) {
            return defaultCatalog()
        }

        val resolvedPresets = dto.presets
            .filter { it.id.isNotBlank() }
            .map { resolvePreset(it) }
            .ifEmpty { return defaultCatalog() }

        val defaultPresetId = resolvedPresets
            .firstOrNull { it.id.equals(dto.defaultPresetId, ignoreCase = true) }
            ?.id
            ?: resolvedPresets.first().id

        val defaultIsDark = !dto.defaultMode.equals("light", ignoreCase = true)

        return AeginThemeCatalog(
            defaultPresetId = defaultPresetId,
            defaultIsDark = defaultIsDark,
            presets = resolvedPresets
        )
    }

    private fun resolvePreset(dto: BrandThemePresetDto): AeginBrandPreset {
        val displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.id
        val description = dto.description?.takeIf { it.isNotBlank() } ?: ""
        val typography = resolveTypography(dto.typography)
        val darkColors = resolveColors(dto.dark, DarkAeginColors)
        val lightColors = resolveColors(dto.light, LightAeginColors)
        val darkPill = resolvePillButtonStyle(dto.dark?.pillButton, dto.pillButton, darkColors)
        val lightPill = resolvePillButtonStyle(dto.light?.pillButton, dto.pillButton, lightColors)

        return AeginBrandPreset(
            id = dto.id,
            displayName = displayName,
            description = description,
            dark = ResolvedAeginTheme(
                presetId = dto.id,
                displayName = displayName,
                description = description,
                isDark = true,
                colors = darkColors,
                pillButton = darkPill,
                typography = typography
            ),
            light = ResolvedAeginTheme(
                presetId = dto.id,
                displayName = displayName,
                description = description,
                isDark = false,
                colors = lightColors,
                pillButton = lightPill,
                typography = typography
            )
        )
    }

    private fun resolveColors(dto: ThemePaletteDto?, fallback: AeginColors): AeginColors {
        if (dto == null) return fallback
        val bg = parseHexColor(dto.background, fallback.bg)
        val surface = parseHexColor(dto.surface, fallback.card)
        val surfaceAlt = parseHexColor(dto.surfaceAlt, fallback.cardAlt)
        val surfaceElevated = parseHexColor(dto.surfaceElevated, fallback.surfaceElevated)
        return AeginColors(
            isDark = fallback.isDark,
            bg = bg,
            card = surface,
            cardAlt = surfaceAlt,
            surfaceElevated = surfaceElevated,
            text = parseHexColor(dto.textPrimary, fallback.text),
            subText = parseHexColor(dto.textSecondary, fallback.subText),
            mutedText = parseHexColor(dto.textMuted, fallback.mutedText),
            border = parseHexColor(dto.border, fallback.border),
            glassBorder = parseHexColor(dto.glassBorder, fallback.glassBorder),
            headerBg = parseHexColor(dto.headerBackground, bg),
            tabBar = parseHexColor(dto.tabBarBackground, surfaceAlt),
            navBarBg = parseHexColor(dto.navBarBackground, surface),
            inputBg = parseHexColor(dto.inputBackground, surfaceAlt),
            primary = parseHexColor(dto.primary, fallback.primary),
            primaryEnd = parseHexColor(dto.primaryEnd, parseHexColor(dto.primary, fallback.primaryEnd)),
            onPrimary = parseHexColor(dto.onPrimary, fallback.onPrimary),
            success = parseHexColor(dto.success, fallback.success),
            accent = parseHexColor(dto.accent, fallback.accent),
            accentEnd = parseHexColor(dto.accentEnd, fallback.accentEnd),
            violet = parseHexColor(dto.violet, fallback.violet),
            warning = parseHexColor(dto.warning, fallback.warning),
            danger = parseHexColor(dto.danger, fallback.danger),
            cyan = parseHexColor(dto.cyan, fallback.cyan)
        )
    }

    private fun resolvePillButtonStyle(
        paletteDto: PillButtonPaletteDto?,
        shapeDto: PillButtonShapeDto?,
        colors: AeginColors
    ): AeginPillButtonStyle {
        val base = defaultPillButtonStyle(colors)
        return AeginPillButtonStyle(
            enabledBackground = parseHexColor(paletteDto?.enabledBackground, base.enabledBackground),
            enabledText = parseHexColor(paletteDto?.enabledText, base.enabledText),
            enabledIcon = parseHexColor(paletteDto?.enabledIcon, base.enabledIcon),
            enabledBorder = parseHexColor(paletteDto?.enabledBorder, base.enabledBorder),
            selectedBackground = parseHexColor(paletteDto?.selectedBackground, base.selectedBackground),
            selectedText = parseHexColor(paletteDto?.selectedText, base.selectedText),
            selectedIcon = parseHexColor(paletteDto?.selectedIcon, base.selectedIcon),
            selectedBorder = parseHexColor(paletteDto?.selectedBorder, base.selectedBorder),
            disabledBackground = parseHexColor(paletteDto?.disabledBackground, base.disabledBackground),
            disabledText = parseHexColor(paletteDto?.disabledText, base.disabledText),
            disabledIcon = parseHexColor(paletteDto?.disabledIcon, base.disabledIcon),
            disabledBorder = parseHexColor(paletteDto?.disabledBorder, base.disabledBorder),
            primaryEnabledBackground = parseHexColor(paletteDto?.primaryEnabledBackground, base.primaryEnabledBackground),
            primaryEnabledBackgroundEnd = parseHexColor(
                paletteDto?.primaryEnabledBackgroundEnd,
                parseHexColor(paletteDto?.primaryEnabledBackground, base.primaryEnabledBackgroundEnd)
            ),
            primaryEnabledText = parseHexColor(paletteDto?.primaryEnabledText, base.primaryEnabledText),
            primaryDisabledBackground = parseHexColor(paletteDto?.primaryDisabledBackground, base.primaryDisabledBackground),
            primaryDisabledText = parseHexColor(paletteDto?.primaryDisabledText, base.primaryDisabledText),
            cornerRadius = (shapeDto?.cornerRadiusDp ?: base.cornerRadius.value).coerceAtLeast(0f).dp,
            borderWidth = (shapeDto?.borderWidthDp ?: base.borderWidth.value).coerceAtLeast(0f).dp,
            horizontalPadding = (shapeDto?.horizontalPaddingDp ?: base.horizontalPadding.value).coerceAtLeast(0f).dp,
            verticalPadding = (shapeDto?.verticalPaddingDp ?: base.verticalPadding.value).coerceAtLeast(0f).dp
        )
    }

    private fun resolveTypography(dto: ThemeTypographyDto?): AeginTypographyTokens {
        if (dto == null) return AeginTypographyTokens()
        val fontName = dto.fontFamily?.trim()?.takeIf { it.isNotEmpty() } ?: "sans-serif"
        return AeginTypographyTokens(
            fontFamilyName = fontName,
            fontFamily = parseFontFamily(fontName),
            monoFontFamily = parseFontFamily(dto.monoFontFamily ?: "monospace"),
            titleWeight = parseFontWeight(dto.titleWeight),
            bodySize = (dto.bodySizeSp ?: 14f).coerceIn(10f, 22f).sp,
            labelSize = (dto.labelSizeSp ?: 11f).coerceIn(8f, 18f).sp
        )
    }

    fun defaultPillButtonStyle(colors: AeginColors): AeginPillButtonStyle {
        val disabledAlpha = 0.42f
        return AeginPillButtonStyle(
            enabledBackground = colors.card,
            enabledText = colors.text,
            enabledIcon = colors.subText,
            enabledBorder = colors.glassBorder,
            selectedBackground = colors.cyan.copy(alpha = 0.18f),
            selectedText = colors.text,
            selectedIcon = colors.cyan,
            selectedBorder = colors.cyan,
            disabledBackground = colors.card.copy(alpha = 0.6f),
            disabledText = colors.subText.copy(alpha = disabledAlpha),
            disabledIcon = colors.subText.copy(alpha = disabledAlpha),
            disabledBorder = colors.glassBorder.copy(alpha = 0.5f),
            primaryEnabledBackground = colors.primary,
            primaryEnabledBackgroundEnd = colors.primaryEnd,
            primaryEnabledText = colors.onPrimary,
            primaryDisabledBackground = colors.surfaceElevated.copy(alpha = 0.45f),
            primaryDisabledText = colors.subText.copy(alpha = disabledAlpha)
        )
    }

    /**
     * Parses `#RGB`, `#RRGGBB`, or `#AARRGGBB` (with or without leading `#`) into a Compose [Color].
     * Returns [fallback] when the string is null, blank, or malformed.
     */
    fun parseHexColor(hex: String?, fallback: Color): Color {
        val cleaned = hex?.trim()?.removePrefix("#") ?: return fallback
        if (cleaned.isEmpty()) return fallback
        val normalized = when (cleaned.length) {
            3 -> buildString(8) {
                append("FF")
                for (c in cleaned) {
                    append(c)
                    append(c)
                }
            }
            6 -> "FF$cleaned"
            8 -> cleaned
            else -> return fallback
        }
        val argb = normalized.toLongOrNull(16) ?: return fallback
        return Color(argb.toInt())
    }

    fun parseFontFamily(name: String?): FontFamily {
        return when (name?.trim()?.lowercase()) {
            "sans", "sans-serif", "sansserif" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono", "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
    }

    fun parseFontWeight(name: String?): FontWeight {
        return when (name?.trim()?.lowercase()) {
            "light" -> FontWeight.Light
            "normal", "regular" -> FontWeight.Normal
            "medium" -> FontWeight.Medium
            "semibold", "semi-bold" -> FontWeight.SemiBold
            "bold" -> FontWeight.Bold
            "extrabold", "extra-bold" -> FontWeight.ExtraBold
            "black" -> FontWeight.Black
            else -> FontWeight.Bold
        }
    }
}
