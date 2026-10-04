package com.example.wakeonwanremotephone.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian & Zinc Base (Dark Precision Hardware Aesthetic)
val DarkBgPrimary = Color(0xFF090B10)
val DarkBgSecondary = Color(0xFF10131B)
val DarkSurface = Color(0xFF151924)
val DarkSurfaceElevated = Color(0xFF1D2232)
val DarkSurfaceHighlight = Color(0xFF262C3F)

// Precision Hairline Borders
val BorderSubtle = Color(0x1AFFFFFF) // 10% white
val BorderStandard = Color(0x28FFFFFF) // 16% white
val BorderHover = Color(0x40FFFFFF) // 25% white

// Precision Single Core Accent: Emerald (#10B981)
val AccentEmerald = Color(0xFF10B981)
val AccentEmeraldDim = Color(0x1F10B981) // 12% alpha
val AccentEmeraldBorder = Color(0x4D10B981) // 30% alpha
val AccentEmeraldGlow = Color(0x3310B981) // 20% alpha

// Semantic Palette
val SemanticDanger = Color(0xFFF43F5E) // Rose/Crimson
val SemanticDangerDim = Color(0x1FF43F5E)
val SemanticDangerBorder = Color(0x4DF43F5E)

val SemanticWarning = Color(0xFFF59E0B) // Amber
val SemanticWarningDim = Color(0x1FF59E0B)

val SemanticInfo = Color(0xFF0EA5E9) // Sky
val SemanticInfoDim = Color(0x1F0EA5E9)

// High-Contrast Slate Typography Palette
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)

// Backward Compatibility Aliases for Remote Phone
val DeepBlack = DarkBgPrimary
val Charcoal = DarkSurface
val GlassWhite = Color(0x14FFFFFF)
val NeonBlue = AccentEmerald
val NeonGreen = AccentEmerald
val NeonPurple = AccentEmerald
val NeonRed = SemanticDanger
val NeonOrange = SemanticWarning

val Purple80 = AccentEmerald
val PurpleGrey80 = Slate400
val Pink80 = SemanticDanger
val Purple40 = AccentEmerald
val PurpleGrey40 = Slate600
val Pink40 = SemanticDanger