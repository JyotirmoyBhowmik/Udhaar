package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enterprise 4dp/8dp Spacing System for Udhaar.
 * Eliminates uneven fractional/odd margins (2, 5, 6, 10, 14 dp) across screens and sheets.
 * Follows strict grid steps: 0, 4, 8, 12, 16, 24, 32 dp.
 */
object UdhaarSpacing {
    val None: Dp = 0.dp
    val XSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 12.dp
    val Large: Dp = 16.dp
    val XLarge: Dp = 24.dp
    val XXLarge: Dp = 32.dp

    // Grid Aliases
    val Grid4: Dp = 4.dp
    val Grid8: Dp = 8.dp
    val Grid12: Dp = 12.dp
    val Grid16: Dp = 16.dp
    val Grid24: Dp = 24.dp
    val Grid32: Dp = 32.dp

    // Semantic Spacing Tokens
    val ScreenMargin: Dp = 16.dp
    val CardPadding: Dp = 16.dp
    val CardGap: Dp = 12.dp
    val FieldGap: Dp = 8.dp
    val SectionGap: Dp = 24.dp
    val MinTouchTarget: Dp = 48.dp
    val PrimaryButtonHeight: Dp = 52.dp
    val SecondaryButtonHeight: Dp = 48.dp
    val ChipHeight: Dp = 40.dp
}

val LocalUdhaarSpacing = staticCompositionLocalOf { UdhaarSpacing }
