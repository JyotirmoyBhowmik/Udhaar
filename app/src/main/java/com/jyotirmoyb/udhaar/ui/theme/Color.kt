package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val label: String, val subtitle: String) {
    EMERALD_FINANCE("Emerald Slate", "Modern financial slate & deep emerald ink"),
    SYSTEM_DEFAULT("Follow Phone Setting", "Automatically matches your phone's Light / Dark mode"),
    MONOCHROME_LIGHT("Monochrome Light", "High-contrast #000000 & #FFFFFF Swiss grid"),
    OBSIDIAN_DARK("Obsidian Dark", "Deep OLED dark mode (#0E0E0E & crisp white)"),
    PAPER_LEDGER("Warm Paper Khata", "Archival cream parchment & espresso ink")
}

enum class AppFontScale(val label: String, val subtitle: String, val multiplier: Float) {
    COMPACT("Compact (Small)", "Smaller text to fit more records on compact mobile screens", 0.90f),
    STANDARD("Standard (Default)", "Balanced mobile typography", 1.00f),
    LARGE("Comfortable (Large)", "Slightly larger text for easier reading", 1.10f)
}

data class UdhaarColorPalette(
    val primaryInk: Color,
    val backgroundSurface: Color,
    val cardSurface: Color,
    val borderStroke: Color,
    val subtleSurface: Color,
    val mutedChip: Color,
    val secondaryInk: Color,
    val mutedInk: Color,
    val selectedPillFill: Color,
    val receivableCardBg: Color,
    val receivableCardInk: Color,
    val receivableBadgeBg: Color,
    val payableCardBg: Color,
    val payableCardInk: Color,
    val accentFabBg: Color,
    val accentFabInk: Color = Color(0xFFFFFFFF)
)

val EmeraldFinancePalette = UdhaarColorPalette(
    primaryInk = Color(0xFF0E3828),
    backgroundSurface = Color(0xFFF3F7F5),
    cardSurface = Color(0xFFFFFFFF),
    borderStroke = Color(0xFFCBDAD3),
    subtleSurface = Color(0xFFEAF2EE),
    mutedChip = Color(0xFFDCE9E3),
    secondaryInk = Color(0xFF3B594D),
    mutedInk = Color(0xFF5D7A6E),
    selectedPillFill = Color(0xFFD7EBE2),
    receivableCardBg = Color(0xFFA2D6BE),
    receivableCardInk = Color(0xFF0B3625),
    receivableBadgeBg = Color(0xFFCDE9DC),
    payableCardBg = Color(0xFFF89894),
    payableCardInk = Color(0xFF5A1513),
    accentFabBg = Color(0xFF369668)
)

val MonochromeLightPalette = UdhaarColorPalette(
    primaryInk = Color(0xFF111827),
    backgroundSurface = Color(0xFFF8FAFC),
    cardSurface = Color(0xFFFFFFFF),
    borderStroke = Color(0xFFE2E8F0),
    subtleSurface = Color(0xFFF1F5F9),
    mutedChip = Color(0xFFE2E8F0),
    secondaryInk = Color(0xFF475569),
    mutedInk = Color(0xFF64748B),
    selectedPillFill = Color(0xFFE2E8F0),
    receivableCardBg = Color(0xFFA7DAC3),
    receivableCardInk = Color(0xFF0B3625),
    receivableBadgeBg = Color(0xFFD1EDE0),
    payableCardBg = Color(0xFFF89D99),
    payableCardInk = Color(0xFF5A1513),
    accentFabBg = Color(0xFF2E8B5E)
)

val ObsidianDarkPalette = UdhaarColorPalette(
    primaryInk = Color(0xFFF1F5F3),
    backgroundSurface = Color(0xFF0E1210),
    cardSurface = Color(0xFF171D1A),
    borderStroke = Color(0xFF2B3631),
    subtleSurface = Color(0xFF1D2521),
    mutedChip = Color(0xFF26312C),
    secondaryInk = Color(0xFFA6B8B0),
    mutedInk = Color(0xFF7B8F86),
    selectedPillFill = Color(0xFF1B3B2E),
    receivableCardBg = Color(0xFF164432),
    receivableCardInk = Color(0xFFA7F3D0),
    receivableBadgeBg = Color(0xFF215B44),
    payableCardBg = Color(0xFF4C1D1B),
    payableCardInk = Color(0xFFFECACA),
    accentFabBg = Color(0xFF38986A)
)

val PaperLedgerPalette = UdhaarColorPalette(
    primaryInk = Color(0xFF1C1917),
    backgroundSurface = Color(0xFFFAF6EE),
    cardSurface = Color(0xFFFFFDF9),
    borderStroke = Color(0xFFDDD3C0),
    subtleSurface = Color(0xFFF2ECE1),
    mutedChip = Color(0xFFE8DFD1),
    secondaryInk = Color(0xFF57534E),
    mutedInk = Color(0xFF78716C),
    selectedPillFill = Color(0xFFDCEBE2),
    receivableCardBg = Color(0xFFAAD6BF),
    receivableCardInk = Color(0xFF0E3828),
    receivableBadgeBg = Color(0xFFD2ECE0),
    payableCardBg = Color(0xFFF69E9A),
    payableCardInk = Color(0xFF5A1513),
    accentFabBg = Color(0xFF338C61)
)

internal var activePaletteState by mutableStateOf(EmeraldFinancePalette)

fun paletteForTheme(mode: AppThemeMode, isSystemDark: Boolean = false): UdhaarColorPalette = when (mode) {
    AppThemeMode.EMERALD_FINANCE -> EmeraldFinancePalette
    AppThemeMode.SYSTEM_DEFAULT -> if (isSystemDark) ObsidianDarkPalette else EmeraldFinancePalette
    AppThemeMode.MONOCHROME_LIGHT -> MonochromeLightPalette
    AppThemeMode.OBSIDIAN_DARK -> ObsidianDarkPalette
    AppThemeMode.PAPER_LEDGER -> PaperLedgerPalette
}

// Snapshot-backed semantic color accessors so all screens & components recompose on theme change
val PureBlack: Color get() = activePaletteState.primaryInk
val PureWhite: Color get() = activePaletteState.backgroundSurface
val CardSurfaceWhite: Color get() = activePaletteState.cardSurface
val BorderGrey: Color get() = activePaletteState.borderStroke
val SubtleSurfaceGrey: Color get() = activePaletteState.subtleSurface
val MutedChipGrey: Color get() = activePaletteState.mutedChip
val SecondaryInkGrey: Color get() = activePaletteState.secondaryInk
val MutedInkGrey: Color get() = activePaletteState.mutedInk
val SelectedPillTint: Color get() = activePaletteState.selectedPillFill
val ReceivableCardBg: Color get() = activePaletteState.receivableCardBg
val ReceivableCardInk: Color get() = activePaletteState.receivableCardInk
val ReceivableBadgeBg: Color get() = activePaletteState.receivableBadgeBg
val PayableCardBg: Color get() = activePaletteState.payableCardBg
val PayableCardInk: Color get() = activePaletteState.payableCardInk
val AccentFabGreen: Color get() = activePaletteState.accentFabBg
val AccentFabInk: Color get() = activePaletteState.accentFabInk
val CharcoalSurface = Color(0xFF141414)
