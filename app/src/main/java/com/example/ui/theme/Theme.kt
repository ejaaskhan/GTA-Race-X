package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GtaRaceXColorScheme = darkColorScheme(
    primary = RacingCrimson,
    onPrimary = TextPrimary,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = TextPrimary,
    secondary = NitroCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = NitroCyanDim,
    onSecondaryContainer = TextPrimary,
    tertiary = ChampionshipGold,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = CarbonDark,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurface,
    onSurfaceVariant = TextSecondary,
    outline = CarbonBorder,
    error = CrimsonGlow,
    onError = TextPrimary
)

@Composable
fun GtaRaceXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GtaRaceXColorScheme,
        typography = GtaRaceXTypography,
        content = content
    )
}
