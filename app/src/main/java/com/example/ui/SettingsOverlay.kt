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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CameraMode
import com.example.data.GraphicsQuality
import com.example.data.TimeOfDayPreset
import com.example.data.WeatherPreset
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.NitroCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.RacingCrimson
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SettingsOverlay(
    state: GameUiState,
    onBack: () -> Unit,
    onSetQuality: (GraphicsQuality) -> Unit,
    onSetCamera: (CameraMode) -> Unit,
    onSetTimeOfDay: (TimeOfDayPreset) -> Unit = {},
    onSetWeather: (WeatherPreset) -> Unit = {},
    onSetSensitivity: (Float) -> Unit,
    onSetAudio: (Boolean) -> Unit,
    onSetShowFps: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onSetMusic: (Boolean) -> Unit = {},
    onSetSfx: (Boolean) -> Unit = onSetAudio
) {
    BackHandler(onBack = onBack)
    val settings = state.settings

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack.copy(alpha = 0.84f))
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
                            .testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("SETTINGS", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                        Text("GRAPHICS LOD, CAMERA, CONTROLS & CLOUD ACCOUNT", style = MaterialTheme.typography.labelSmall, color = NitroCyan)
                    }
                }

                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = GraphiteSurface, contentColor = RacingCrimson),
                    border = BorderStroke(1.dp, RacingCrimson),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("settings_sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Graphics & Camera
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsSectionCard(title = "GRAPHICS QUALITY & LOD") {
                        Text(
                            text = settings.graphicsQuality.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NitroCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GraphicsQuality.entries.forEach { q ->
                                OptionChip(
                                    label = q.label,
                                    selected = settings.graphicsQuality == q,
                                    tag = "quality_${q.name.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                ) { onSetQuality(q) }
                            }
                        }
                    }

                    SettingsSectionCard(title = "DEFAULT CAMERA ANGLE") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CameraMode.entries.forEach { cam ->
                                OptionChip(
                                    label = cam.label,
                                    selected = settings.cameraMode == cam,
                                    tag = "camera_${cam.name.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                ) { onSetCamera(cam) }
                            }
                        }
                    }

                    SettingsSectionCard(title = "DAY / NIGHT CYCLE") {
                        Text(
                            text = "Auto cycles through Dawn, Day, Sunset & Midnight or lock a preset.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TimeOfDayPreset.entries.forEach { preset ->
                                OptionChip(
                                    label = preset.label,
                                    selected = settings.timeOfDayPreset == preset,
                                    tag = "time_${preset.name.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                ) { onSetTimeOfDay(preset) }
                            }
                        }
                    }

                    SettingsSectionCard(title = "WEATHER & WET-ROAD GRIP") {
                        Text(
                            text = "Rain lowers asphalt grip for longer wet drifts and adds 3D rain streaks.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            WeatherPreset.entries.forEach { preset ->
                                OptionChip(
                                    label = preset.label,
                                    selected = settings.weatherPreset == preset,
                                    tag = "weather_${preset.name.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                ) { onSetWeather(preset) }
                            }
                        }
                    }
                }

                // Right Column: Steering Sensitivity, Audio, FPS & Cloud Account
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsSectionCard(title = "STEERING SENSITIVITY") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Touch steering responsiveness", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text(
                                String.format(Locale.US, "%.2fx", settings.steeringSensitivity),
                                style = MaterialTheme.typography.labelLarge,
                                color = NitroCyan
                            )
                        }
                        Slider(
                            value = settings.steeringSensitivity,
                            onValueChange = onSetSensitivity,
                            valueRange = 0.6f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = RacingCrimson,
                                activeTrackColor = RacingCrimson,
                                inactiveTrackColor = CarbonBorder
                            ),
                            modifier = Modifier.testTag("steering_sensitivity_slider")
                        )
                    }

                    SettingsSectionCard(title = "AUDIO & TELEMETRY") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.settings_music_title), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Text(stringResource(R.string.settings_music_desc), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Switch(
                                checked = settings.musicEnabled,
                                onCheckedChange = onSetMusic,
                                colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = NitroCyan),
                                modifier = Modifier.testTag("music_toggle")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(R.string.settings_sfx_title), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Text(stringResource(R.string.settings_sfx_desc), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Switch(
                                checked = settings.sfxEnabled,
                                onCheckedChange = { enabled ->
                                    onSetSfx(enabled)
                                    onSetAudio(enabled)
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = RacingCrimson),
                                modifier = Modifier.testTag("audio_toggle")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("SHOW LIVE FPS COUNTER", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Text("Display live frame rate in racing HUD", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Switch(
                                checked = settings.showFpsCounter,
                                onCheckedChange = onSetShowFps,
                                colors = SwitchDefaults.colors(checkedThumbColor = TextPrimary, checkedTrackColor = NitroCyan),
                                modifier = Modifier.testTag("fps_toggle")
                            )
                        }
                    }

                    SettingsSectionCard(title = "CLOUD PROFILE") {
                        Text("DRIVER: ${state.profile.displayName}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                        Text(
                            "Garage fleet, tuning levels, and career medals automatically sync via Google Sign-In & Cloud Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = CarbonDark.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, CarbonBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = RacingCrimson)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) RacingCrimson else GraphiteSurface,
        border = BorderStroke(1.dp, if (selected) RacingCrimson else CarbonBorder),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
            .height(48.dp)
            .testTag(tag)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
