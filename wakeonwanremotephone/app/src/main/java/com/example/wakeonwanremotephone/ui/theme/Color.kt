package com.example.wakeonwanremotephone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette selector read by every color token below. The theme composable sets
 * this before it builds the Material color scheme, then recreates the activity
 * when the user changes the preference.
 */
object FrostCurrent {
    @Volatile
    var dark: Boolean = false
}

// ---------------------------------------------------------------------------
// Light Frost tokens. Values are the original light-only palette.
// ---------------------------------------------------------------------------

private val LightFrostBg = Color(0xFFF2F2F7)
private val LightFrostBgSecondary = Color(0xFFE9E9EF)

private val LightFrostSurfaceGlass = Color(0x94FFFFFF)      // #FFFFFF @ 58%
private val LightFrostSurfaceElevated = Color(0xC2FFFFFF)   // #FFFFFF @ 76%
private val LightFrostSurfaceSolid = Color(0xEBFFFFFF)      // #FFFFFF @ 92%
private val LightFrostSurfaceInset = Color(0xB8FFFFFF)      // #FFFFFF @ 72%

private val LightFrostBorderSubtle = Color(0x0F000000)      // #000000 @ 6%
private val LightFrostBorderStandard = Color(0x1A000000)    // #000000 @ 10%
private val LightFrostBorderHover = Color(0x29000000)       // #000000 @ 16%
private val LightFrostBorderStrong = Color(0xE6FFFFFF)      // #FFFFFF @ 90%

private val LightFrostTextPrimary = Color(0xFF1C1C1E)
private val LightFrostTextSecondary = Color(0xFF6E6E73)
private val LightFrostTextTertiary = Color(0xFF8E8E93)

private val LightFrostAccent = Color(0xFF0A84FF)            // iOS blue
private val LightFrostAccentDim = Color(0x1F0A84FF)         // #0A84FF @ 12%
private val LightFrostAccentBorder = Color(0x400A84FF)      // #0A84FF @ 25%
private val LightFrostAccentGlow = Color(0x330A84FF)        // #0A84FF @ 20%
private val LightFrostSuccess = Color(0xFF34C759)
private val LightFrostSuccessDim = Color(0x1F34C759)
private val LightFrostSuccessBorder = Color(0x4034C759)
private val LightFrostWarning = Color(0xFFFF9F0A)
private val LightFrostWarningDim = Color(0x1FFF9F0A)
private val LightFrostWarningBorder = Color(0x40FF9F0A)
private val LightFrostDanger = Color(0xFFFF3B30)
private val LightFrostDangerDim = Color(0x1FFF3B30)
private val LightFrostDangerBorder = Color(0x40FF3B30)
private val LightFrostInfo = Color(0xFF0A84FF)
private val LightFrostInfoDim = Color(0x1F0A84FF)
private val LightFrostInfoBorder = Color(0x400A84FF)

private val LightFrostTrack = Color(0xFFE3E3E8)
private val LightFrostDivider = Color(0x14000000)           // #000000 @ 8%
private val LightFrostShadow = Color(0x1F000000)            // #000000 @ 12%

private val LightFrostBlobWhite = Color(0x59FFFFFF)         // 35%
private val LightFrostBlobCool = Color(0x47D9DEE8)          // cool grey @ 28%
private val LightFrostBlobWarm = Color(0x4DE7E3DD)          // warm grey @ 30%

private val LightPrimaryHover = Color(0xFF0071E3)
private val LightDarkSurfaceHighlight = Color(0xD9FFFFFF)   // #FFFFFF @ 85%

// Slate runs from dark ink (50) to faint grey (900) in the light theme. The
// light values are the original legacy aliases; dark mode inverts the scale.
private val LightSlate50 = Color(0xFF1C1C1E)
private val LightSlate100 = Color(0xFF1C1C1E)
private val LightSlate200 = Color(0xFF1C1C1E)
private val LightSlate300 = Color(0xFF6E6E73)
private val LightSlate400 = Color(0xFF6E6E73)
private val LightSlate500 = Color(0xFF8E8E93)
private val LightSlate600 = Color(0xFF8E8E93)
private val LightSlate700 = Color(0xFF1C1C1E)
private val LightSlate800 = Color(0xFF1C1C1E)
private val LightSlate900 = Color(0xFF1C1C1E)

// ---------------------------------------------------------------------------
// Dark Frost tokens. Translucent charcoal glass on a near-black background.
// ---------------------------------------------------------------------------

private val DarkFrostBg = Color(0xFF121214)
private val DarkFrostBgSecondary = Color(0xFF1C1C1E)

private val DarkFrostSurfaceGlass = Color(0xCC1C1C1E)        // #1C1C1E @ 80%
private val DarkFrostSurfaceElevated = Color(0xF22C2C2E)     // #2C2C2E @ 95%
private val DarkFrostSurfaceSolid = Color(0xFF2C2C2E)        // opaque dialog fill
private val DarkFrostSurfaceInset = Color(0xB81C1C1E)        // inset wells

private val DarkFrostBorderSubtle = Color(0x1AFFFFFF)       // #FFFFFF @ 10%
private val DarkFrostBorderStandard = Color(0x2BFFFFFF)     // #FFFFFF @ 17%
private val DarkFrostBorderHover = Color(0x40FFFFFF)        // #FFFFFF @ 25%
private val DarkFrostBorderStrong = Color(0x33FFFFFF)       // #FFFFFF @ 20%

private val DarkFrostTextPrimary = Color(0xFFF5F5F7)
private val DarkFrostTextSecondary = Color(0xFFA1A1A6)
private val DarkFrostTextTertiary = Color(0xFF8E8E93)

private val DarkFrostAccent = Color(0xFF0A84FF)
private val DarkFrostAccentDim = Color(0x330A84FF)          // #0A84FF @ 20%
private val DarkFrostAccentBorder = Color(0x660A84FF)       // #0A84FF @ 40%
private val DarkFrostAccentGlow = Color(0x330A84FF)
private val DarkFrostSuccess = Color(0xFF34C759)
private val DarkFrostSuccessDim = Color(0x3334C759)
private val DarkFrostSuccessBorder = Color(0x6634C759)
private val DarkFrostWarning = Color(0xFFFF9F0A)
private val DarkFrostWarningDim = Color(0x33FF9F0A)
private val DarkFrostWarningBorder = Color(0x66FF9F0A)
private val DarkFrostDanger = Color(0xFFFF3B30)
private val DarkFrostDangerDim = Color(0x33FF3B30)
private val DarkFrostDangerBorder = Color(0x66FF3B30)
private val DarkFrostInfo = Color(0xFF0A84FF)
private val DarkFrostInfoDim = Color(0x330A84FF)
private val DarkFrostInfoBorder = Color(0x660A84FF)

private val DarkFrostTrack = Color(0xFF2C2C2E)
private val DarkFrostDivider = Color(0x1AFFFFFF)
private val DarkFrostShadow = Color(0x66000000)              // #000000 @ 40%

private val DarkFrostBlobWhite = Color(0x14FFFFFF)          // 8%
private val DarkFrostBlobCool = Color(0x14D9DEE8)
private val DarkFrostBlobWarm = Color(0x14E7E3DD)

private val DarkPrimaryHover = Color(0xFF409CFF)
private val DarkDarkSurfaceHighlight = Color(0xFF3A3A3C)

// The dark Slate scale is the light scale inverted, so Slate50 is the
// lightest ink and Slate900 the darkest.
private val DarkSlate50 = Color(0xFFE5E5EA)
private val DarkSlate100 = Color(0xFFD1D1D6)
private val DarkSlate200 = Color(0xFFC7C7CC)
private val DarkSlate300 = Color(0xFFA1A1A6)
private val DarkSlate400 = Color(0xFF8E8E93)
private val DarkSlate500 = Color(0xFF6E6E73)
private val DarkSlate600 = Color(0xFF48484A)
private val DarkSlate700 = Color(0xFF3A3A3C)
private val DarkSlate800 = Color(0xFF2C2C2E)
private val DarkSlate900 = Color(0xFF1C1C1E)

// ---------------------------------------------------------------------------
// Frost tokens. Every public name below is a computed property, so call sites
// keep their original shape while picking up the active palette.
// ---------------------------------------------------------------------------

// Backgrounds
val FrostBg: Color
    get() = if (FrostCurrent.dark) DarkFrostBg else LightFrostBg
val FrostBgSecondary: Color
    get() = if (FrostCurrent.dark) DarkFrostBgSecondary else LightFrostBgSecondary

// Glass surfaces (layered over the background)
val FrostSurfaceGlass: Color
    get() = if (FrostCurrent.dark) DarkFrostSurfaceGlass else LightFrostSurfaceGlass
val FrostSurfaceElevated: Color
    get() = if (FrostCurrent.dark) DarkFrostSurfaceElevated else LightFrostSurfaceElevated
val FrostSurfaceSolid: Color
    get() = if (FrostCurrent.dark) DarkFrostSurfaceSolid else LightFrostSurfaceSolid
val FrostSurfaceInset: Color
    get() = if (FrostCurrent.dark) DarkFrostSurfaceInset else LightFrostSurfaceInset

// Hairline borders
val FrostBorderSubtle: Color
    get() = if (FrostCurrent.dark) DarkFrostBorderSubtle else LightFrostBorderSubtle
val FrostBorderStandard: Color
    get() = if (FrostCurrent.dark) DarkFrostBorderStandard else LightFrostBorderStandard
val FrostBorderHover: Color
    get() = if (FrostCurrent.dark) DarkFrostBorderHover else LightFrostBorderHover
val FrostBorderStrong: Color
    get() = if (FrostCurrent.dark) DarkFrostBorderStrong else LightFrostBorderStrong

// Text
val FrostTextPrimary: Color
    get() = if (FrostCurrent.dark) DarkFrostTextPrimary else LightFrostTextPrimary
val FrostTextSecondary: Color
    get() = if (FrostCurrent.dark) DarkFrostTextSecondary else LightFrostTextSecondary
val FrostTextTertiary: Color
    get() = if (FrostCurrent.dark) DarkFrostTextTertiary else LightFrostTextTertiary

// Accent and semantics
val FrostAccent: Color
    get() = if (FrostCurrent.dark) DarkFrostAccent else LightFrostAccent
val FrostAccentDim: Color
    get() = if (FrostCurrent.dark) DarkFrostAccentDim else LightFrostAccentDim
val FrostAccentBorder: Color
    get() = if (FrostCurrent.dark) DarkFrostAccentBorder else LightFrostAccentBorder
val FrostAccentGlow: Color
    get() = if (FrostCurrent.dark) DarkFrostAccentGlow else LightFrostAccentGlow

val FrostSuccess: Color
    get() = if (FrostCurrent.dark) DarkFrostSuccess else LightFrostSuccess
val FrostSuccessDim: Color
    get() = if (FrostCurrent.dark) DarkFrostSuccessDim else LightFrostSuccessDim
val FrostSuccessBorder: Color
    get() = if (FrostCurrent.dark) DarkFrostSuccessBorder else LightFrostSuccessBorder

val FrostWarning: Color
    get() = if (FrostCurrent.dark) DarkFrostWarning else LightFrostWarning
val FrostWarningDim: Color
    get() = if (FrostCurrent.dark) DarkFrostWarningDim else LightFrostWarningDim
val FrostWarningBorder: Color
    get() = if (FrostCurrent.dark) DarkFrostWarningBorder else LightFrostWarningBorder

val FrostDanger: Color
    get() = if (FrostCurrent.dark) DarkFrostDanger else LightFrostDanger
val FrostDangerDim: Color
    get() = if (FrostCurrent.dark) DarkFrostDangerDim else LightFrostDangerDim
val FrostDangerBorder: Color
    get() = if (FrostCurrent.dark) DarkFrostDangerBorder else LightFrostDangerBorder

val FrostInfo: Color
    get() = if (FrostCurrent.dark) DarkFrostInfo else LightFrostInfo
val FrostInfoDim: Color
    get() = if (FrostCurrent.dark) DarkFrostInfoDim else LightFrostInfoDim
val FrostInfoBorder: Color
    get() = if (FrostCurrent.dark) DarkFrostInfoBorder else LightFrostInfoBorder

// Neutral track for controls (iOS segmented-control trough)
val FrostTrack: Color
    get() = if (FrostCurrent.dark) DarkFrostTrack else LightFrostTrack
val FrostSegmentTrack: Color
    get() = FrostTrack
val FrostDivider: Color
    get() = if (FrostCurrent.dark) DarkFrostDivider else LightFrostDivider

// Soft ambient shadow + decorative background blob tints
val FrostShadow: Color
    get() = if (FrostCurrent.dark) DarkFrostShadow else LightFrostShadow

val FrostBlobWhite: Color
    get() = if (FrostCurrent.dark) DarkFrostBlobWhite else LightFrostBlobWhite
val FrostBlobCool: Color
    get() = if (FrostCurrent.dark) DarkFrostBlobCool else LightFrostBlobCool
val FrostBlobWarm: Color
    get() = if (FrostCurrent.dark) DarkFrostBlobWarm else LightFrostBlobWarm

// ---------------------------------------------------------------------------
// Legacy aliases — retained so existing call sites keep compiling. All are
// computed properties that follow the active palette.
// ---------------------------------------------------------------------------

// Former dark backgrounds now map to Frost backgrounds.
val DarkBgPrimary: Color
    get() = FrostBg
val DarkBgSecondary: Color
    get() = FrostBgSecondary
val DarkSurface: Color
    get() = FrostSurfaceGlass
val DarkSurfaceElevated: Color
    get() = FrostSurfaceElevated
val DarkSurfaceHighlight: Color
    get() = if (FrostCurrent.dark) DarkDarkSurfaceHighlight else LightDarkSurfaceHighlight

// Hairline borders
val BorderSubtle: Color
    get() = FrostBorderSubtle
val BorderStandard: Color
    get() = FrostBorderStandard
val BorderHover: Color
    get() = FrostBorderHover

// Former emerald accent now maps to the Frost blue accent.
val AccentEmerald: Color
    get() = FrostAccent
val AccentEmeraldDim: Color
    get() = FrostAccentDim
val AccentEmeraldBorder: Color
    get() = FrostAccentBorder
val AccentEmeraldGlow: Color
    get() = FrostAccentGlow

// Semantic palette
val SemanticDanger: Color
    get() = FrostDanger
val SemanticDangerDim: Color
    get() = FrostDangerDim
val SemanticDangerBorder: Color
    get() = FrostDangerBorder

val SemanticWarning: Color
    get() = FrostWarning
val SemanticWarningDim: Color
    get() = FrostWarningDim
val SemanticWarningBorder: Color
    get() = FrostWarningBorder

val SemanticInfo: Color
    get() = FrostInfo
val SemanticInfoDim: Color
    get() = FrostInfoDim
val SemanticInfoBorder: Color
    get() = FrostInfoBorder

// Slate typography scale (dark ink at 50 in light mode; inverted in dark mode).
val Slate50: Color
    get() = if (FrostCurrent.dark) DarkSlate50 else LightSlate50
val Slate100: Color
    get() = if (FrostCurrent.dark) DarkSlate100 else LightSlate100
val Slate200: Color
    get() = if (FrostCurrent.dark) DarkSlate200 else LightSlate200
val Slate300: Color
    get() = if (FrostCurrent.dark) DarkSlate300 else LightSlate300
val Slate400: Color
    get() = if (FrostCurrent.dark) DarkSlate400 else LightSlate400
val Slate500: Color
    get() = if (FrostCurrent.dark) DarkSlate500 else LightSlate500
val Slate600: Color
    get() = if (FrostCurrent.dark) DarkSlate600 else LightSlate600
val Slate700: Color
    get() = if (FrostCurrent.dark) DarkSlate700 else LightSlate700
val Slate800: Color
    get() = if (FrostCurrent.dark) DarkSlate800 else LightSlate800
val Slate900: Color
    get() = if (FrostCurrent.dark) DarkSlate900 else LightSlate900

// Backward compatibility aliases for Remote Phone
val DeepBlack: Color
    get() = FrostBg
val Charcoal: Color
    get() = FrostSurfaceSolid
val GlassWhite: Color
    get() = FrostSurfaceGlass
val NeonBlue: Color
    get() = FrostAccent
val NeonGreen: Color
    get() = FrostAccent
val NeonPurple: Color
    get() = FrostAccent
val NeonRed: Color
    get() = FrostDanger
val NeonOrange: Color
    get() = FrostWarning

// Legacy navy scale (formerly dark backgrounds, now palette-following aliases).
val Navy950: Color
    get() = FrostBg
val Navy900: Color
    get() = FrostBgSecondary
val Navy850: Color
    get() = FrostSurfaceGlass
val Navy800: Color
    get() = FrostSurfaceElevated
val Navy700: Color
    get() = Slate700
val Navy600: Color
    get() = Slate600

val PrimaryHover: Color
    get() = if (FrostCurrent.dark) DarkPrimaryHover else LightPrimaryHover

val Purple80: Color
    get() = FrostAccent
val PurpleGrey80: Color
    get() = FrostTextSecondary
val Pink80: Color
    get() = FrostDanger
val Purple40: Color
    get() = FrostAccent
val PurpleGrey40: Color
    get() = FrostTextTertiary
val Pink40: Color
    get() = FrostDanger
