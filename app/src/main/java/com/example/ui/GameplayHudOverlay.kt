package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.GameMode
import com.example.data.MedalTier
import com.example.data.RaceResult
import com.example.data.TelemetrySnapshot
import com.example.engine.ControlInput
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.ChampionshipGold
import com.example.ui.theme.CrimsonGlow
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.SilverMedal
import com.example.ui.theme.StatEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun GameplayHudOverlay(
    state: GameUiState,
    telemetry: TelemetrySnapshot,
    controlInput: ControlInput,
    onTogglePause: () -> Unit,
    onCycleCamera: () -> Unit,
    onRecoverCar: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit,
    onRepairCar: () -> Unit = {}
) {
    var showFullMap by remember { mutableStateOf(false) }

    BackHandler {
        when {
            showFullMap -> showFullMap = false
            state.lastRaceResult != null -> onExitToMenu()
            else -> onTogglePause()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen non-blocking crimson crash impact flash vignette
        if (telemetry.impactFlashAlpha > 0.02f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("hud_impact_flash")
            ) {
                val alpha = (telemetry.impactFlashAlpha * 0.55f).coerceIn(0f, 0.58f)
                val borderStroke = size.minDimension * 0.085f
                drawRect(
                    color = RacingCrimson.copy(alpha = alpha),
                    style = Stroke(width = borderStroke)
                )
                drawRect(
                    color = RacingCrimson.copy(alpha = alpha * 0.22f)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // TOP-LEFT: Realistic Rotating GTA-Style GPS Mini-Map + District/Weather/Camera Pills & Chassis Bar
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GpsMiniMap(
                        telemetry = telemetry,
                        onExpandMap = { showFullMap = true }
                    )
                    ChassisDamageHudCard(telemetry = telemetry)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = CarbonDark.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = telemetry.districtName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("hud_district_label")
                        )
                    }
                    Surface(
                        color = CarbonDark.copy(alpha = 0.78f),
                        border = BorderStroke(
                            1.dp,
                            if (telemetry.roadWetness > 0.45f) NitroCyan else CarbonBorder
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        val wetTag = if (telemetry.roadWetness > 0.45f) " • WET" else ""
                        Text(
                            text = "${telemetry.timeOfDayLabel} • ${telemetry.weatherLabel}$wetTag",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.roadWetness > 0.45f) NitroCyan else TextSecondary,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("hud_weather_badge")
                        )
                    }
                    Surface(
                        color = CarbonDark.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, NitroCyan.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "CAM: ${state.settings.cameraMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NitroCyan,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("hud_camera_mode_label")
                        )
                    }
                    if (state.settings.showFpsCounter) {
                        Surface(
                            color = CarbonDark.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, StatEmerald.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${telemetry.fps} FPS • ${state.settings.graphicsQuality.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // TOP-CENTER: Race Event Banner, Street Race Position & Live Drift Combo Telemetry
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = CarbonDark.copy(alpha = 0.84f),
                border = BorderStroke(1.dp, RacingCrimson.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (telemetry.mode == GameMode.STREET_RACE && telemetry.totalRacers > 1) {
                        Surface(
                            color = if (telemetry.racePosition == 1) ChampionshipGold else RacingCrimson,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${formatOrdinal(telemetry.racePosition)} / ${telemetry.totalRacers}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (telemetry.racePosition == 1) ObsidianBlack else TextPrimary,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .testTag("hud_position_badge")
                            )
                        }
                    }
                    Text(
                        text = telemetry.activeEventTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    when (telemetry.mode) {
                        GameMode.FREE_ROAM -> {
                            Text(
                                text = "DRIFT: ${String.format(Locale.US, "%,d", telemetry.totalSessionDriftScore)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = NitroCyan
                            )
                        }
                        GameMode.DRIFT_CHALLENGE -> {
                            Text(
                                text = formatRaceTime(telemetry.remainingMs),
                                style = MaterialTheme.typography.labelLarge,
                                color = RacingCrimson
                            )
                            Text(
                                text = "${String.format(Locale.US, "%,d", telemetry.totalSessionDriftScore + telemetry.currentDriftPoints)} PTS",
                                style = MaterialTheme.typography.labelLarge,
                                color = ChampionshipGold
                            )
                        }
                        else -> {
                            Text(
                                text = "CP ${telemetry.checkpointIndex}/${telemetry.totalCheckpoints}",
                                style = MaterialTheme.typography.labelLarge,
                                color = NitroCyan
                            )
                            Text(
                                text = formatRaceTime(telemetry.elapsedMs),
                                style = MaterialTheme.typography.labelLarge,
                                color = ChampionshipGold
                            )
                        }
                    }
                }
            }

            if (telemetry.currentDriftPoints > 25L) {
                Surface(
                    color = RacingCrimson.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, ChampionshipGold),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "${telemetry.driftMultiplier}x DRIFT COMBO  +${String.format(Locale.US, "%,d", telemetry.currentDriftPoints)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }

            if (telemetry.repairedNoticeActive) {
                Surface(
                    color = StatEmerald.copy(alpha = 0.22f),
                    border = BorderStroke(1.5.dp, StatEmerald),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "REPAIR BAY • 100% INTEGRITY",
                        style = MaterialTheme.typography.labelLarge,
                        color = StatEmerald,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .testTag("hud_repaired_notice")
                    )
                }
            }
        }

        // CENTER: 3 - 2 - 1 - GO! Motorsport Starting Lights Countdown
        val countdown = telemetry.countdownLabel
        if (countdown != null && !state.paused && state.lastRaceResult == null) {
            val isGo = countdown == "GO!"
            val accent = when (countdown) {
                "GO!" -> StatEmerald
                "1" -> ChampionshipGold
                else -> RacingCrimson
            }
            Surface(
                color = ObsidianBlack.copy(alpha = 0.84f),
                border = BorderStroke(2.dp, accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 48.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isGo) "LAUNCH!" else "GET READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.displayLarge,
                        color = if (isGo) StatEmerald else TextPrimary,
                        modifier = Modifier.testTag("hud_countdown_text")
                    )
                }
            }
        }

        // TOP-RIGHT: Quick Repair, Camera Switch, Reset Car, and Pause Buttons
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDamaged = telemetry.healthNormalized < 0.99f
            HudIconButton(
                icon = Icons.Filled.Build,
                contentDesc = stringResource(R.string.hud_repair),
                tag = "hud_repair_button",
                accentColor = if (isDamaged) StatEmerald else CarbonBorder,
                iconTint = if (isDamaged) StatEmerald else TextPrimary,
                onClick = onRepairCar
            )
            HudIconButton(
                icon = Icons.Filled.Videocam,
                contentDesc = "Switch Camera (${state.settings.cameraMode.label})",
                tag = "hud_camera_button",
                onClick = onCycleCamera
            )
            HudIconButton(
                icon = Icons.Filled.RestartAlt,
                contentDesc = "Recover Car to Road",
                tag = "hud_recover_button",
                onClick = onRecoverCar
            )
            HudIconButton(
                icon = Icons.Filled.Pause,
                contentDesc = "Pause Race",
                tag = "hud_pause_button",
                onClick = onTogglePause
            )
        }

        // BOTTOM-LEFT: Ergonomic Simultaneous Multi-Touch STEER LEFT & STEER RIGHT Pads
        Row(
            modifier = Modifier.align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            MultiTouchControlPad(
                label = "LEFT",
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                accentColor = RacingCrimson,
                width = 88.dp,
                height = 78.dp,
                tag = "steer_left_button",
                onPressedChange = { controlInput.steerLeft = it }
            )
            MultiTouchControlPad(
                label = "RIGHT",
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                accentColor = RacingCrimson,
                width = 88.dp,
                height = 78.dp,
                tag = "steer_right_button",
                onPressedChange = { controlInput.steerRight = it }
            )
        }

        // BOTTOM-CENTER: Actual Physics Speedometer, Live RPM Tachometer, Gear & Nitro Gauge
        SpeedometerCluster(
            telemetry = telemetry,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )

        // BOTTOM-RIGHT: Ergonomic Simultaneous Multi-Touch HANDBRAKE, NITRO, BRAKE & GAS Pedals
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MultiTouchControlPad(
                    label = stringResource(R.string.hud_drift),
                    icon = null,
                    accentColor = ChampionshipGold,
                    width = 80.dp,
                    height = 56.dp,
                    tag = "handbrake_button",
                    onPressedChange = { controlInput.handbrake = it }
                )
                MultiTouchControlPad(
                    label = stringResource(R.string.hud_nitro),
                    icon = Icons.Filled.Bolt,
                    accentColor = NitroCyan,
                    width = 80.dp,
                    height = 56.dp,
                    tag = "nitro_button",
                    onPressedChange = { controlInput.nitro = it }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MultiTouchControlPad(
                    label = stringResource(R.string.hud_brake),
                    icon = null,
                    accentColor = CrimsonGlow,
                    width = 80.dp,
                    height = 82.dp,
                    tag = "brake_button",
                    onPressedChange = { controlInput.brake = it }
                )
                MultiTouchControlPad(
                    label = stringResource(R.string.hud_gas),
                    icon = null,
                    accentColor = StatEmerald,
                    width = 88.dp,
                    height = 94.dp,
                    tag = "gas_button",
                    onPressedChange = { controlInput.throttle = it }
                )
            }
        }
        }
    }

    // Interactive Full Satellite World Map Overlay (opened by tapping the GPS Mini-Map)
    if (showFullMap && !state.paused && state.lastRaceResult == null) {
        FullWorldMapModal(
            telemetry = telemetry,
            onDismiss = { showFullMap = false }
        )
    }

    // Pause Overlay Modal
    if (state.paused && state.lastRaceResult == null) {
        PauseModal(
            eventTitle = telemetry.activeEventTitle,
            onResume = onTogglePause,
            onRestart = onRestart,
            onMainMenu = onExitToMenu
        )
    }

    // Race / Drift Finished Podium Modal
    if (state.lastRaceResult != null) {
        RaceResultModal(
            result = state.lastRaceResult,
            onReplay = onRestart,
            onMainMenu = onExitToMenu
        )
    }
}

/**
 * True multi-pointer touch pad tracking independent pointer IDs inside this button's bounds,
 * guaranteeing simultaneous left-hand steering + right-hand accelerator/brake/handbrake/nitro
 * inputs without pointer theft or stuck states.
 */
@Composable
private fun MultiTouchControlPad(
    label: String,
    icon: ImageVector?,
    accentColor: Color,
    width: Dp,
    height: Dp,
    tag: String,
    onPressedChange: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onPressedChange(false) }
    }

    Surface(
        color = if (pressed) accentColor.copy(alpha = 0.78f) else CarbonDark.copy(alpha = 0.74f),
        border = BorderStroke(if (pressed) 2.dp else 1.5.dp, if (pressed) TextPrimary else accentColor.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(width)
            .height(height)
            .testTag(tag)
            .pointerInput(tag) {
                val activePointers = mutableSetOf<PointerId>()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        for (change in event.changes) {
                            val inside = change.position.x in -16f..(size.width + 16f) &&
                                change.position.y in -16f..(size.height + 16f)
                            if (change.pressed && inside) {
                                activePointers.add(change.id)
                            } else {
                                activePointers.remove(change.id)
                            }
                        }
                        val isDown = activePointers.isNotEmpty()
                        if (isDown != pressed) {
                            pressed = isDown
                            onPressedChange(isDown)
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (pressed) ObsidianBlack else TextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (pressed) ObsidianBlack else TextPrimary
            )
        }
    }
}

@Composable
private fun SpeedometerCluster(
    telemetry: TelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val isRedline = telemetry.engineRpm >= 7600
    val borderCol = when {
        telemetry.gearShiftFlash -> NitroCyan
        isRedline -> RacingCrimson
        telemetry.handbrakeActive -> ChampionshipGold
        else -> CarbonBorder
    }

    Surface(
        color = CarbonDark.copy(alpha = 0.82f),
        border = BorderStroke(if (isRedline || telemetry.gearShiftFlash) 1.5.dp else 1.dp, borderCol),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.testTag("hud_speedometer_cluster")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Circular Live RPM Arc + Gear Indicator with Shift Flash
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    val inset = 5.dp.toPx()
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = GraphiteElevated,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    val rpmColor = when {
                        telemetry.rpmNormalized > 0.84f -> RacingCrimson
                        telemetry.rpmNormalized > 0.65f -> ChampionshipGold
                        else -> NitroCyan
                    }
                    drawArc(
                        color = rpmColor,
                        startAngle = 135f,
                        sweepAngle = 270f * telemetry.rpmNormalized.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = telemetry.gearLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = when {
                            telemetry.gearLabel == "R" -> RacingCrimson
                            telemetry.gearShiftFlash -> NitroCyan
                            else -> TextPrimary
                        },
                        modifier = Modifier.testTag("hud_gear_text")
                    )
                    Text(
                        text = "${telemetry.engineRpm}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRedline) RacingCrimson else TextSecondary,
                        modifier = Modifier.testTag("hud_rpm_text")
                    )
                }
            }

            // Actual Physics Speedometer (KM/H) + Nitro Gauge
            Column(modifier = Modifier.width(122.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = telemetry.speedKmh.toString().padStart(3, '0'),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                        modifier = Modifier.testTag("hud_speed_text")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "KM/H",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (telemetry.handbrakeActive) "H-BRAKE" else "NITRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.handbrakeActive) ChampionshipGold else NitroCyan
                    )
                    Text(
                        text = "${(telemetry.nitroNormalized * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.testTag("hud_nitro_text")
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { telemetry.nitroNormalized.coerceIn(0f, 1f) },
                    color = NitroCyan,
                    trackColor = GraphiteElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun ChassisDamageHudCard(telemetry: TelemetrySnapshot) {
    val healthPct = (telemetry.healthNormalized.coerceIn(0f, 1f) * 100f).toInt()
    val statusColor = when {
        telemetry.healthNormalized >= 0.70f -> StatEmerald
        telemetry.healthNormalized >= 0.38f -> ChampionshipGold
        else -> RacingCrimson
    }

    fun zoneColor(zoneDmg: Float): Color = when {
        zoneDmg >= 0.65f -> RacingCrimson
        zoneDmg >= 0.25f -> ChampionshipGold
        else -> StatEmerald
    }

    Surface(
        color = CarbonDark.copy(alpha = 0.84f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.78f)),
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier
            .width(146.dp)
            .testTag("hud_health_bar")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = telemetry.damageStatusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.testTag("hud_damage_status_label")
                )
                Text(
                    text = "$healthPct%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier.testTag("hud_health_pct_text")
                )
            }
            LinearProgressIndicator(
                progress = { telemetry.healthNormalized.coerceIn(0f, 1f) },
                color = statusColor,
                trackColor = GraphiteElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DamageZoneBadge("F", zoneColor(telemetry.frontDamage))
                DamageZoneBadge("R", zoneColor(telemetry.rearDamage))
                DamageZoneBadge("L", zoneColor(telemetry.leftDamage))
                DamageZoneBadge("RT", zoneColor(telemetry.rightDamage))
            }
        }
    }
}

@Composable
private fun DamageZoneBadge(code: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

private fun formatOrdinal(pos: Int): String = when (pos) {
    1 -> "1ST"
    2 -> "2ND"
    3 -> "3RD"
    else -> "${pos}TH"
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDesc: String,
    tag: String,
    accentColor: Color = CarbonBorder,
    iconTint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        color = CarbonDark.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, accentColor),
        shape = CircleShape
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = contentDesc, tint = iconTint)
        }
    }
}

@Composable
private fun PauseModal(
    eventTitle: String,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CarbonDark,
            border = BorderStroke(1.5.dp, RacingCrimson),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.hud_pause), style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                Text(eventTitle, style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = RacingCrimson, contentColor = TextPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_resume_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.hud_resume), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = GraphiteSurface, contentColor = TextPrimary),
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_restart_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Restart")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.hud_restart), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onMainMenu,
                    colors = ButtonDefaults.buttonColors(containerColor = GraphiteSurface, contentColor = TextSecondary),
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_menu_button")
                ) {
                    Text(stringResource(R.string.hud_main_menu), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun RaceResultModal(
    result: RaceResult,
    onReplay: () -> Unit,
    onMainMenu: () -> Unit
) {
    val medalColor = when (result.medal) {
        MedalTier.GOLD -> ChampionshipGold
        MedalTier.SILVER -> SilverMedal
        MedalTier.BRONZE -> BronzeMedal
        MedalTier.NONE -> TextSecondary
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CarbonDark,
            border = BorderStroke(2.dp, medalColor),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .width(480.dp)
                .testTag("mission_complete_modal")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = result.medal.label, tint = medalColor, modifier = Modifier.size(36.dp))
                val headline = if (result.mode == GameMode.STREET_RACE) {
                    "${formatOrdinal(result.finishPosition)} PLACE • ${result.medal.label}"
                } else {
                    "MISSION COMPLETE • ${result.medal.label}"
                }
                Text(headline, style = MaterialTheme.typography.headlineLarge, color = medalColor)
                Text(result.eventTitle, style = MaterialTheme.typography.labelLarge, color = TextPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (result.mode == GameMode.STREET_RACE) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RANK", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = "${formatOrdinal(result.finishPosition)} / ${result.totalRacers}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = medalColor
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (result.mode == GameMode.DRIFT_CHALLENGE) "DRIFT SCORE" else "TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = if (result.mode == GameMode.DRIFT_CHALLENGE) {
                                String.format(Locale.US, "%,d", result.driftScore)
                            } else {
                                formatRaceTime(result.elapsedMs)
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = NitroCyan
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CASH", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "+${String.format(Locale.US, "%,d", result.creditsEarned)} CR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ChampionshipGold
                        )
                    }
                    if (result.xpEarned > 0L) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("XP", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                text = "+${String.format(Locale.US, "%,d", result.xpEarned)} XP",
                                style = MaterialTheme.typography.headlineMedium,
                                color = NitroCyan,
                                modifier = Modifier.testTag("result_xp_text")
                            )
                        }
                    }
                }

                if (result.leveledUpTo != null || result.newlyUnlockedAchievements.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (result.leveledUpTo != null) {
                            Surface(
                                color = StatEmerald.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, StatEmerald),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "LEVEL UP • LVL ${result.leveledUpTo}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatEmerald,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("result_level_up_badge")
                                )
                            }
                        }
                        if (result.newlyUnlockedAchievements.isNotEmpty()) {
                            Surface(
                                color = ChampionshipGold.copy(alpha = 0.20f),
                                border = BorderStroke(1.dp, ChampionshipGold),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "UNLOCKED: ${result.newlyUnlockedAchievements.joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampionshipGold,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                        .testTag("result_achievements_badge")
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onReplay,
                        colors = ButtonDefaults.buttonColors(containerColor = RacingCrimson, contentColor = TextPrimary),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("result_replay_button")
                    ) {
                        Text("RACE AGAIN", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onMainMenu,
                        colors = ButtonDefaults.buttonColors(containerColor = GraphiteSurface, contentColor = TextPrimary),
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("result_menu_button")
                    ) {
                        Text("MENU", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
