package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.CareerEventSpec
import com.example.data.CareerProgressDoc
import com.example.data.GameCatalog
import com.example.data.GameMode
import com.example.data.MedalTier
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.SilverMedal
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CareerOverlay(
    state: GameUiState,
    onBack: () -> Unit,
    onLaunchEvent: (CareerEventSpec) -> Unit
) {
    BackHandler(onBack = onBack)

    val completedCount = state.careerProgress.values.count { MedalTier.fromRaw(it.medalTier) != MedalTier.NONE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.80f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                            .testTag("career_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main Menu",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("STREET CAREER", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                        Text("COMPLETE EVENTS TO EARN MEDALS & CREDITS", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                    }
                }

                Surface(
                    color = CarbonDark,
                    border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = "Medals", tint = ChampionshipGold, modifier = Modifier.size(18.dp))
                        Text(
                            text = "$completedCount / ${GameCatalog.careerEvents.size} COMPLETED",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampionshipGold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(GameCatalog.careerEvents, key = { it.id }) { event ->
                    val record = state.careerProgress[event.id]
                    CareerEventCard(
                        event = event,
                        progress = record,
                        onLaunch = { onLaunchEvent(event) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CareerEventCard(
    event: CareerEventSpec,
    progress: CareerProgressDoc?,
    onLaunch: () -> Unit
) {
    val medal = MedalTier.fromRaw(progress?.medalTier ?: "NONE")
    val medalColor = when (medal) {
        MedalTier.GOLD -> ChampionshipGold
        MedalTier.SILVER -> SilverMedal
        MedalTier.BRONZE -> BronzeMedal
        MedalTier.NONE -> TextSecondary
    }
    val modeColor = when (event.mode) {
        GameMode.STREET_RACE -> RacingCrimson
        GameMode.CHECKPOINT_RACE -> com.example.ui.theme.StatEmerald
        GameMode.TIME_TRIAL -> ChampionshipGold
        GameMode.DRIFT_CHALLENGE -> NitroCyan
        GameMode.FREE_ROAM -> NitroCyan
    }

    Surface(
        color = GraphiteSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, if (medal == MedalTier.GOLD) ChampionshipGold.copy(alpha = 0.7f) else CarbonBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("career_event_${event.id}")
            .clickable(onClick = onLaunch)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = modeColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${event.mode.title} • ${event.district}",
                        style = MaterialTheme.typography.labelSmall,
                        color = modeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = medal.label, tint = medalColor, modifier = Modifier.size(16.dp))
                    Text(medal.label, style = MaterialTheme.typography.labelSmall, color = medalColor)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(event.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(event.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val targetText = when (event.mode) {
                        GameMode.DRIFT_CHALLENGE -> "GOLD: ${String.format(Locale.US, "%,d", event.goldTarget)} PTS"
                        GameMode.STREET_RACE -> "GOLD: 1ST PLACE (4 CARS)"
                        else -> "GOLD: ${formatRaceTime(event.goldTarget)}"
                    }
                    Text(targetText, style = MaterialTheme.typography.labelSmall, color = ChampionshipGold)
                    val bestText = when {
                        progress == null -> "REWARD: +${String.format(Locale.US, "%,d", event.rewardCredits)} CR"
                        event.mode == GameMode.DRIFT_CHALLENGE -> "BEST: ${String.format(Locale.US, "%,d", progress.bestDriftScore)} PTS"
                        else -> "BEST: ${formatRaceTime(progress.bestTimeMs)}"
                    }
                    Text(bestText, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }

                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingCrimson, contentColor = TextPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Race", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RACE", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

fun formatRaceTime(ms: Long): String {
    if (ms <= 0L) return "--:--.-"
    val totalTenths = ms / 100L
    val tenths = totalTenths % 10L
    val totalSeconds = totalTenths / 10L
    val seconds = totalSeconds % 60L
    val minutes = totalSeconds / 60L
    return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
}
