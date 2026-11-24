package com.reveila.aegin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Ensures a pill button's width is always at least its measured height (forming at minimum
 * a circle rather than a vertically squished capsule), while allowing the width to fit its
 * label text without stretching beyond content. Place after background/border/clickable and
 * before inner padding so the pill surface covers the full adjusted bounds.
 */
fun Modifier.pillMinWidthAtLeastHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val finalWidth = maxOf(placeable.width, placeable.height)
        .coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(finalWidth, placeable.height) {
        val xOffset = (finalWidth - placeable.width) / 2
        placeable.placeRelative(xOffset, 0)
    }
}

enum class AeginPillButtonVariant {
    /** Outlined/tinted surface pill (e.g., quick action chips, filter pills). */
    SURFACE,

    /** Filled brand-primary pill (e.g., active tab, primary CTA). */
    PRIMARY
}

/**
 * Theme-driven pill button whose enabled, selected, and disabled styles (colors, border,
 * corner radius, padding, and typography) are configured via `app_theme.json`.
 */
@Composable
fun AeginPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    variant: AeginPillButtonVariant = AeginPillButtonVariant.SURFACE,
    leadingIcon: AeginIconType? = null,
    iconSize: Dp = 12.dp,
    fontSize: TextUnit? = null,
    fontWeight: FontWeight? = null
) {
    val resolvedTheme = LocalAeginTheme.current
    val pill = resolvedTheme.pillButton
    val typo = resolvedTheme.typography
    val shape = RoundedCornerShape(pill.cornerRadius)

    val backgroundBrush: Brush = when (variant) {
        AeginPillButtonVariant.PRIMARY -> {
            if (enabled) pill.primaryEnabledBrush
            else SolidColor(pill.primaryDisabledBackground)
        }
        AeginPillButtonVariant.SURFACE -> {
            val bgColor = when {
                !enabled -> pill.disabledBackground
                selected -> pill.selectedBackground
                else -> pill.enabledBackground
            }
            SolidColor(bgColor)
        }
    }

    val textColor = when (variant) {
        AeginPillButtonVariant.PRIMARY -> {
            if (enabled) pill.primaryEnabledText else pill.primaryDisabledText
        }
        AeginPillButtonVariant.SURFACE -> when {
            !enabled -> pill.disabledText
            selected -> pill.selectedText
            else -> pill.enabledText
        }
    }

    val iconColor = when (variant) {
        AeginPillButtonVariant.PRIMARY -> textColor
        AeginPillButtonVariant.SURFACE -> when {
            !enabled -> pill.disabledIcon
            selected -> pill.selectedIcon
            else -> pill.enabledIcon
        }
    }

    val borderModifier = if (variant == AeginPillButtonVariant.SURFACE && pill.borderWidth > 0.dp) {
        val borderColor = when {
            !enabled -> pill.disabledBorder
            selected -> pill.selectedBorder
            else -> pill.enabledBorder
        }
        Modifier.border(width = pill.borderWidth, color = borderColor, shape = shape)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .clip(shape)
            .background(backgroundBrush)
            .then(borderModifier)
            .clickable(enabled = enabled, onClick = onClick)
            .pillMinWidthAtLeastHeight()
            .padding(horizontal = pill.horizontalPadding, vertical = pill.verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        if (leadingIcon != null) {
            AeginVectorIcon(
                type = leadingIcon,
                tint = iconColor,
                size = iconSize,
                strokeWidth = 1.6.dp
            )
        }
        val resolvedFontSize = fontSize ?: typo.labelSize
        val resolvedWeight = fontWeight ?: if (selected || variant == AeginPillButtonVariant.PRIMARY) {
            typo.titleWeight
        } else {
            FontWeight.SemiBold
        }
        Text(
            text = text,
            color = textColor,
            style = TextStyle(
                fontFamily = typo.fontFamily,
                fontSize = resolvedFontSize,
                lineHeight = resolvedFontSize,
                fontWeight = resolvedWeight,
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
