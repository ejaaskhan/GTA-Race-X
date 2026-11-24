package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.GameMode
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonGlow
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.StatEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlayModeOverlay(
    activeCarName: String,
    onBack: () -> Unit,
    onLaunchMode: (GameMode) -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedMode by remember { mutableStateOf(GameMode.FREE_ROAM) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.74f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("play_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main Menu",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SELECT RACE MODE", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                        Text("ACTIVE VEHICLE: $activeCarName • 5 MODES • 7 CITY DISTRICTS", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                    }
                }

                Button(
                    onClick = { onLaunchMode(selectedMode) },
                    colors = ButtonDefaults.buttonColors(containerColor = RacingCrimson, contentColor = TextPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("launch_selected_mode_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Drive Now", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DRIVE NOW", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5 Playable Game Modes in a crisp horizontal landscape grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeCard(
                    mode = GameMode.FREE_ROAM,
                    icon = Icons.Filled.Explore,
                    accent = NitroCyan,
                    badge = "7 ZONES",
                    selected = selectedMode == GameMode.FREE_ROAM,
                    onSelect = { selectedMode = GameMode.FREE_ROAM },
                    onDoubleLaunch = { onLaunchMode(GameMode.FREE_ROAM) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    mode = GameMode.STREET_RACE,
                    icon = Icons.Filled.EmojiEvents,
                    accent = RacingCrimson,
                    badge = "4 CARS",
                    selected = selectedMode == GameMode.STREET_RACE,
                    onSelect = { selectedMode = GameMode.STREET_RACE },
                    onDoubleLaunch = { onLaunchMode(GameMode.STREET_RACE) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    mode = GameMode.TIME_TRIAL,
                    icon = Icons.Filled.Timer,
                    accent = ChampionshipGold,
                    badge = "BEAT GOLD",
                    selected = selectedMode == GameMode.TIME_TRIAL,
                    onSelect = { selectedMode = GameMode.TIME_TRIAL },
                    onDoubleLaunch = { onLaunchMode(GameMode.TIME_TRIAL) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    mode = GameMode.CHECKPOINT_RACE,
                    icon = Icons.Filled.Flag,
                    accent = StatEmerald,
                    badge = "GATES",
                    selected = selectedMode == GameMode.CHECKPOINT_RACE,
                    onSelect = { selectedMode = GameMode.CHECKPOINT_RACE },
                    onDoubleLaunch = { onLaunchMode(GameMode.CHECKPOINT_RACE) },
                    modifier = Modifier.weight(1f)
                )
                ModeCard(
                    mode = GameMode.DRIFT_CHALLENGE,
                    icon = Icons.Filled.LocalFireDepartment,
                    accent = CrimsonGlow,
                    badge = "60s COMBO",
                    selected = selectedMode == GameMode.DRIFT_CHALLENGE,
                    onSelect = { selectedMode = GameMode.DRIFT_CHALLENGE },
                    onDoubleLaunch = { onLaunchMode(GameMode.DRIFT_CHALLENGE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: GameMode,
    icon: ImageVector,
    accent: Color,
    badge: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onDoubleLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) GraphiteSurface.copy(alpha = 0.95f) else CarbonDark.copy(alpha = 0.82f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else CarbonBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxHeight()
            .testTag("mode_card_${mode.name.lowercase()}")
            .clickable {
                if (selected) onDoubleLaunch() else onSelect()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = mode.title,
                        tint = accent,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Surface(
                    color = ObsidianBlack.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 4
                )
            }

            Text(
                text = if (selected) "TAP TO LAUNCH" else "SELECT",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else TextSecondary
            )
        }
    }
}
