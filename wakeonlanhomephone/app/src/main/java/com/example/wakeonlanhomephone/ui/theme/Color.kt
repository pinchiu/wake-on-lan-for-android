package com.example.wakeonlanhomephone.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Frost UI tokens (light, Apple-like grey glass)
// ---------------------------------------------------------------------------
// The app historically used a dark "precision hardware" palette. The constant
// names are kept as legacy aliases so existing call sites keep compiling, but
// every value now resolves to a Frost token. New UI code should prefer the
// Frost* names directly.

// Backgrounds
val FrostBg = Color(0xFFF2F2F7)
val FrostBgSecondary = Color(0xFFE9E9EF)

// Translucent white surfaces
val FrostSurfaceGlass = Color(0x94FFFFFF)      // #FFFFFF @ 58%
val FrostSurfaceElevated = Color(0xC2FFFFFF)   // #FFFFFF @ 76%
val FrostSurfaceSolid = Color(0xEBFFFFFF)      // #FFFFFF @ 92%

// Hairlines
val FrostBorderSubtle = Color(0x0F000000)      // #000000 @ 6%
val FrostBorderStandard = Color(0x1A000000)    // #000000 @ 10%
val FrostBorderStrong = Color(0xE6FFFFFF)      // #FFFFFF @ 90%

// Text
val FrostTextPrimary = Color(0xFF1C1C1E)
val FrostTextSecondary = Color(0xFF6E6E73)
val FrostTextTertiary = Color(0xFF8E8E93)

// Accent (iOS blue)
val FrostAccent = Color(0xFF0A84FF)
val FrostAccentDim = Color(0x1F0A84FF)         // 12%
val FrostAccentBorder = Color(0x400A84FF)      // 25%

// Semantic colors (iOS system colors)
val FrostSuccess = Color(0xFF34C759)
val FrostSuccessDim = Color(0x1F34C759)
val FrostSuccessBorder = Color(0x4034C759)
val FrostWarning = Color(0xFFFF9F0A)
val FrostWarningDim = Color(0x1FFF9F0A)
val FrostWarningBorder = Color(0x40FF9F0A)
val FrostDanger = Color(0xFFFF3B30)
val FrostDangerDim = Color(0x1FFF3B30)
val FrostDangerBorder = Color(0x40FF3B30)
val FrostInfo = Color(0xFF0A84FF)
val FrostInfoDim = Color(0x1F0A84FF)
val FrostInfoBorder = Color(0x400A84FF)

// Soft shadow + iOS segmented-control track
val FrostShadow = Color(0x14000000)
val FrostSegmentTrack = Color(0xFFE3E3E8)

// ---------------------------------------------------------------------------
// Legacy aliases. Do not add new call sites that depend on the old names.
// ---------------------------------------------------------------------------

// Backgrounds formerly "Obsidian & Zinc"
val DarkBgPrimary = FrostBg
val DarkBgSecondary = FrostBgSecondary
val DarkSurface = FrostSurfaceGlass
val DarkSurfaceElevated = FrostSurfaceElevated
val DarkSurfaceHighlight = Color(0xD9FFFFFF)   // #FFFFFF @ 85%

// Borders formerly white hairline
val BorderSubtle = FrostBorderSubtle
val BorderStandard = FrostBorderStandard
val BorderHover = Color(0x24000000)

// Accent formerly Emerald
val AccentEmerald = FrostAccent
val AccentEmeraldDim = FrostAccentDim
val AccentEmeraldBorder = FrostAccentBorder
val AccentEmeraldGlow = Color(0x1F0A84FF)

// Semantic palette
val SemanticDanger = FrostDanger
val SemanticDangerDim = FrostDangerDim
val SemanticDangerBorder = FrostDangerBorder

val SemanticWarning = FrostWarning
val SemanticWarningDim = FrostWarningDim
val SemanticWarningBorder = FrostWarningBorder

val SemanticInfo = FrostInfo
val SemanticInfoDim = FrostInfoDim
val SemanticInfoBorder = FrostInfoBorder

// Slate scale, remapped to iOS neutral text/surface tones. The light end now
// means "primary dark text" so existing text colors stay readable on glass.
val Slate50 = FrostTextPrimary
val Slate100 = Color(0xFF2C2C2E)
val Slate200 = Color(0xFF3A3A3C)
val Slate300 = Color(0xFF48484A)
val Slate400 = FrostTextSecondary
val Slate500 = FrostTextTertiary
val Slate600 = Color(0xFFA1A1A6)
val Slate700 = Color(0xFFC7C7CC)
val Slate800 = Color(0xFFD1D1D6)
val Slate900 = Color(0xFFE5E5EA)

// Backward compatibility aliases remapped to Frost tokens
val BackroundDark = FrostBg
val SurfaceGlass = FrostSurfaceGlass
val GlassBorder = FrostBorderSubtle
val GlassSurface = FrostSurfaceElevated

val NeonGreen = FrostSuccess
val NeonBlue = FrostAccent
val CyberCyan = FrostAccent
val CyberYellow = FrostWarning
val NeonRed = FrostDanger
val CyberPink = FrostDanger

val PrimaryBlue = FrostAccent
val PrimaryHover = Color(0xFF0071E3)
val AccentIndigo = FrostAccent
val SettingsPrimary = FrostAccent
val BackgroundDeepNavy = FrostBg
val PurpleAccent = FrostAccent

val Navy950 = FrostBg
val Navy900 = FrostBgSecondary
val Navy850 = FrostSurfaceGlass
val Navy800 = FrostSurfaceElevated
val Navy700 = Slate700
val Navy600 = Slate600

val SuccessGreen = FrostSuccess
val DangerRed = FrostDanger
val OfflineGray = FrostTextTertiary
