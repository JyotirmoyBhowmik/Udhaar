package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun UdhaarTheme(
    themeMode: AppThemeMode = AppThemeMode.MONOCHROME_LIGHT,
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
            primaryContainer = palette.primaryInk,
            onPrimaryContainer = palette.backgroundSurface,
            secondary = palette.secondaryInk,
            onSecondary = palette.backgroundSurface,
            secondaryContainer = palette.mutedChip,
            onSecondaryContainer = palette.primaryInk,
            tertiary = palette.primaryInk,
            onTertiary = palette.backgroundSurface,
            background = palette.backgroundSurface,
            onBackground = palette.primaryInk,
            surface = palette.backgroundSurface,
            onSurface = palette.primaryInk,
            surfaceVariant = palette.subtleSurface,
            onSurfaceVariant = palette.secondaryInk,
            outline = palette.borderStroke,
            outlineVariant = palette.borderStroke
        )
    } else {
        lightColorScheme(
            primary = palette.primaryInk,
            onPrimary = palette.backgroundSurface,
            primaryContainer = palette.primaryInk,
            onPrimaryContainer = palette.backgroundSurface,
            secondary = palette.secondaryInk,
            onSecondary = palette.backgroundSurface,
            secondaryContainer = palette.mutedChip,
            onSecondaryContainer = palette.primaryInk,
            tertiary = palette.primaryInk,
            onTertiary = palette.backgroundSurface,
            background = palette.backgroundSurface,
            onBackground = palette.primaryInk,
            surface = palette.backgroundSurface,
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
            content = content
        )
    }
}
