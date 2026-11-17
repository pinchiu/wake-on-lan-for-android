package com.example.wakeonlanhomephone.ui.theme

import android.app.Activity
import android.os.Build
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

// Frost shares one palette definition in Color.kt. These builders read the
// current tokens, so they must be called after FrostCurrent.dark is set.
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
    tertiaryContainer = FrostInfoDim,
    onTertiaryContainer = FrostInfo,
    background = FrostBg,
    onBackground = FrostTextPrimary,
    surface = FrostSurfaceGlass,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostSurfaceElevated,
    onSurfaceVariant = FrostTextSecondary,
    surfaceTint = FrostAccent,
    inverseSurface = FrostTextPrimary,
    inverseOnSurface = FrostBg,
    outline = FrostBorderStandard,
    outlineVariant = FrostBorderSubtle,
    error = FrostDanger,
    onError = Color.White,
    errorContainer = FrostDangerDim,
    onErrorContainer = FrostDanger,
    scrim = Color(0x33000000)
)

private fun frostDarkColorScheme() = darkColorScheme(
    primary = FrostAccent,
    onPrimary = Color.White,
    primaryContainer = FrostAccentDim,
    onPrimaryContainer = FrostAccent,
    secondary = FrostTextSecondary,
    onSecondary = FrostBg,
    secondaryContainer = FrostSurfaceElevated,
    onSecondaryContainer = FrostTextPrimary,
    tertiary = FrostInfo,
    onTertiary = Color.White,
    tertiaryContainer = FrostInfoDim,
    onTertiaryContainer = FrostInfo,
    background = FrostBg,
    onBackground = FrostTextPrimary,
    surface = FrostSurfaceGlass,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostSurfaceElevated,
    onSurfaceVariant = FrostTextSecondary,
    surfaceTint = FrostAccent,
    inverseSurface = FrostTextPrimary,
    inverseOnSurface = FrostBg,
    outline = FrostBorderStandard,
    outlineVariant = FrostBorderSubtle,
    error = FrostDanger,
    onError = Color.White,
    errorContainer = FrostDangerDim,
    onErrorContainer = FrostDanger,
    scrim = Color(0xCC000000)
)

@Suppress("DEPRECATION")
@Composable
fun WakeOnLanTheme(
    // Kept for source compatibility. The persisted preference selects the mode.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    // Kept for source compatibility. Dynamic color is never used.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (ThemePreference(context).mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // Flip the shared palette before any color token or scheme is read.
    FrostCurrent.dark = dark

    val colorScheme = if (dark) frostDarkColorScheme() else frostLightColorScheme()
    val statusBarColor = FrostBg
    val navigationBarColor = FrostBgSecondary
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window

            // Android 15+ is edge-to-edge: setStatusBarColor/setNavigationBarColor
            // are no-ops and force a platform scrim. Let the Frost background
            // draw behind the transparent bars instead.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = statusBarColor.toArgb()
                window.navigationBarColor = navigationBarColor.toArgb()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
