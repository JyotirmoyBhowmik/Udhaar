package com.jyotirmoyb.udhaar.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jyotirmoyb.udhaar.ui.components.rememberReducedMotionEnabled

/**
 * Shared shape system tokens (8, 12, 16, and 24 dp) used across every screen, sheet, and dialog.
 */
object UdhaarShapeTokens {
    val Corner8 = RoundedCornerShape(8.dp)
    val Corner12 = RoundedCornerShape(12.dp)
    val Corner16 = RoundedCornerShape(16.dp)
    val Corner24 = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)

    val Small = Corner8
    val Medium = Corner12
    val Large = Corner16
    val ExtraLarge = Corner24

    val ShapeSmall = Corner8
    val ShapeMedium = Corner12
    val ShapeLarge = Corner16
    val ShapeExtraLarge = Corner24
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
    val targetPalette = paletteForTheme(themeMode, isSystemDark)
    val reducedMotion = rememberReducedMotionEnabled()
    val colorAnimDuration = if (reducedMotion) 0 else 240
    val colorSpec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = colorAnimDuration,
        easing = FastOutSlowInEasing
    )

    val primaryInk by animateColorAsState(targetPalette.primaryInk, colorSpec, label = "primaryInk")
    val backgroundSurface by animateColorAsState(targetPalette.backgroundSurface, colorSpec, label = "backgroundSurface")
    val cardSurface by animateColorAsState(targetPalette.cardSurface, colorSpec, label = "cardSurface")
    val borderStroke by animateColorAsState(targetPalette.borderStroke, colorSpec, label = "borderStroke")
    val subtleSurface by animateColorAsState(targetPalette.subtleSurface, colorSpec, label = "subtleSurface")
    val mutedChip by animateColorAsState(targetPalette.mutedChip, colorSpec, label = "mutedChip")
    val secondaryInk by animateColorAsState(targetPalette.secondaryInk, colorSpec, label = "secondaryInk")
    val mutedInk by animateColorAsState(targetPalette.mutedInk, colorSpec, label = "mutedInk")
    val selectedPillFill by animateColorAsState(targetPalette.selectedPillFill, colorSpec, label = "selectedPillFill")
    val receivableCardBg by animateColorAsState(targetPalette.receivableCardBg, colorSpec, label = "receivableCardBg")
    val receivableCardInk by animateColorAsState(targetPalette.receivableCardInk, colorSpec, label = "receivableCardInk")
    val receivableBadgeBg by animateColorAsState(targetPalette.receivableBadgeBg, colorSpec, label = "receivableBadgeBg")
    val payableCardBg by animateColorAsState(targetPalette.payableCardBg, colorSpec, label = "payableCardBg")
    val payableCardInk by animateColorAsState(targetPalette.payableCardInk, colorSpec, label = "payableCardInk")
    val accentFabBg by animateColorAsState(targetPalette.accentFabBg, colorSpec, label = "accentFabBg")
    val accentFabInk by animateColorAsState(targetPalette.accentFabInk, colorSpec, label = "accentFabInk")

    val palette = UdhaarColorPalette(
        primaryInk = primaryInk,
        backgroundSurface = backgroundSurface,
        cardSurface = cardSurface,
        borderStroke = borderStroke,
        subtleSurface = subtleSurface,
        mutedChip = mutedChip,
        secondaryInk = secondaryInk,
        mutedInk = mutedInk,
        selectedPillFill = selectedPillFill,
        receivableCardBg = receivableCardBg,
        receivableCardInk = receivableCardInk,
        receivableBadgeBg = receivableBadgeBg,
        payableCardBg = payableCardBg,
        payableCardInk = payableCardInk,
        accentFabBg = accentFabBg,
        accentFabInk = accentFabInk
    )
    val isDark = themeMode == AppThemeMode.OBSIDIAN_DARK ||
        (themeMode == AppThemeMode.SYSTEM_DEFAULT && isSystemDark)

    val view = LocalView.current
    SideEffect {
        activePaletteState = palette
        if (!view.isInEditMode) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    activePaletteState = palette

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
    val boundedSystemFontScale = currentDensity.fontScale.coerceIn(0.85f, 2.0f)
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
