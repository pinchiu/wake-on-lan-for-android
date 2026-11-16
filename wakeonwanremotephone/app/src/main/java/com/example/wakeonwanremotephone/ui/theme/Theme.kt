package com.example.wakeonwanremotephone.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Frost is a light-only design: warm grey background, translucent white glass,
 * dark text and a single iOS-blue accent.
 */
private val FrostLightColorScheme = lightColorScheme(
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

@Suppress("DEPRECATION")
@Composable
fun WakeOnWanRemotePhoneTheme(
    // Retained for source compatibility. Frost is a light-only design, so both
    // flags are ignored and the app always renders the light scheme.
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            // Dark status-bar / navigation-bar icons on the light Frost surface.
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = FrostLightColorScheme,
        typography = Typography,
        content = content
    )
}
