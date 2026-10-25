package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MonochromeColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = PureBlack,
    onPrimaryContainer = PureWhite,
    secondary = SecondaryInkGrey,
    onSecondary = PureWhite,
    secondaryContainer = MutedChipGrey,
    onSecondaryContainer = PureBlack,
    tertiary = PureBlack,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = PureBlack,
    surface = PureWhite,
    onSurface = PureBlack,
    surfaceVariant = SubtleSurfaceGrey,
    onSurfaceVariant = SecondaryInkGrey,
    outline = BorderGrey,
    outlineVariant = BorderGrey,
    error = PureBlack,
    onError = PureWhite,
    errorContainer = MutedChipGrey,
    onErrorContainer = PureBlack
)

@Composable
fun UdhaarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeColorScheme,
        typography = Typography,
        content = content
    )
}
