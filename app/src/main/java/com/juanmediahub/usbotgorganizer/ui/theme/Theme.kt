package com.juanmediahub.usbotgorganizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.juanmediahub.usbotgorganizer.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F4C5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0EBF2),
    secondary = Color(0xFF146C80),
    background = Color(0xFFF6FBFC),
    surface = Color(0xFFF6FBFC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AD4E8),
    onPrimary = Color(0xFF003440),
    primaryContainer = Color(0xFF0F4C5C),
    secondary = Color(0xFF9AD7E4),
    background = Color(0xFF0E1A1D),
    surface = Color(0xFF0E1A1D)
)

@Composable
fun OtgExplorerTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
