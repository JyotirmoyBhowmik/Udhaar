package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

@Composable
fun UdhaarTheme(
    themeMode: AppThemeMode = AppThemeMode.MONOCHROME_LIGHT,
    content: @Composable () -> Unit
) {
    val palette = paletteForTheme(themeMode)
    SideEffect {
        activePaletteState = palette
    }
    activePaletteState = palette

    val colorScheme = if (themeMode == AppThemeMode.OBSIDIAN_DARK) {
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
