package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OceanicDarkColorScheme = darkColorScheme(
    primary = OceanIceSky,
    onPrimary = OceanDark900,
    primaryContainer = OceanDark700,
    onPrimaryContainer = OceanIceWhite,
    secondary = OceanSteel,
    onSecondary = OceanIceWhite,
    secondaryContainer = Color(0xFF1E3F66),
    onSecondaryContainer = OceanIceSky,
    tertiary = OceanAqua,
    onTertiary = OceanDark900,
    tertiaryContainer = Color(0xFF163852),
    onTertiaryContainer = OceanIceWhite,
    background = OceanDark900,
    onBackground = OceanIceWhite,
    surface = OceanDark800,
    onSurface = OceanIceWhite,
    surfaceVariant = OceanDark700,
    onSurfaceVariant = OceanIceSky,
    outline = OceanBorder,
    outlineVariant = OceanDark700,
    error = OceanError,
    onError = OceanDark900,
    errorContainer = Color(0xFF4C1D24),
    onErrorContainer = OceanError
)

@Composable
fun AndroidHarnessTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OceanicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
