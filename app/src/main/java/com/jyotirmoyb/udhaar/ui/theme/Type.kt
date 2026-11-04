package com.jyotirmoyb.udhaar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val IbmPlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono, FontWeight.Normal),
    Font(R.font.ibm_plex_mono, FontWeight.Medium),
    Font(R.font.ibm_plex_mono, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono, FontWeight.Bold)
)

/**
 * Typography rules:
 * - Space Grotesk: used ONLY for the app title (`displayLarge`)
 * - Inter: used for ALL general text, titles, labels, buttons, and sentences
 * - IBM Plex Mono: applied explicitly via `IbmPlexMonoFamily` only on amounts, dates, and IDs
 */
fun scaledTypography(multiplier: Float = 1.0f): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (24f * multiplier).sp,
        lineHeight = (32f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (22f * multiplier).sp,
        lineHeight = (30f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (20f * multiplier).sp,
        lineHeight = (28f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (18f * multiplier).sp,
        lineHeight = (26f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (18f * multiplier).sp,
        lineHeight = (26f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (16f * multiplier).sp,
        lineHeight = (24f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (14f * multiplier).sp,
        lineHeight = (20f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (16f * multiplier).sp,
        lineHeight = (24f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (14f * multiplier).sp,
        lineHeight = (20f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (12f * multiplier).sp,
        lineHeight = (18f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (14f * multiplier).sp,
        lineHeight = (20f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (12f * multiplier).sp,
        lineHeight = (18f * multiplier).sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (12f * multiplier).sp,
        lineHeight = (16f * multiplier).sp,
        letterSpacing = 0.sp
    )
)

val Typography = scaledTypography(1.0f)
