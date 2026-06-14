package com.example.aqua_control.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AquaSecondary,
    onPrimary = OceanPrimaryDark,
    secondary = AlertAmber,
    tertiary = AlertRed,
    background = OceanPrimaryDark,
    surface = Color(0xFF0E3B40),
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    secondary = AquaSecondary,
    onSecondary = OceanPrimaryDark,
    tertiary = AlertAmber,
    background = TankSurface,
    surface = Color.White,
    onBackground = InkPrimary,
    onSurface = InkPrimary,
    error = AlertRed,
)

@Composable
fun AquaControlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
