package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameCatalog
import com.example.data.GameScreen
import com.example.data.MedalTier
import com.example.data.PlayerProgression
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonGlow
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun MainMenuOverlay(
    state: GameUiState,
    onNavigate: (GameScreen) -> Unit
) {
    val goldMedals = state.careerProgress.values.count { MedalTier.fromRaw(it.medalTier) == MedalTier.GOLD }
    val totalMedals = state.careerProgress.values.count { MedalTier.fromRaw(it.medalTier) != MedalTier.NONE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    0.0f to ObsidianBlack.copy(alpha = 0.88f),
                    0.44f to ObsidianBlack.copy(alpha = 0.42f),
                    1.0f to Color.Transparent
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Left asymmetrical motorsport command rail
        Column(
            modifier = Modifier
                .width(292.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Brand Header
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "GTA RACE ",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.displayMedium,
                        color = RacingCrimson
                    )
                }
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = NitroCyan
                )
            }

            // 4 Primary Menu Tiles: PLAY, GARAGE, CAREER, SETTINGS
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuActionCard(
                    title = stringResource(R.string.menu_play),
                    subtitle = "Free Roam • Street Races • Drift",
                    icon = Icons.Filled.PlayArrow,
                    accentColor = RacingCrimson,
                    highlighted = true,
                    tag = "menu_play_button",
                    onClick = { onNavigate(GameScreen.PLAY_SELECTOR) }
                )
                MenuActionCard(
                    title = stringResource(R.string.menu_garage),
                    subtitle = "${state.activeCarBuild.spec.name} • ${state.garageCars.size}/${GameCatalog.cars.size} Cars",
                    icon = Icons.Filled.Build,
                    accentColor = NitroCyan,
                    highlighted = false,
                    tag = "menu_garage_button",
                    onClick = { onNavigate(GameScreen.GARAGE) }
                )
                MenuActionCard(
                    title = stringResource(R.string.menu_career),
                    subtitle = "$totalMedals / ${GameCatalog.careerEvents.size} Events • $goldMedals Gold",
                    icon = Icons.Filled.EmojiEvents,
                    accentColor = ChampionshipGold,
                    highlighted = false,
                    tag = "menu_career_button",
                    onClick = { onNavigate(GameScreen.CAREER) }
                )
                MenuActionCard(
                    title = stringResource(R.string.menu_settings),
                    subtitle = "${state.settings.graphicsQuality.label} Graphics • Controls • Audio",
                    icon = Icons.Filled.Settings,
                    accentColor = TextSecondary,
                    highlighted = false,
                    tag = "menu_settings_button",
                    onClick = { onNavigate(GameScreen.SETTINGS) }
                )
            }

            // Bottom telemetry footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatMiniPill("DIST", String.format(Locale.US, "%.1f KM", state.profile.totalDistanceKm))
                StatMiniPill("BEST DRIFT", "${state.profile.highestDriftCombo}")
            }
        }

        // Top-Right Tappable Driver Profile, Level/XP Bar & Credits Badge (opens Profile & Achievements)
        val xpProgress = PlayerProgression.xpProgressInCurrentLevel(state.profile.xp)
        Surface(
            color = CarbonDark.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, NitroCyan.copy(alpha = 0.55f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { onNavigate(GameScreen.PROFILE) }
                .testTag("menu_profile_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = NitroCyan.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, NitroCyan),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LVL ${state.profile.level}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NitroCyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = state.profile.displayName.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { xpProgress },
                            color = NitroCyan,
                            trackColor = GraphiteSurface,
                            modifier = Modifier
                                .width(84.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "PROFILE & AWARDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = NitroCyan
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(CarbonBorder)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format(Locale.US, "%,d", state.profile.credits)} CR",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ChampionshipGold
                    )
                    Text(
                        text = "${state.profile.unlockedAchievements.size}/${GameCatalog.achievements.size} AWARDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Bottom-Right Active Car Spec Badge over live 3D rotating vehicle
        Surface(
            color = CarbonDark.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, RacingCrimson.copy(alpha = 0.65f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = "Active car speed",
                    tint = RacingCrimson,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = state.activeCarBuild.spec.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "TOP ${state.activeCarBuild.effectiveTopSpeedKmh.toInt()} KM/H  •  ${state.activeCarBuild.spec.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    highlighted: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) RacingCrimson.copy(alpha = 0.92f) else GraphiteSurface.copy(alpha = 0.86f),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = if (highlighted) CrimsonGlow else CarbonBorder
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .background(if (highlighted) TextPrimary else accentColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (highlighted) TextPrimary else accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (highlighted) TextPrimary.copy(alpha = 0.88f) else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatMiniPill(label: String, value: String) {
    Surface(
        color = CarbonDark.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, CarbonBorder),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = value, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}
