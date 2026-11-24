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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.GameCatalog
import com.example.data.PlayerProgression
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.StatEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ProfileOverlay(
    state: GameUiState,
    onBack: () -> Unit,
    onInspectCarInGarage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val profile = state.profile
    val currentLevel = profile.level.toInt().coerceIn(1, PlayerProgression.MAX_LEVEL)
    val xpProgress = PlayerProgression.xpProgressInCurrentLevel(profile.xp)
    val nextLevelXp = PlayerProgression.xpRequiredForLevel((currentLevel + 1).coerceAtMost(PlayerProgression.MAX_LEVEL))
    val unlockedAchSet = profile.unlockedAchievements.toSet()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag("profile_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header: Back + Driver Identity + Level/XP Bar + Cash Balance
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
                            .testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = profile.displayName.uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextPrimary
                            )
                            Surface(
                                color = NitroCyan.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, NitroCyan),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "LVL $currentLevel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NitroCyan,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .testTag("profile_level_badge")
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { xpProgress },
                                color = NitroCyan,
                                trackColor = GraphiteElevated,
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,d", profile.xp)} / ${String.format(Locale.US, "%,d", nextLevelXp)} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.testTag("profile_xp_text")
                            )
                        }
                    }
                }

                Surface(
                    color = CarbonDark,
                    border = BorderStroke(1.dp, ChampionshipGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%,d", profile.credits)} CR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ChampionshipGold
                        )
                        Text("DRIVER CASH", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Split Content: Left = Driver Stats & Vehicle Collection, Right = Achievements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Pane: Career Telemetry + 5-Car Vehicle Collection
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMetricCard("WINS", "${profile.racesWon}", StatEmerald, Modifier.weight(1f))
                        ProfileMetricCard("RACES", "${profile.racesCompleted}", NitroCyan, Modifier.weight(1f))
                        ProfileMetricCard("DIST", String.format(Locale.US, "%.1f KM", profile.totalDistanceKm), TextPrimary, Modifier.weight(1f))
                        ProfileMetricCard("DRIFT", "${profile.highestDriftCombo}", ChampionshipGold, Modifier.weight(1f))
                    }

                    Surface(
                        color = CarbonDark.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = RacingCrimson, modifier = Modifier.size(18.dp))
                                    Text("VEHICLE COLLECTION", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                }
                                Text(
                                    text = "${state.garageCars.size} / ${GameCatalog.cars.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NitroCyan,
                                    modifier = Modifier.testTag("profile_collection_count")
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(GameCatalog.cars, key = { it.id }) { car ->
                                    val owned = state.garageCars.containsKey(car.id)
                                    val equipped = profile.selectedCarId == car.id
                                    Surface(
                                        color = if (equipped) GraphiteElevated else GraphiteSurface,
                                        border = BorderStroke(1.dp, if (equipped) RacingCrimson else CarbonBorder),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onInspectCarInGarage(car.id) }
                                            .testTag("profile_car_${car.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(car.name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                                Text(car.vehicleClass.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }
                                            Text(
                                                text = when {
                                                    equipped -> "ACTIVE"
                                                    owned -> "OWNED"
                                                    currentLevel >= car.unlockLevel -> "CLAIM IN GARAGE"
                                                    else -> "LVL ${car.unlockLevel}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    equipped -> StatEmerald
                                                    owned -> NitroCyan
                                                    currentLevel >= car.unlockLevel -> StatEmerald
                                                    else -> TextSecondary
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Pane: Achievements List (10 Trackable Milestones)
                Surface(
                    color = CarbonDark.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = ChampionshipGold, modifier = Modifier.size(18.dp))
                                Text("ACHIEVEMENTS", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                            Text(
                                text = "${unlockedAchSet.size} / ${GameCatalog.achievements.size} UNLOCKED",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChampionshipGold,
                                modifier = Modifier.testTag("profile_achievements_count")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(GameCatalog.achievements, key = { it.id }) { ach ->
                                val unlocked = ach.id in unlockedAchSet
                                Surface(
                                    color = if (unlocked) GraphiteElevated else GraphiteSurface.copy(alpha = 0.72f),
                                    border = BorderStroke(1.dp, if (unlocked) StatEmerald else CarbonBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("achievement_${ach.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = if (unlocked) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                                contentDescription = if (unlocked) "Unlocked" else "Locked",
                                                tint = if (unlocked) StatEmerald else TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(ach.title, style = MaterialTheme.typography.labelLarge, color = if (unlocked) TextPrimary else TextSecondary)
                                                Text(ach.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (unlocked) "CLAIMED" else "+${ach.rewardCredits} CR",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (unlocked) StatEmerald else ChampionshipGold
                                            )
                                            Text(
                                                text = "+${ach.rewardXp} XP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NitroCyan
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMetricCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonDark.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, CarbonBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}
