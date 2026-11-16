package com.example.wakeonwanremotephone.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Frost palette (light theme) — the single source of truth for the redesign.
// Warm neutral greys, translucent white glass, one iOS-blue accent.
// ---------------------------------------------------------------------------

// Backgrounds
val FrostBg = Color(0xFFF2F2F7)
val FrostBgSecondary = Color(0xFFE9E9EF)

// Glass surfaces (translucent white, layered over the grey background)
val FrostSurfaceGlass = Color(0x94FFFFFF)      // #FFFFFF @ 58% — main cards
val FrostSurfaceElevated = Color(0xC2FFFFFF)   // #FFFFFF @ 76% — nested cards / inputs
val FrostSurfaceSolid = Color(0xEBFFFFFF)      // #FFFFFF @ 92% — dialogs / nav bar
val FrostSurfaceInset = Color(0xB8FFFFFF)      // #FFFFFF @ 72% — log rows / inset wells

// Hairline borders
val FrostBorderSubtle = Color(0x0F000000)      // #000000 @ 6%
val FrostBorderStandard = Color(0x1A000000)    // #000000 @ 10%
val FrostBorderHover = Color(0x29000000)       // #000000 @ 16%
val FrostBorderStrong = Color(0xE6FFFFFF)      // #FFFFFF @ 90% — top inner highlight

// Text
val FrostTextPrimary = Color(0xFF1C1C1E)
val FrostTextSecondary = Color(0xFF6E6E73)
val FrostTextTertiary = Color(0xFF8E8E93)

// Accent and semantics
val FrostAccent = Color(0xFF0A84FF)            // iOS blue
val FrostAccentDim = Color(0x1F0A84FF)         // #0A84FF @ 12%
val FrostAccentBorder = Color(0x400A84FF)      // #0A84FF @ 25%
val FrostAccentGlow = Color(0x330A84FF)        // #0A84FF @ 20% — soft ambient tint
val FrostSuccess = Color(0xFF34C759)
val FrostSuccessDim = Color(0x1F34C759)
val FrostWarning = Color(0xFFFF9F0A)
val FrostWarningDim = Color(0x1FFF9F0A)
val FrostDanger = Color(0xFFFF3B30)
val FrostDangerDim = Color(0x1FFF3B30)
val FrostDangerBorder = Color(0x40FF3B30)
val FrostInfo = Color(0xFF0A84FF)
val FrostInfoDim = Color(0x1F0A84FF)

// Neutral track for controls (iOS segmented-control trough)
val FrostTrack = Color(0xFFE3E3E8)
val FrostDivider = Color(0x14000000)           // #000000 @ 8%

// Decorative background blob tints
val FrostBlobWhite = Color(0x59FFFFFF)         // 35%
val FrostBlobCool = Color(0x47D9DEE8)          // cool grey @ 28%
val FrostBlobWarm = Color(0x4DE7E3DD)          // warm grey @ 30%

// ---------------------------------------------------------------------------
// Legacy aliases — retained so existing call sites keep compiling.
// Every name below now resolves to a Frost token; none of them are dark.
// ---------------------------------------------------------------------------

// Former dark backgrounds now map to light Frost surfaces.
val DarkBgPrimary = FrostBg
val DarkBgSecondary = FrostBgSecondary
val DarkSurface = FrostSurfaceGlass
val DarkSurfaceElevated = FrostSurfaceElevated
val DarkSurfaceHighlight = FrostSurfaceSolid

// Hairline borders
val BorderSubtle = FrostBorderSubtle
val BorderStandard = FrostBorderStandard
val BorderHover = FrostBorderHover

// Former emerald accent now maps to the Frost blue accent.
val AccentEmerald = FrostAccent
val AccentEmeraldDim = FrostAccentDim
val AccentEmeraldBorder = FrostAccentBorder
val AccentEmeraldGlow = FrostAccentGlow

// Semantic palette
val SemanticDanger = FrostDanger
val SemanticDangerDim = FrostDangerDim
val SemanticDangerBorder = FrostDangerBorder

val SemanticWarning = FrostWarning
val SemanticWarningDim = FrostWarningDim

val SemanticInfo = FrostInfo
val SemanticInfoDim = FrostInfoDim

// Former slate typography scale now maps to dark Frost text tokens.
val Slate50 = FrostTextPrimary
val Slate100 = FrostTextPrimary
val Slate200 = FrostTextPrimary
val Slate300 = FrostTextSecondary
val Slate400 = FrostTextSecondary
val Slate500 = FrostTextTertiary
val Slate600 = FrostTextTertiary
val Slate700 = FrostTextPrimary
val Slate800 = FrostTextPrimary
val Slate900 = FrostTextPrimary

// Backward compatibility aliases for Remote Phone
val DeepBlack = FrostBg
val Charcoal = FrostSurfaceSolid
val GlassWhite = FrostSurfaceGlass
val NeonBlue = FrostAccent
val NeonGreen = FrostAccent
val NeonPurple = FrostAccent
val NeonRed = FrostDanger
val NeonOrange = FrostWarning

val Purple80 = FrostAccent
val PurpleGrey80 = FrostTextSecondary
val Pink80 = FrostDanger
val Purple40 = FrostAccent
val PurpleGrey40 = FrostTextTertiary
val Pink40 = FrostDanger
