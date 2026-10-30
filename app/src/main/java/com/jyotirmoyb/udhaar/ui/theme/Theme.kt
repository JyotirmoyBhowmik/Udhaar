package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Shared shape system tokens (8, 12, 16, and 24 dp) used across every screen, sheet, and dialog.
 */
object UdhaarShapeTokens {
    val Corner8 = RoundedCornerShape(8.dp)
    val Corner12 = RoundedCornerShape(12.dp)
    val Corner16 = RoundedCornerShape(16.dp)
    val Corner24 = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

val UdhaarShapes = Shapes(
    extraSmall = UdhaarShapeTokens.Corner8,
    small = UdhaarShapeTokens.Corner12,
    medium = UdhaarShapeTokens.Corner16,
    large = UdhaarShapeTokens.Corner24,
    extraLarge = UdhaarShapeTokens.Corner24
)

@Composable
fun UdhaarTheme(
    themeMode: AppThemeMode = AppThemeMode.EMERALD_FINANCE,
    fontScaleMode: AppFontScale = AppFontScale.STANDARD,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val palette = paletteForTheme(themeMode, isSystemDark)
    SideEffect {
        activePaletteState = palette
    }
    activePaletteState = palette

    val isDark = themeMode == AppThemeMode.OBSIDIAN_DARK ||
        (themeMode == AppThemeMode.SYSTEM_DEFAULT && isSystemDark)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.primaryInk,
            onPrimary = palette.backgroundSurface,
            primaryContainer = palette.selectedPillFill,
            onPrimaryContainer = palette.primaryInk,
            secondary = palette.secondaryInk,
            onSecondary = palette.backgroundSurface,
            secondaryContainer = palette.mutedChip,
            onSecondaryContainer = palette.primaryInk,
            tertiary = palette.accentFabBg,
            onTertiary = palette.accentFabInk,
            background = palette.backgroundSurface,
            onBackground = palette.primaryInk,
            surface = palette.cardSurface,
            onSurface = palette.primaryInk,
            surfaceVariant = palette.subtleSurface,
            onSurfaceVariant = palette.secondaryInk,
            outline = palette.borderStroke,
            outlineVariant = palette.borderStroke
        )
    } else {
        lightColorScheme(
            primary = palette.primaryInk,
            onPrimary = palette.cardSurface,
            primaryContainer = palette.selectedPillFill,
            onPrimaryContainer = palette.primaryInk,
            secondary = palette.secondaryInk,
            onSecondary = palette.cardSurface,
            secondaryContainer = palette.mutedChip,
            onSecondaryContainer = palette.primaryInk,
            tertiary = palette.accentFabBg,
            onTertiary = palette.accentFabInk,
            background = palette.backgroundSurface,
            onBackground = palette.primaryInk,
            surface = palette.cardSurface,
            onSurface = palette.primaryInk,
            surfaceVariant = palette.subtleSurface,
            onSurfaceVariant = palette.secondaryInk,
            outline = palette.borderStroke,
            outlineVariant = palette.borderStroke
        )
    }

    val currentDensity = LocalDensity.current
    // Respect phone system font scale while safely bounding it so large phone font settings never break mobile cards
    val boundedSystemFontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)
    val customDensity = remember(currentDensity.density, boundedSystemFontScale) {
        Density(
            density = currentDensity.density,
            fontScale = boundedSystemFontScale
        )
    }
    val activeTypography = remember(fontScaleMode) {
        scaledTypography(fontScaleMode.multiplier)
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = activeTypography,
            shapes = UdhaarShapes,
            content = content
        )
    }
}
