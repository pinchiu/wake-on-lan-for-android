package com.example.wakeonlanhomephone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Frost is a single, calm light theme. Dynamic color is intentionally not used
// so every device looks the same.
private val FrostColorScheme = lightColorScheme(
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

@Suppress("DEPRECATION")
@Composable
fun WakeOnLanTheme(
    // Kept for source compatibility. Frost always renders the light scheme.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    // Kept for source compatibility. Dynamic color is never used.
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window

            window.statusBarColor = FrostBg.toArgb()
            window.navigationBarColor = FrostBgSecondary.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = FrostColorScheme,
        typography = Typography,
        content = content
    )
}
