package com.example.wakeonwanremotephone.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Frost light scheme: warm grey background, translucent white glass, dark text
 * and a single iOS-blue accent.
 */
private fun frostLightColorScheme() = lightColorScheme(
    primary = FrostAccent,
    onPrimary = Color.White,
    primaryContainer = FrostAccentDim,
    onPrimaryContainer = FrostAccent,
    secondary = FrostTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = FrostSurfaceElevated,
    onSecondaryContainer = FrostTextPrimary,
    tertiary = FrostInfo,
    onTertiary = Color.White,
    background = FrostBg,
    onBackground = FrostTextPrimary,
    surface = FrostSurfaceGlass,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostSurfaceElevated,
    onSurfaceVariant = FrostTextSecondary,
    surfaceTint = FrostAccent,
    inverseSurface = FrostTextPrimary,
    inverseOnSurface = FrostBg,
    error = FrostDanger,
    onError = Color.White,
    outline = FrostBorderStandard,
    outlineVariant = FrostBorderSubtle,
    scrim = Color(0x66000000)
)

/**
 * Frost dark scheme: near-black background, translucent charcoal glass, light
 * text and the same iOS-blue accent.
 */
private fun frostDarkColorScheme() = darkColorScheme(
    primary = FrostAccent,
    onPrimary = Color.White,
    primaryContainer = FrostAccentDim,
    onPrimaryContainer = FrostAccent,
    secondary = FrostTextSecondary,
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = FrostSurfaceElevated,
    onSecondaryContainer = FrostTextPrimary,
    tertiary = FrostInfo,
    onTertiary = Color.White,
    background = FrostBg,
    onBackground = FrostTextPrimary,
    surface = FrostSurfaceGlass,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostSurfaceElevated,
    onSurfaceVariant = FrostTextSecondary,
    surfaceTint = FrostAccent,
    inverseSurface = FrostTextPrimary,
    inverseOnSurface = FrostBg,
    error = FrostDanger,
    onError = Color.White,
    outline = FrostBorderStandard,
    outlineVariant = FrostBorderSubtle,
    scrim = Color(0x66000000)
)

@Suppress("DEPRECATION")
@Composable
fun WakeOnWanRemotePhoneTheme(
    // Retained for source compatibility. The persisted appearance preference
    // is authoritative; SYSTEM resolves through isSystemInDarkTheme().
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePreference(context).mode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    // Palette tokens are plain top-level properties, so the flag must be set
    // before the Material scheme and the content are composed.
    FrostCurrent.dark = dark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = findActivity(view.context)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = if (dark) frostDarkColorScheme() else frostLightColorScheme(),
        typography = Typography,
        content = content
    )
}

/** Unwraps a (possibly wrapped) Compose context to the hosting Activity. */
private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
