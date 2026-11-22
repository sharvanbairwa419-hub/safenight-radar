package com.cityfix.safenight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * SafeNight is dark-mode first by design: a bright map would destroy night
 * vision exactly when the user needs it. The app therefore always uses the
 * dark scheme, regardless of the system setting.
 */
private val SafeNightColors = darkColorScheme(
    primary = RadarTeal,
    onPrimary = NightVoid,
    primaryContainer = RadarTealDeep,
    onPrimaryContainer = TextPrimary,
    secondary = WifiGlow,
    onSecondary = NightVoid,
    tertiary = ZoneModerate,
    background = NightVoid,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = TextMuted,
    error = DangerRed,
    onError = Color.White,
    outline = Color(0xFF2A3550)
)

@Composable
fun SafeNightTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SafeNightColors,
        typography = SafeNightTypography,
        content = content
    )
}
