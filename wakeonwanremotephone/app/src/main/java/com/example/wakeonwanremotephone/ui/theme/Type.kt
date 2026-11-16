package com.example.wakeonwanremotephone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SF-like type scale rendered with the platform sans-serif.
 * No bundled fonts, no monospace body copy.
 */
object FrostTextStyles {
    val LargeTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.8).sp
    )

    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    )

    val Headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp
    )

    val Callout = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )

    val Caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    )

    val Micro = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp
    )

    val Log = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
}

// Convenience accessors used by the Remote Phone UI.
val FrostLargeTitle = FrostTextStyles.LargeTitle
val FrostTitle = FrostTextStyles.Title
val FrostHeadline = FrostTextStyles.Headline
val FrostBody = FrostTextStyles.Body
val FrostCallout = FrostTextStyles.Callout
val FrostCaption = FrostTextStyles.Caption
val FrostMicro = FrostTextStyles.Micro
val FrostLog = FrostTextStyles.Log

// Material typography slots, filled from the same Frost scale.
val Typography = Typography(
    displayLarge = FrostTextStyles.LargeTitle,
    displayMedium = FrostTextStyles.Title,
    displaySmall = FrostTextStyles.Headline,
    headlineLarge = FrostTextStyles.Title,
    headlineMedium = FrostTextStyles.Headline,
    headlineSmall = FrostTextStyles.Headline.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = FrostTextStyles.Title.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = FrostTextStyles.Headline,
    titleSmall = FrostTextStyles.Callout,
    bodyLarge = FrostTextStyles.Body,
    bodyMedium = FrostTextStyles.Body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = FrostTextStyles.Caption,
    labelLarge = FrostTextStyles.Callout,
    labelMedium = FrostTextStyles.Caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = FrostTextStyles.Micro
)
