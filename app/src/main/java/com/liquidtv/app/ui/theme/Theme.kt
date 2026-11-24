package com.liquidtv.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Composition local for glass-specific design tokens
 * dynamically tuned for light (marble) vs dark themes.
 */
@Immutable
data class LiquidGlassTokens(
    val glassAlpha: Float = 0.72f,
    val blurRadius: Float = 20f,
    val borderAlpha: Float = 0.48f,
    val highlightAlpha: Float = 0.34f,
    val focusBorderAlpha: Float = 0.95f,
    val focusScale: Float = 1.06f,
    val cardCornerRadius: Float = 16f,
    val pillCornerRadius: Float = 50f,
    val navBubbleCornerRadius: Float = 28f,
)

val LocalLiquidGlassTokens = staticCompositionLocalOf { LiquidGlassTokens() }

val LocalAppTheme = staticCompositionLocalOf { AppThemeCatalog.defaultTheme }

object AppTheme {
    val current: ThemeDefinition
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val colors: ThemePalette
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current.palette
}

private fun ThemeDefinition.toMaterialColorScheme(): ColorScheme {
    val p = palette
    return if (isLight) {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.selected,
            onPrimaryContainer = p.text,
            secondary = p.waveTwo,
            onSecondary = p.onAccent,
            secondaryContainer = p.panel,
            onSecondaryContainer = p.textSecondary,
            tertiary = p.waveOne,
            onTertiary = p.onAccent,
            background = p.background,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.panel,
            onSurfaceVariant = p.textSecondary,
            surfaceContainerHighest = p.card,
            error = p.danger,
            onError = p.onDanger,
            outline = p.border,
            outlineVariant = p.muted.copy(alpha = 0.45f),
            scrim = p.dialogScrim,
        )
    } else {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.selected,
            onPrimaryContainer = p.text,
            secondary = p.waveTwo,
            onSecondary = p.onAccent,
            secondaryContainer = p.panel,
            onSecondaryContainer = p.textSecondary,
            tertiary = p.waveOne,
            onTertiary = p.text,
            background = p.background,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.panel,
            onSurfaceVariant = p.textSecondary,
            surfaceContainerHighest = p.card,
            error = p.danger,
            onError = p.onDanger,
            outline = p.border,
            outlineVariant = p.muted.copy(alpha = 0.40f),
            scrim = p.dialogScrim,
        )
    }
}

@Composable
fun LiquidTVTheme(
    theme: ThemeDefinition = AppThemeCatalog.defaultTheme,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(theme) { theme.toMaterialColorScheme() }
    val glassTokens = remember(theme.isLight) {
        if (theme.isLight) {
            LiquidGlassTokens(
                glassAlpha = 0.84f,
                borderAlpha = 0.65f,
                highlightAlpha = 0.55f,
                focusBorderAlpha = 0.95f,
            )
        } else {
            LiquidGlassTokens(
                glassAlpha = 0.72f,
                borderAlpha = 0.48f,
                highlightAlpha = 0.28f,
                focusBorderAlpha = 0.95f,
            )
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAppTheme provides theme,
        LocalLiquidGlassTokens provides glassTokens,
        LocalContentColor provides theme.palette.text,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LiquidMaterialTypography,
            content = content,
        )
    }
}

