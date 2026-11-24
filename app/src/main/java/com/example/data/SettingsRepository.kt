package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gta_race_x_settings"
)

class SettingsRepository(private val context: Context) {

    private val keyQuality = stringPreferencesKey("graphics_quality")
    private val keyCamera = stringPreferencesKey("camera_mode")
    private val keyTimeOfDay = stringPreferencesKey("time_of_day_preset")
    private val keyWeather = stringPreferencesKey("weather_preset")
    private val keySensitivity = floatPreferencesKey("steering_sensitivity")
    private val keyAudio = booleanPreferencesKey("engine_audio_enabled")
    private val keyMusic = booleanPreferencesKey("music_enabled")
    private val keySfx = booleanPreferencesKey("sfx_enabled")
    private val keyShowFps = booleanPreferencesKey("show_fps_counter")

    val settingsFlow: Flow<DeviceSettings> = context.settingsDataStore.data.map { prefs ->
        val sfx = prefs[keySfx] ?: prefs[keyAudio] ?: true
        DeviceSettings(
            graphicsQuality = GraphicsQuality.entries.firstOrNull {
                it.name == prefs[keyQuality]
            } ?: GraphicsQuality.HIGH,
            cameraMode = CameraMode.entries.firstOrNull {
                it.name == prefs[keyCamera]
            } ?: CameraMode.CHASE,
            timeOfDayPreset = TimeOfDayPreset.entries.firstOrNull {
                it.name == prefs[keyTimeOfDay]
            } ?: TimeOfDayPreset.AUTO,
            weatherPreset = WeatherPreset.entries.firstOrNull {
                it.name == prefs[keyWeather]
            } ?: WeatherPreset.AUTO,
            steeringSensitivity = (prefs[keySensitivity] ?: 1.0f).coerceIn(0.6f, 1.5f),
            engineAudioEnabled = sfx,
            musicEnabled = prefs[keyMusic] ?: true,
            sfxEnabled = sfx,
            showFpsCounter = prefs[keyShowFps] ?: true
        )
    }

    suspend fun setGraphicsQuality(quality: GraphicsQuality) {
        context.settingsDataStore.edit { it[keyQuality] = quality.name }
    }

    suspend fun setCameraMode(mode: CameraMode) {
        context.settingsDataStore.edit { it[keyCamera] = mode.name }
    }

    suspend fun setTimeOfDayPreset(preset: TimeOfDayPreset) {
        context.settingsDataStore.edit { it[keyTimeOfDay] = preset.name }
    }

    suspend fun setWeatherPreset(preset: WeatherPreset) {
        context.settingsDataStore.edit { it[keyWeather] = preset.name }
    }

    suspend fun setSteeringSensitivity(value: Float) {
        context.settingsDataStore.edit { it[keySensitivity] = value.coerceIn(0.6f, 1.5f) }
    }

    suspend fun setEngineAudioEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[keyAudio] = enabled
            it[keySfx] = enabled
        }
    }

    suspend fun setMusicEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyMusic] = enabled }
    }

    suspend fun setSfxEnabled(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[keySfx] = enabled
            it[keyAudio] = enabled
        }
    }

    suspend fun setShowFpsCounter(show: Boolean) {
        context.settingsDataStore.edit { it[keyShowFps] = show }
    }
}
