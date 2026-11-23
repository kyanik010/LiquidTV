package com.liquidtv.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Always dark for TV & IPTV
private val LiquidDarkColorScheme = darkColorScheme(
    primary = LiquidColors.AccentBlue,
    onPrimary = LiquidColors.TextPrimary,
    primaryContainer = LiquidColors.AccentBlueDark,
    onPrimaryContainer = LiquidColors.TextPrimary,
    secondary = LiquidColors.AccentViolet,
    onSecondary = LiquidColors.TextPrimary,
    background = LiquidColors.MidnightNavy,
    onBackground = LiquidColors.TextPrimary,
    surface = LiquidColors.DarkSurface,
    onSurface = LiquidColors.TextPrimary,
    surfaceVariant = LiquidColors.DeepSurface,
    onSurfaceVariant = LiquidColors.TextSecondary,
    error = LiquidColors.ErrorRed,
    onError = LiquidColors.TextPrimary,
    outline = LiquidColors.GlassBorder,
)

/**
 * Composition local for glass-specific design tokens
 * not covered by MaterialTheme.
 */
data class LiquidGlassTokens(
    val glassAlpha: Float = 0.15f,
    val blurRadius: Float = 20f,
    val borderAlpha: Float = 0.2f,
    val highlightAlpha: Float = 0.5f,
    val focusBorderAlpha: Float = 0.7f,
    val focusScale: Float = 1.06f,
    val cardCornerRadius: Float = 16f,
    val pillCornerRadius: Float = 50f,
    val navBubbleCornerRadius: Float = 28f,
)

val LocalLiquidGlassTokens = staticCompositionLocalOf { LiquidGlassTokens() }

@Composable
fun LiquidTVTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalLiquidGlassTokens provides LiquidGlassTokens(),
    ) {
        MaterialTheme(
            colorScheme = LiquidDarkColorScheme,
            typography = LiquidMaterialTypography,
            content = content,
        )
    }
}

