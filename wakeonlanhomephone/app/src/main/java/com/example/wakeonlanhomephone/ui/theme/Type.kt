package com.example.wakeonlanhomephone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// SF-like scale built from the platform sans-serif. No bundled fonts.
val FrostLargeTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 41.sp,
    letterSpacing = (-0.8).sp
)

val FrostTitle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp
)

val FrostHeadline = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp
)

val FrostBody = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 23.sp,
    letterSpacing = (-0.1).sp
)

val FrostCallout = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

val FrostCaption = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp
)

val FrostMicro = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp
)

// Material 3 mapping so existing MaterialTheme.typography.* call sites pick up
// the Frost scale automatically.
val Typography = Typography(
    displayLarge = FrostLargeTitle,
    displayMedium = FrostLargeTitle,
    displaySmall = FrostTitle,
    headlineLarge = FrostLargeTitle,
    headlineMedium = FrostTitle,
    headlineSmall = FrostHeadline,
    titleLarge = FrostTitle,
    titleMedium = FrostHeadline,
    titleSmall = FrostCallout,
    bodyLarge = FrostBody,
    bodyMedium = FrostCallout,
    bodySmall = FrostCaption,
    labelLarge = FrostCallout,
    labelMedium = FrostCaption,
    labelSmall = FrostMicro
)
