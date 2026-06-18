package com.kyuusanq3.mixauto.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val OledBlack = Color(0xFF000000)
val DeepCharcoal = Color(0xFF121212)
val DarkSurface = Color(0xFF1C1B1F)
val ElectricCyan = Color(0xFF00E5FF)
val CyanVariant = Color(0xFF00BCD4)
val OnDark = Color(0xFFE1E1E1)
val OnAccent = Color(0xFF000000)
val ErrorRed = Color(0xFFCF6679)

val MixAutoDarkColors = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnAccent,
    primaryContainer = CyanVariant,
    onPrimaryContainer = OnDark,
    secondary = CyanVariant,
    onSecondary = OnAccent,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = OnDark,
    tertiary = ElectricCyan,
    onTertiary = OnAccent,
    background = OledBlack,
    onBackground = OnDark,
    surface = DeepCharcoal,
    onSurface = OnDark,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = OnDark,
    error = ErrorRed,
    onError = OnDark,
)
