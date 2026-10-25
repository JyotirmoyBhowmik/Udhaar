package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val label: String, val subtitle: String) {
    MONOCHROME_LIGHT("Monochrome Light", "High-contrast #000000 & #FFFFFF Swiss grid"),
    OBSIDIAN_DARK("Obsidian Dark", "Deep OLED dark mode (#0C0C0C & crisp white)"),
    PAPER_LEDGER("Warm Paper Khata", "Archival cream parchment & espresso ink"),
    EMERALD_FINANCE("Emerald Slate", "Modern financial slate & deep emerald ink")
}

data class UdhaarColorPalette(
    val primaryInk: Color,
    val backgroundSurface: Color,
    val borderStroke: Color,
    val subtleSurface: Color,
    val mutedChip: Color,
    val secondaryInk: Color,
    val mutedInk: Color
)

val MonochromeLightPalette = UdhaarColorPalette(
    primaryInk = Color(0xFF000000),
    backgroundSurface = Color(0xFFFFFFFF),
    borderStroke = Color(0xFFE5E5E5),
    subtleSurface = Color(0xFFFAFAFA),
    mutedChip = Color(0xFFF2F2F2),
    secondaryInk = Color(0xFF525252),
    mutedInk = Color(0xFF737373)
)

val ObsidianDarkPalette = UdhaarColorPalette(
    primaryInk = Color(0xFFF5F5F5),
    backgroundSurface = Color(0xFF0E0E0E),
    borderStroke = Color(0xFF2E2E2E),
    subtleSurface = Color(0xFF181818),
    mutedChip = Color(0xFF242424),
    secondaryInk = Color(0xFFA3A3A3),
    mutedInk = Color(0xFF737373)
)

val PaperLedgerPalette = UdhaarColorPalette(
    primaryInk = Color(0xFF1C1917),
    backgroundSurface = Color(0xFFFAF6EE),
    borderStroke = Color(0xFFDDD3C0),
    subtleSurface = Color(0xFFF2ECE1),
    mutedChip = Color(0xFFE8DFD1),
    secondaryInk = Color(0xFF57534E),
    mutedInk = Color(0xFF78716C)
)

val EmeraldFinancePalette = UdhaarColorPalette(
    primaryInk = Color(0xFF063729),
    backgroundSurface = Color(0xFFF7FAF8),
    borderStroke = Color(0xFFCCE0D8),
    subtleSurface = Color(0xFFEDF5F1),
    mutedChip = Color(0xFFDCECE5),
    secondaryInk = Color(0xFF2D5A4C),
    mutedInk = Color(0xFF52796C)
)

internal var activePaletteState by mutableStateOf(MonochromeLightPalette)

fun paletteForTheme(mode: AppThemeMode): UdhaarColorPalette = when (mode) {
    AppThemeMode.MONOCHROME_LIGHT -> MonochromeLightPalette
    AppThemeMode.OBSIDIAN_DARK -> ObsidianDarkPalette
    AppThemeMode.PAPER_LEDGER -> PaperLedgerPalette
    AppThemeMode.EMERALD_FINANCE -> EmeraldFinancePalette
}

// Snapshot-backed semantic color accessors so all screens & components recompose on theme change
val PureBlack: Color get() = activePaletteState.primaryInk
val PureWhite: Color get() = activePaletteState.backgroundSurface
val BorderGrey: Color get() = activePaletteState.borderStroke
val SubtleSurfaceGrey: Color get() = activePaletteState.subtleSurface
val MutedChipGrey: Color get() = activePaletteState.mutedChip
val SecondaryInkGrey: Color get() = activePaletteState.secondaryInk
val MutedInkGrey: Color get() = activePaletteState.mutedInk
val CharcoalSurface = Color(0xFF141414)
