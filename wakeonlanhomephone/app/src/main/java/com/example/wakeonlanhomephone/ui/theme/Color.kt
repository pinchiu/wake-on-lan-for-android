package com.example.wakeonlanhomephone.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Frost UI tokens (Apple-like grey glass)
// ---------------------------------------------------------------------------
// The app historically used a dark "precision hardware" palette. The constant
// names are kept as legacy aliases so existing call sites keep compiling, but
// every value resolves to the active Frost token. New UI code should prefer the
// Frost* names directly.
//
// Tokens are exposed as top-level `val`s with getters instead of `const val`s so
// the same call sites render light or dark without being rewritten. The theme
// flips [FrostCurrent.dark] before composing the UI and recreates the activity
// when the user changes the preference, so each composition reads one palette.

/** Palette currently selected by the rest of the UI. Set by WakeOnLanTheme. */
object FrostCurrent {
    @Volatile
    var dark: Boolean = false
}

private fun frostColor(light: Long, dark: Long): Color =
    Color(if (FrostCurrent.dark) dark else light)

// Backgrounds
val FrostBg: Color get() = frostColor(0xFFF2F2F7, 0xFF121214)
val FrostBgSecondary: Color get() = frostColor(0xFFE9E9EF, 0xFF1C1C1E)

// Translucent white (light) / charcoal (dark) surfaces
val FrostSurfaceGlass: Color get() = frostColor(0x94FFFFFF, 0xCC1C1C1E)      // light 58%, dark 80%
val FrostSurfaceElevated: Color get() = frostColor(0xC2FFFFFF, 0xF22C2C2E)   // light 76%, dark 95%
val FrostSurfaceSolid: Color get() = frostColor(0xEBFFFFFF, 0xFF2C2C2E)      // light 92%, dark 100%

// Hairlines
val FrostBorderSubtle: Color get() = frostColor(0x0F000000, 0x1AFFFFFF)      // 6% / 10%
val FrostBorderStandard: Color get() = frostColor(0x1A000000, 0x2BFFFFFF)    // 10% / 17%
val FrostBorderStrong: Color get() = frostColor(0xE6FFFFFF, 0x33FFFFFF)      // 90% / 20%

// Text
val FrostTextPrimary: Color get() = frostColor(0xFF1C1C1E, 0xFFF5F5F7)
val FrostTextSecondary: Color get() = frostColor(0xFF6E6E73, 0xFFA1A1A6)
val FrostTextTertiary: Color get() = frostColor(0xFF8E8E93, 0xFF8E8E93)

// Accent (iOS blue)
val FrostAccent: Color get() = frostColor(0xFF0A84FF, 0xFF0A84FF)
val FrostAccentDim: Color get() = frostColor(0x1F0A84FF, 0x330A84FF)         // 12% / 20%
val FrostAccentBorder: Color get() = frostColor(0x400A84FF, 0x660A84FF)      // 25% / 40%

// Semantic colors (iOS system colors)
val FrostSuccess: Color get() = frostColor(0xFF34C759, 0xFF34C759)
val FrostSuccessDim: Color get() = frostColor(0x1F34C759, 0x3334C759)
val FrostSuccessBorder: Color get() = frostColor(0x4034C759, 0x6634C759)
val FrostWarning: Color get() = frostColor(0xFFFF9F0A, 0xFFFF9F0A)
val FrostWarningDim: Color get() = frostColor(0x1FFF9F0A, 0x33FF9F0A)
val FrostWarningBorder: Color get() = frostColor(0x40FF9F0A, 0x66FF9F0A)
val FrostDanger: Color get() = frostColor(0xFFFF3B30, 0xFFFF3B30)
val FrostDangerDim: Color get() = frostColor(0x1FFF3B30, 0x33FF3B30)
val FrostDangerBorder: Color get() = frostColor(0x40FF3B30, 0x66FF3B30)
val FrostInfo: Color get() = frostColor(0xFF0A84FF, 0xFF0A84FF)
val FrostInfoDim: Color get() = frostColor(0x1F0A84FF, 0x330A84FF)
val FrostInfoBorder: Color get() = frostColor(0x400A84FF, 0x660A84FF)

// Soft shadow + iOS segmented-control track
val FrostShadow: Color get() = frostColor(0x14000000, 0x66000000)
val FrostSegmentTrack: Color get() = frostColor(0xFFE3E3E8, 0xFF2C2C2E)

// ---------------------------------------------------------------------------
// Legacy aliases. Do not add new call sites that depend on the old names.
// ---------------------------------------------------------------------------

// Backgrounds formerly "Obsidian & Zinc"
val DarkBgPrimary: Color get() = FrostBg
val DarkBgSecondary: Color get() = FrostBgSecondary
val DarkSurface: Color get() = FrostSurfaceGlass
val DarkSurfaceElevated: Color get() = FrostSurfaceElevated
val DarkSurfaceHighlight: Color get() = frostColor(0xD9FFFFFF, 0x33FFFFFF)

// Borders formerly white hairline
val BorderSubtle: Color get() = FrostBorderSubtle
val BorderStandard: Color get() = FrostBorderStandard
val BorderHover: Color get() = frostColor(0x24000000, 0x40FFFFFF)

// Accent formerly Emerald
val AccentEmerald: Color get() = FrostAccent
val AccentEmeraldDim: Color get() = FrostAccentDim
val AccentEmeraldBorder: Color get() = FrostAccentBorder
val AccentEmeraldGlow: Color get() = frostColor(0x1F0A84FF, 0x330A84FF)

// Semantic palette
val SemanticDanger: Color get() = FrostDanger
val SemanticDangerDim: Color get() = FrostDangerDim
val SemanticDangerBorder: Color get() = FrostDangerBorder

val SemanticWarning: Color get() = FrostWarning
val SemanticWarningDim: Color get() = FrostWarningDim
val SemanticWarningBorder: Color get() = FrostWarningBorder

val SemanticInfo: Color get() = FrostInfo
val SemanticInfoDim: Color get() = FrostInfoDim
val SemanticInfoBorder: Color get() = FrostInfoBorder

// Slate scale, remapped to iOS neutral text/surface tones. Light values map the
// light end to "primary dark text"; the dark palette inverts the scale so the
// same aliases stay readable on charcoal glass.
val Slate50: Color get() = frostColor(0xFF1C1C1E, 0xFFF5F5F7)
val Slate100: Color get() = frostColor(0xFF2C2C2E, 0xFFE5E5EA)
val Slate200: Color get() = frostColor(0xFF3A3A3C, 0xFFD1D1D6)
val Slate300: Color get() = frostColor(0xFF48484A, 0xFFC7C7CC)
val Slate400: Color get() = frostColor(0xFF6E6E73, 0xFFA1A1A6)
val Slate500: Color get() = frostColor(0xFF8E8E93, 0xFF8E8E93)
val Slate600: Color get() = frostColor(0xFFA1A1A6, 0xFF6E6E73)
val Slate700: Color get() = frostColor(0xFFC7C7CC, 0xFF48484A)
val Slate800: Color get() = frostColor(0xFFD1D1D6, 0xFF3A3A3C)
val Slate900: Color get() = frostColor(0xFFE5E5EA, 0xFF2C2C2E)

// Backward compatibility aliases remapped to Frost tokens
val BackroundDark: Color get() = FrostBg
val SurfaceGlass: Color get() = FrostSurfaceGlass
val GlassBorder: Color get() = FrostBorderSubtle
val GlassSurface: Color get() = FrostSurfaceElevated

val NeonGreen: Color get() = FrostSuccess
val NeonBlue: Color get() = FrostAccent
val CyberCyan: Color get() = FrostAccent
val CyberYellow: Color get() = FrostWarning
val NeonRed: Color get() = FrostDanger
val CyberPink: Color get() = FrostDanger

val PrimaryBlue: Color get() = FrostAccent
val PrimaryHover: Color get() = frostColor(0xFF0071E3, 0xFF409CFF)
val AccentIndigo: Color get() = FrostAccent
val SettingsPrimary: Color get() = FrostAccent
val BackgroundDeepNavy: Color get() = FrostBg
val PurpleAccent: Color get() = FrostAccent

val Navy950: Color get() = FrostBg
val Navy900: Color get() = FrostBgSecondary
val Navy850: Color get() = FrostSurfaceGlass
val Navy800: Color get() = FrostSurfaceElevated
val Navy700: Color get() = Slate700
val Navy600: Color get() = Slate600

val SuccessGreen: Color get() = FrostSuccess
val DangerRed: Color get() = FrostDanger
val OfflineGray: Color get() = FrostTextTertiary
