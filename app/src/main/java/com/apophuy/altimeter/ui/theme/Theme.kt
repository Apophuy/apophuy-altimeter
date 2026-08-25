package com.apophuy.altimeter.ui.theme

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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.apophuy.altimeter.model.ThemeMode

val InstrumentBackground: Color @Composable get() = MaterialTheme.colorScheme.background
val InstrumentSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val InstrumentSurfaceHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val InstrumentTeal: Color @Composable get() = MaterialTheme.colorScheme.primary
val InstrumentGreen: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val InstrumentRed: Color @Composable get() = MaterialTheme.colorScheme.error
val InstrumentText: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val InstrumentMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val InstrumentAmber: Color @Composable get() = MaterialTheme.colorScheme.secondary
val InstrumentDial: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0C8),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF153E40),
    onPrimaryContainer = Color(0xFFB0F2EE),
    secondary = Color(0xFFF5B942),
    onSecondary = Color(0xFF342400),
    secondaryContainer = Color(0xFF153E40),
    onSecondaryContainer = Color(0xFFB0F2EE),
    tertiary = Color(0xFF4CAF50),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF5F7FA),
    surface = Color(0xFF151B22),
    onSurface = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFF202833),
    onSurfaceVariant = Color(0xFF9CA9B8),
    surfaceContainer = Color(0xFF10161D),
    surfaceContainerLow = Color(0xFF151B22),
    surfaceContainerHigh = Color(0xFF202833),
    surfaceContainerHighest = Color(0xFF2B3542),
    error = Color(0xFFFF5A5F),
    onError = Color(0xFF340A0C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EDE7),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF805600),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6EDE7),
    onSecondaryContainer = Color(0xFF004D49),
    tertiary = Color(0xFF2E7D32),
    background = Color(0xFFF2F6F7),
    onBackground = Color(0xFF17242D),
    surface = Color.White,
    onSurface = Color(0xFF17242D),
    surfaceVariant = Color(0xFFDDE7EA),
    onSurfaceVariant = Color(0xFF4E626F),
    surfaceContainer = Color(0xFFE8F1F3),
    surfaceContainerLow = Color(0xFFF7FAFB),
    surfaceContainerHigh = Color(0xFFE5EDF0),
    surfaceContainerHighest = Color(0xFFDDE7EA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun AltimeterTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
